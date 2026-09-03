package com.sunpos.backend.domain.recipe

import com.sunpos.backend.common.ApiResponse
import com.sunpos.backend.common.JdbcRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

// ── Entity ──

/**
 * Represents estimated per-head ingredient consumption for self-serve buffets.
 * Links a buffet promotion tier to inventory items with a quantity-per-head ratio.
 * Used during EOD stock consumption to calculate headcount-based deductions
 * for buffets where guests serve themselves (no individual order lines).
 */
class BuffetPackageRecipe(
    val id: String = UUID.randomUUID().toString(),
    var buffetTierId: String = "",
    var inventoryItemId: String = "",
    var quantityPerHead: BigDecimal = BigDecimal.ZERO,
    var unit: String = "",
    var wastePercentage: BigDecimal = BigDecimal.ZERO,
    var notes: String? = null,
    var isActive: Boolean = true,
    val createdAt: Instant = Instant.now()
)

// ── DTOs ──

data class CreateBuffetPackageRecipeDto(
    val buffetTierId: String = "",
    val inventoryItemId: String = "",
    val quantityPerHead: BigDecimal = BigDecimal.ZERO,
    val unit: String = "",
    val wastePercentage: BigDecimal = BigDecimal.ZERO,
    val notes: String? = null
)

data class UpdateBuffetPackageRecipeDto(
    val quantityPerHead: BigDecimal? = null,
    val unit: String? = null,
    val wastePercentage: BigDecimal? = null,
    val notes: String? = null,
    val isActive: Boolean? = null
)

data class BuffetPackageRecipeResponseDto(
    val id: String = "",
    val buffetTierId: String = "",
    val inventoryItemId: String = "",
    val inventoryItemName: String? = null,
    val quantityPerHead: BigDecimal = BigDecimal.ZERO,
    val unit: String = "",
    val wastePercentage: BigDecimal = BigDecimal.ZERO,
    val notes: String? = null,
    val isActive: Boolean = true,
    val createdAt: Instant = Instant.now()
)

// ── Repository ──

@Repository
class BuffetPackageRecipeRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<BuffetPackageRecipe>(jdbcTemplate, "buffet_package_recipes", BuffetPackageRecipe::class.java) {
    fun findByBuffetTierId(buffetTierId: String): List<BuffetPackageRecipe> = findByField("buffetTierId", buffetTierId)
    fun findByBuffetTierIdAndIsActiveTrue(buffetTierId: String): List<BuffetPackageRecipe> =
        findByFields(mapOf("buffetTierId" to buffetTierId, "isActive" to true))
}

// ── Service ──

@Service
class BuffetPackageRecipeService(
    private val recipeRepository: BuffetPackageRecipeRepository,
    private val inventoryItemRepository: com.sunpos.backend.domain.inventory.InventoryItemRepository
) {
    @Transactional
    fun createRecipe(dto: CreateBuffetPackageRecipeDto): BuffetPackageRecipeResponseDto {
        // Validate inventory item exists
        inventoryItemRepository.findById(dto.inventoryItemId)
            .orElseThrow { NoSuchElementException("Inventory item '${dto.inventoryItemId}' not found") }

        val recipe = BuffetPackageRecipe(
            buffetTierId = dto.buffetTierId,
            inventoryItemId = dto.inventoryItemId,
            quantityPerHead = dto.quantityPerHead,
            unit = dto.unit,
            wastePercentage = dto.wastePercentage,
            notes = dto.notes
        )
        return recipeRepository.save(recipe).toDto()
    }

    @Transactional
    fun updateRecipe(id: String, dto: UpdateBuffetPackageRecipeDto): BuffetPackageRecipeResponseDto {
        val recipe = recipeRepository.findById(id)
            .orElseThrow { NoSuchElementException("Buffet package recipe '$id' not found") }

        dto.quantityPerHead?.let { recipe.quantityPerHead = it }
        dto.unit?.let { recipe.unit = it }
        dto.wastePercentage?.let { recipe.wastePercentage = it }
        dto.notes?.let { recipe.notes = it }
        dto.isActive?.let { recipe.isActive = it }

        return recipeRepository.save(recipe).toDto()
    }

    fun listByTier(buffetTierId: String): List<BuffetPackageRecipeResponseDto> {
        return recipeRepository.findByBuffetTierId(buffetTierId).map { it.toDto() }
    }

    fun listActiveByTier(buffetTierId: String): List<BuffetPackageRecipeResponseDto> {
        return recipeRepository.findByBuffetTierIdAndIsActiveTrue(buffetTierId).map { it.toDto() }
    }

    fun getById(id: String): BuffetPackageRecipeResponseDto {
        return recipeRepository.findById(id)
            .orElseThrow { NoSuchElementException("Buffet package recipe '$id' not found") }
            .toDto()
    }

    @Transactional
    fun deleteRecipe(id: String) {
        val recipe = recipeRepository.findById(id)
            .orElseThrow { NoSuchElementException("Buffet package recipe '$id' not found") }
        recipe.isActive = false
        recipeRepository.save(recipe)
    }

    private fun BuffetPackageRecipe.toDto(): BuffetPackageRecipeResponseDto {
        val itemName = inventoryItemRepository.findById(this.inventoryItemId).map { it.name }.orElse(null)
        return BuffetPackageRecipeResponseDto(
            id = this.id,
            buffetTierId = this.buffetTierId,
            inventoryItemId = this.inventoryItemId,
            inventoryItemName = itemName,
            quantityPerHead = this.quantityPerHead,
            unit = this.unit,
            wastePercentage = this.wastePercentage,
            notes = this.notes,
            isActive = this.isActive,
            createdAt = this.createdAt
        )
    }
}

// ── Controller ──

@RestController
@RequestMapping("/api/v1/recipes/buffet-packages")
class BuffetPackageRecipeController(
    private val service: BuffetPackageRecipeService
) {
    @GetMapping
    fun listByTier(@RequestParam buffetTierId: String): ApiResponse<List<BuffetPackageRecipeResponseDto>> {
        return ApiResponse.success(service.listByTier(buffetTierId))
    }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: String): ApiResponse<BuffetPackageRecipeResponseDto> {
        return ApiResponse.success(service.getById(id))
    }

    @PostMapping
    @PreAuthorize("hasAuthority('INVENTORY_CONFIG_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_BRANCH_MANAGER')")
    fun create(@RequestBody dto: CreateBuffetPackageRecipeDto): ApiResponse<BuffetPackageRecipeResponseDto> {
        val result = service.createRecipe(dto)
        return ApiResponse.success(result, "Buffet package recipe created successfully")
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_CONFIG_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_BRANCH_MANAGER')")
    fun update(
        @PathVariable id: String,
        @RequestBody dto: UpdateBuffetPackageRecipeDto
    ): ApiResponse<BuffetPackageRecipeResponseDto> {
        val result = service.updateRecipe(id, dto)
        return ApiResponse.success(result, "Buffet package recipe updated successfully")
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('INVENTORY_CONFIG_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_BRANCH_MANAGER')")
    fun delete(@PathVariable id: String): ApiResponse<Boolean> {
        service.deleteRecipe(id)
        return ApiResponse.success(true, "Buffet package recipe deactivated successfully")
    }
}
