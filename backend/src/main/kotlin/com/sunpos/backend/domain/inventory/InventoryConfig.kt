package com.sunpos.backend.domain.inventory

import com.sunpos.backend.common.ApiResponse
import com.sunpos.backend.common.JdbcRepository
import org.springframework.jdbc.core.JdbcTemplate
import com.sunpos.backend.domain.organization.BranchRepository
import com.sunpos.backend.domain.organization.BrandRepository
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

// ── Enums ──

enum class StockDeductionMode {
    EOD,       // Deduct stock at end-of-day close (default)
    REALTIME   // Deduct stock at payment time
}

enum class BuffetConsumptionMode {
    HEADCOUNT_RECIPE,   // Use package recipe × headcount for self-serve buffets (default)
    ORDER_LINES_ONLY    // Only deduct via explicit order line items
}

// ── Entity ──

class InventoryBranchConfig(
    val id: String = UUID.randomUUID().toString(),
    var branchId: String = "",
    var stockDeductionMode: StockDeductionMode = StockDeductionMode.EOD,
    var buffetConsumptionMode: BuffetConsumptionMode = BuffetConsumptionMode.HEADCOUNT_RECIPE,
    var allowNegativeStock: Boolean = false,
    var autoCreateStockOnSale: Boolean = false,
    var updatedAt: Instant = Instant.now(),
    var updatedBy: String? = null
)

// ── DTOs ──

data class InventoryBranchConfigDto(
    val stockDeductionMode: StockDeductionMode? = null,
    val buffetConsumptionMode: BuffetConsumptionMode? = null,
    val allowNegativeStock: Boolean? = null,
    val autoCreateStockOnSale: Boolean? = null,
    val updatedBy: String? = null
)

data class BranchPosConfigDto(
    val businessDayCloseTime: String? = null,
    val taxRate: BigDecimal? = null,
    val serviceChargeRate: BigDecimal? = null
)

data class BatchInventoryBranchConfigDto(
    val branchIds: List<String> = emptyList(),
    val config: InventoryBranchConfigDto? = null,
    val posConfig: BranchPosConfigDto? = null,
    val updatedBy: String? = null
)

data class CloneInventoryConfigDto(
    val sourceBranchId: String = "",
    val targetBranchIds: List<String> = emptyList(),
    val clonePosConfig: Boolean = true,
    val updatedBy: String? = null
)

data class InventoryBranchConfigResponseDto(
    val id: String = "",
    val branchId: String = "",
    val stockDeductionMode: String = "",
    val buffetConsumptionMode: String = "",
    val allowNegativeStock: Boolean = false,
    val autoCreateStockOnSale: Boolean = false,
    val updatedAt: Instant = Instant.now(),
    val updatedBy: String? = null
)

data class BranchConfigOverviewDto(
    val branchId: String = "",
    val branchName: String = "",
    val branchCode: String = "",
    val brandId: String? = null,
    val brandName: String? = null,
    val brandCode: String? = null,
    val businessDayCloseTime: String = "02:00",
    val taxRate: BigDecimal = BigDecimal("7.00"),
    val serviceChargeRate: BigDecimal = BigDecimal("10.00"),
    val stockDeductionMode: String = StockDeductionMode.EOD.name,
    val buffetConsumptionMode: String = BuffetConsumptionMode.HEADCOUNT_RECIPE.name,
    val allowNegativeStock: Boolean = false,
    val autoCreateStockOnSale: Boolean = false,
    val updatedAt: Instant = Instant.now(),
    val updatedBy: String? = null
)

data class BatchConfigResultDto(
    val updatedBranchCount: Int = 0,
    val branchIds: List<String> = emptyList(),
    val message: String = "Configuration successfully applied"
)

// ── Repository ──

@Repository
class InventoryBranchConfigRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<InventoryBranchConfig>(jdbcTemplate, "inventory_branch_configs", InventoryBranchConfig::class.java) {
    fun findByBranchId(branchId: String): Optional<InventoryBranchConfig> = findOneByField("branchId", branchId)
}

// ── Service ──

@Service
class InventoryConfigService(
    private val configRepository: InventoryBranchConfigRepository,
    private val branchRepository: BranchRepository,
    private val brandRepository: BrandRepository
) {
    /**
     * Returns the inventory config for a branch, creating a default one if none exists.
     */
    fun getConfigForBranch(branchId: String): InventoryBranchConfig {
        return configRepository.findByBranchId(branchId).orElseGet {
            configRepository.save(InventoryBranchConfig(branchId = branchId))
        }
    }

    fun getConfigResponseForBranch(branchId: String): InventoryBranchConfigResponseDto {
        return getConfigForBranch(branchId).toDto()
    }

    /**
     * Overview matrix of all branches with brand metadata and both inventory and POS settings
     */
    fun getAllBranchConfigsOverview(): List<BranchConfigOverviewDto> {
        val branches = branchRepository.findAll()
        val brandsMap = brandRepository.findAll().associateBy { it.id }
        val configsMap = configRepository.findAll().associateBy { it.branchId }

        return branches.map { branch ->
            val brand = branch.brandId?.let { brandsMap[it] }
            val invConfig = configsMap[branch.id] ?: InventoryBranchConfig(branchId = branch.id)

            BranchConfigOverviewDto(
                branchId = branch.id,
                branchName = branch.name,
                branchCode = branch.code,
                brandId = branch.brandId,
                brandName = brand?.name,
                brandCode = brand?.code,
                businessDayCloseTime = branch.businessDayCloseTime,
                taxRate = branch.taxRate,
                serviceChargeRate = branch.serviceChargeRate,
                stockDeductionMode = invConfig.stockDeductionMode.name,
                buffetConsumptionMode = invConfig.buffetConsumptionMode.name,
                allowNegativeStock = invConfig.allowNegativeStock,
                autoCreateStockOnSale = invConfig.autoCreateStockOnSale,
                updatedAt = invConfig.updatedAt,
                updatedBy = invConfig.updatedBy
            )
        }
    }

    @Transactional
    fun updateConfig(branchId: String, dto: InventoryBranchConfigDto): InventoryBranchConfigResponseDto {
        val config = getConfigForBranch(branchId)

        dto.stockDeductionMode?.let { config.stockDeductionMode = it }
        dto.buffetConsumptionMode?.let { config.buffetConsumptionMode = it }
        dto.allowNegativeStock?.let { config.allowNegativeStock = it }
        dto.autoCreateStockOnSale?.let { config.autoCreateStockOnSale = it }
        dto.updatedBy?.let { config.updatedBy = it }
        config.updatedAt = Instant.now()

        return configRepository.save(config).toDto()
    }

    @Transactional
    fun batchUpdateConfigs(dto: BatchInventoryBranchConfigDto): BatchConfigResultDto {
        val uniqueBranchIds = dto.branchIds.distinct().filter { it.isNotBlank() }
        if (uniqueBranchIds.isEmpty()) {
            return BatchConfigResultDto(0, emptyList(), "No branches specified for update")
        }

        // 1. Update Inventory Config for each branch
        if (dto.config != null) {
            uniqueBranchIds.forEach { branchId ->
                val config = getConfigForBranch(branchId)
                dto.config.stockDeductionMode?.let { config.stockDeductionMode = it }
                dto.config.buffetConsumptionMode?.let { config.buffetConsumptionMode = it }
                dto.config.allowNegativeStock?.let { config.allowNegativeStock = it }
                dto.config.autoCreateStockOnSale?.let { config.autoCreateStockOnSale = it }
                dto.updatedBy?.let { config.updatedBy = it }
                config.updatedAt = Instant.now()
                configRepository.save(config)
            }
        }

        // 2. Optionally update POS Config on Branch entity
        if (dto.posConfig != null) {
            uniqueBranchIds.forEach { branchId ->
                branchRepository.findById(branchId).ifPresent { branch ->
                    dto.posConfig.businessDayCloseTime?.let { branch.businessDayCloseTime = it }
                    dto.posConfig.taxRate?.let { branch.taxRate = it }
                    dto.posConfig.serviceChargeRate?.let { branch.serviceChargeRate = it }
                    branch.updatedAt = Instant.now()
                    dto.updatedBy?.let { branch.updatedBy = it }
                    branchRepository.save(branch)
                }
            }
        }

        return BatchConfigResultDto(
            updatedBranchCount = uniqueBranchIds.size,
            branchIds = uniqueBranchIds,
            message = "Successfully updated configuration for ${uniqueBranchIds.size} branches"
        )
    }

    @Transactional
    fun cloneConfig(dto: CloneInventoryConfigDto): BatchConfigResultDto {
        val sourceConfig = getConfigForBranch(dto.sourceBranchId)
        val sourceBranch = branchRepository.findById(dto.sourceBranchId)

        val targetIds = dto.targetBranchIds.distinct().filter { it.isNotBlank() && it != dto.sourceBranchId }
        if (targetIds.isEmpty()) {
            return BatchConfigResultDto(0, emptyList(), "No target branches specified for clone")
        }

        targetIds.forEach { targetBranchId ->
            // Clone Inventory settings
            val targetConfig = getConfigForBranch(targetBranchId)
            targetConfig.stockDeductionMode = sourceConfig.stockDeductionMode
            targetConfig.buffetConsumptionMode = sourceConfig.buffetConsumptionMode
            targetConfig.allowNegativeStock = sourceConfig.allowNegativeStock
            targetConfig.autoCreateStockOnSale = sourceConfig.autoCreateStockOnSale
            targetConfig.updatedBy = dto.updatedBy
            targetConfig.updatedAt = Instant.now()
            configRepository.save(targetConfig)

            // Clone POS settings if enabled
            if (dto.clonePosConfig && sourceBranch.isPresent) {
                val src = sourceBranch.get()
                branchRepository.findById(targetBranchId).ifPresent { targetBranch ->
                    targetBranch.businessDayCloseTime = src.businessDayCloseTime
                    targetBranch.taxRate = src.taxRate
                    targetBranch.serviceChargeRate = src.serviceChargeRate
                    targetBranch.updatedAt = Instant.now()
                    dto.updatedBy?.let { targetBranch.updatedBy = it }
                    branchRepository.save(targetBranch)
                }
            }
        }

        return BatchConfigResultDto(
            updatedBranchCount = targetIds.size,
            branchIds = targetIds,
            message = "Successfully cloned configuration from source to ${targetIds.size} branches"
        )
    }

    private fun InventoryBranchConfig.toDto() = InventoryBranchConfigResponseDto(
        id = this.id,
        branchId = this.branchId,
        stockDeductionMode = this.stockDeductionMode.name,
        buffetConsumptionMode = this.buffetConsumptionMode.name,
        allowNegativeStock = this.allowNegativeStock,
        autoCreateStockOnSale = this.autoCreateStockOnSale,
        updatedAt = this.updatedAt,
        updatedBy = this.updatedBy
    )
}

// ── Controller ──

@RestController
@RequestMapping("/api/v1/inventory/config")
class InventoryConfigController(
    private val configService: InventoryConfigService
) {
    @GetMapping
    fun getConfig(@RequestParam branchId: String): ApiResponse<InventoryBranchConfigResponseDto> {
        return ApiResponse.success(configService.getConfigResponseForBranch(branchId))
    }

    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('INVENTORY_CONFIG_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_MANAGER') or hasAuthority('ORGANIZATION_MANAGE')")
    fun getAllBranchConfigsOverview(): ApiResponse<List<BranchConfigOverviewDto>> {
        return ApiResponse.success(configService.getAllBranchConfigsOverview())
    }

    @PutMapping
    @PreAuthorize("hasAuthority('INVENTORY_CONFIG_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_BRANCH_MANAGER')")
    fun updateConfig(
        @RequestParam branchId: String,
        @RequestBody dto: InventoryBranchConfigDto
    ): ApiResponse<InventoryBranchConfigResponseDto> {
        val result = configService.updateConfig(branchId, dto)
        return ApiResponse.success(result, "Inventory configuration updated successfully")
    }

    @PostMapping("/batch-update")
    @PreAuthorize("hasAuthority('INVENTORY_CONFIG_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_MANAGER') or hasAuthority('ORGANIZATION_MANAGE')")
    fun batchUpdateConfig(
        @RequestBody dto: BatchInventoryBranchConfigDto
    ): ApiResponse<BatchConfigResultDto> {
        val result = configService.batchUpdateConfigs(dto)
        return ApiResponse.success(result, result.message)
    }

    @PostMapping("/clone")
    @PreAuthorize("hasAuthority('INVENTORY_CONFIG_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_MANAGER') or hasAuthority('ORGANIZATION_MANAGE')")
    fun cloneConfig(
        @RequestBody dto: CloneInventoryConfigDto
    ): ApiResponse<BatchConfigResultDto> {
        val result = configService.cloneConfig(dto)
        return ApiResponse.success(result, result.message)
    }
}
