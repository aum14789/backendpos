package com.sunpos.backend.domain.organization

import com.sunpos.backend.common.ApiResponse
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.time.Instant

data class ActivateBranchDto(
    val purgeTestData: Boolean = true,
    val confirmationCode: String? = null
)

data class PurgeConfirmationDto(
    val confirmationCode: String? = null
)

data class BranchPurgeSummaryDto(
    val branchId: String,
    val purgedOrdersCount: Int,
    val purgedPaymentsCount: Int,
    val purgedShiftsCount: Int,
    val timestamp: Instant = Instant.now()
)

@Service
class BranchLifecycleService(
    private val branchRepository: BranchRepository,
    private val jdbcTemplate: JdbcTemplate
) {
    private val log = LoggerFactory.getLogger(BranchLifecycleService::class.java)

    @Transactional
    fun purgeBranchTestData(branchId: String, operatorUserId: String? = null): BranchPurgeSummaryDto {
        val branch = branchRepository.findById(branchId)
            .orElseThrow { IllegalArgumentException("Branch not found: $branchId") }

        if (branch.status == "ACTIVE" && !branch.isTestBranch) {
            throw IllegalStateException("Cannot purge transactions from an active production branch [$branchId]. Operation locked.")
        }

        log.info("Starting atomic purge of test sales data for branch [{}] (Operator: {})", branchId, operatorUserId)

        // Count before delete for audit reporting
        val ordersCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM orders WHERE branch_id = ?",
            Int::class.java,
            branchId
        ) ?: 0

        val paymentsCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM payment_transactions WHERE branch_id = ?",
            Int::class.java,
            branchId
        ) ?: 0

        val shiftsCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM cashier_shifts WHERE branch_id = ?",
            Int::class.java,
            branchId
        ) ?: 0

        // 1. Delete payment & refund transactions
        jdbcTemplate.update("DELETE FROM refund_transactions WHERE branch_id = ?", branchId)
        jdbcTemplate.update("DELETE FROM payment_transactions WHERE branch_id = ?", branchId)

        // 2. Delete order modifier items, line items, snapshots, and orders
        jdbcTemplate.update(
            "DELETE FROM order_item_modifiers WHERE order_item_id IN (SELECT id FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE branch_id = ?))",
            branchId
        )
        jdbcTemplate.update(
            "DELETE FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE branch_id = ?)",
            branchId
        )
        // Check order_recipe_snapshots table
        try {
            jdbcTemplate.update(
                "DELETE FROM order_recipe_snapshots WHERE order_id IN (SELECT id FROM orders WHERE branch_id = ?)",
                branchId
            )
        } catch (_: Exception) {}

        jdbcTemplate.update("DELETE FROM orders WHERE branch_id = ?", branchId)

        // 3. Delete cash movements and cashier shifts
        try {
            jdbcTemplate.update(
                "DELETE FROM cash_movements WHERE shift_id IN (SELECT id FROM cashier_shifts WHERE branch_id = ?)",
                branchId
            )
        } catch (_: Exception) {}
        jdbcTemplate.update("DELETE FROM cashier_shifts WHERE branch_id = ?", branchId)

        // 4. Delete table sessions and reset table occupancy status to AVAILABLE
        jdbcTemplate.update("DELETE FROM table_sessions WHERE branch_id = ?", branchId)
        try {
            jdbcTemplate.update("UPDATE tables SET status = 'AVAILABLE', current_order_id = NULL WHERE branch_id = ?", branchId)
        } catch (_: Exception) {
            jdbcTemplate.update("UPDATE tables SET status = 'AVAILABLE' WHERE branch_id = ?", branchId)
        }

        // 5. Reset invoice sequence to 0
        branch.invoiceSequenceNumber = 0L
        branch.updatedAt = Instant.now()
        branch.updatedBy = operatorUserId
        branchRepository.save(branch)

        log.info("Completed purge for branch [{}]: purged {} orders, {} payments, {} shifts", branchId, ordersCount, paymentsCount, shiftsCount)

        return BranchPurgeSummaryDto(
            branchId = branchId,
            purgedOrdersCount = ordersCount,
            purgedPaymentsCount = paymentsCount,
            purgedShiftsCount = shiftsCount
        )
    }

    @Transactional
    fun activateBranch(branchId: String, purgeTestData: Boolean = true, operatorUserId: String? = null): Branch {
        val branch = branchRepository.findById(branchId)
            .orElseThrow { IllegalArgumentException("Branch not found: $branchId") }

        if (purgeTestData) {
            purgeBranchTestData(branchId, operatorUserId)
        }

        branch.status = "ACTIVE"
        branch.updatedAt = Instant.now()
        branch.updatedBy = operatorUserId
        val saved = branchRepository.save(branch)

        log.info("Branch [{}] successfully activated to ACTIVE status (Go-Live)", branchId)
        return saved
    }

    @Transactional
    fun resetTestBranch(branchId: String, operatorUserId: String? = null): BranchPurgeSummaryDto {
        val branch = branchRepository.findById(branchId)
            .orElseThrow { IllegalArgumentException("Branch not found: $branchId") }

        if (!branch.isTestBranch) {
            throw IllegalArgumentException("Branch [$branchId] is not marked as a test branch")
        }

        return purgeBranchTestData(branchId, operatorUserId)
    }
}

@RestController
@RequestMapping("/api/v1/branches")
class BranchLifecycleController(
    private val lifecycleService: BranchLifecycleService,
    private val seedingService: BranchDataSeedingService
) {
    @PostMapping("/{id}/purge-test-data")
    @PreAuthorize("hasAuthority('ORGANIZATION_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_BRAND_OWNER')")
    fun purgeBranchTestData(
        @PathVariable id: String,
        @RequestBody(required = false) body: PurgeConfirmationDto?
    ): ApiResponse<BranchPurgeSummaryDto> {
        val result = lifecycleService.purgeBranchTestData(id)
        return ApiResponse.success(result, "Test sales data purged successfully")
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('ORGANIZATION_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_BRAND_OWNER')")
    fun activateBranch(
        @PathVariable id: String,
        @RequestBody(required = false) body: ActivateBranchDto?
    ): ApiResponse<Branch> {
        val purge = body?.purgeTestData ?: true
        val result = lifecycleService.activateBranch(id, purgeTestData = purge)
        return ApiResponse.success(result, "Branch activated successfully")
    }

    @PostMapping("/{id}/reset-test-branch")
    @PreAuthorize("hasAuthority('ORGANIZATION_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_BRAND_OWNER')")
    fun resetTestBranch(
        @PathVariable id: String
    ): ApiResponse<BranchPurgeSummaryDto> {
        val result = lifecycleService.resetTestBranch(id)
        return ApiResponse.success(result, "Test branch reset successfully")
    }

    @PostMapping("/{id}/clone-master-data")
    @PreAuthorize("hasAuthority('ORGANIZATION_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_BRAND_OWNER')")
    fun cloneMasterData(
        @PathVariable id: String,
        @RequestBody request: CloneBranchMasterDataRequest
    ): ApiResponse<BranchDataSeedingSummaryDto> {
        val result = seedingService.cloneBranchMasterData(id, request)
        return ApiResponse.success(result, "Branch master data cloned successfully")
    }
}

