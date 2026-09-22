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

/** One checklist row: a category of data still living in the branch. */
data class BranchDeletionChecklistItem(
    val category: String,
    val label: String,
    val count: Long
)

/** Spec 0036 (ผู้ใช้เพิ่ม): result of the explicit name/code availability check. */
data class BranchNameCodeCheckDto(
    val nameAvailable: Boolean,
    val codeAvailable: Boolean
)

/** Spec 0036: what must be empty before a branch may be soft-deleted. */
data class BranchDeletionChecklistDto(
    val branchId: String,
    val branchStatus: String,
    val deletable: Boolean,
    val blockedByStatus: Boolean,
    val items: List<BranchDeletionChecklistItem>
)

@Service
class BranchLifecycleService(
    private val branchRepository: BranchRepository,
    private val jdbcTemplate: JdbcTemplate
) {
    private val log = LoggerFactory.getLogger(BranchLifecycleService::class.java)

    /**
     * Spec 0036: a branch may only be deleted when it is PRE_OPENING and truly untouched.
     * Auto-provisioned items (main warehouse row itself, activation codes) never count as
     * leftovers — but any stock inside a warehouse, per-branch menu assignments, devices,
     * users or transactions do.
     */
    fun deletionChecklist(branchId: String): BranchDeletionChecklistDto {
        val branch = branchRepository.findById(branchId)
            .orElseThrow { IllegalArgumentException("Branch not found: $branchId") }

        val blockedByStatus = branch.status == "ACTIVE"

        fun count(sql: String, params: Int = 1): Long =
            jdbcTemplate.queryForObject(sql, Long::class.java, *Array(params) { branchId }) ?: 0L

        val items = listOf(
            BranchDeletionChecklistItem(
                "menu_assignments", "เมนูประจำสาขา",
                count("SELECT COUNT(*) FROM menu_item_branches WHERE branch_id = ? AND is_active = true")
            ),
            BranchDeletionChecklistItem(
                "devices", "อุปกรณ์ POS",
                count("SELECT COUNT(*) FROM devices WHERE branch_id = ? AND is_active = true")
            ),
            BranchDeletionChecklistItem(
                "stock", "สต็อกวัตถุดิบในคลัง (ผลจากการรับ/โอนสินค้า)",
                count("""SELECT COALESCE(SUM(s.quantity), 0) FROM inventory_stocks s
                          JOIN warehouses w ON w.id = s.warehouse_id
                          WHERE w.branch_id = ? AND s.quantity > 0""")
            ),
            BranchDeletionChecklistItem(
                "stock_movements", "ประวัติรับ-โอน-เบิกวัตถุดิบ",
                count("""SELECT COUNT(*) FROM stock_movements m
                          JOIN warehouses w ON w.id = m.warehouse_id
                          WHERE w.branch_id = ?""")
            ),
            BranchDeletionChecklistItem(
                "orders", "ทรานแซกชันการขาย (ออเดอร์)",
                count("SELECT COUNT(*) FROM orders WHERE branch_id = ?")
            ),
            BranchDeletionChecklistItem(
                "shifts", "การปิดกะ",
                count("SELECT COUNT(*) FROM cashier_shifts WHERE branch_id = ?")
            ),
            BranchDeletionChecklistItem(
                "business_days", "การปิดวันขาย",
                count("SELECT COUNT(*) FROM business_days WHERE branch_id = ?")
            ),
            BranchDeletionChecklistItem(
                "qr_orders", "ออเดอร์ QR หน้าร้าน",
                count("SELECT COUNT(*) FROM qr_orders WHERE branch_id = ?")
            ),
            BranchDeletionChecklistItem(
                "inventory_transactions", "ใบรับ/โอน/ปิดคลังวัตถุดิบ",
                count(
                    """SELECT (SELECT COUNT(*) FROM stock_transfers st
                            JOIN warehouses sw ON sw.id = st.source_warehouse_id
                            WHERE sw.branch_id = ?
                            OR st.target_warehouse_id IN (SELECT id FROM warehouses WHERE branch_id = ?))
                          + (SELECT COUNT(*) FROM inventory_close_batches icb WHERE icb.branch_id = ?)""",
                    3
                )
            )
        ).filter { it.count > 0 }

        val deletable = !blockedByStatus && items.isEmpty()
        return BranchDeletionChecklistDto(
            branchId = branchId,
            branchStatus = branch.status,
            deletable = deletable,
            blockedByStatus = blockedByStatus,
            items = items
        )
    }

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
        jdbcTemplate.update(
            "DELETE FROM order_recipe_snapshots WHERE order_id IN (SELECT id FROM orders WHERE branch_id = ?)",
            branchId
        )

        jdbcTemplate.update("DELETE FROM orders WHERE branch_id = ?", branchId)

        // 3. Delete cash movements and cashier shifts
        jdbcTemplate.update(
            "DELETE FROM cash_movements WHERE shift_id IN (SELECT id FROM cashier_shifts WHERE branch_id = ?)",
            branchId
        )
        jdbcTemplate.update("DELETE FROM cashier_shifts WHERE branch_id = ?", branchId)

        // 4. Delete table sessions and reset table occupancy status to AVAILABLE
        // NOTE: never swallow exceptions here — on PostgreSQL a failed statement aborts the
        // whole transaction (25P02), so a try/catch fallback just moves the failure downstream.
        jdbcTemplate.update("DELETE FROM table_sessions WHERE branch_id = ?", branchId)
        jdbcTemplate.update("UPDATE tables SET status = 'AVAILABLE' WHERE branch_id = ?", branchId)

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

    /**
     * Spec 0036 (ผู้ใช้เพิ่ม): explicit name/code uniqueness check for the create/edit form's
     * "check" button (no realtime validation). excludeBranchId lets an edit form keep its own values.
     */
    fun checkNameAndCodeAvailable(name: String, code: String, excludeBranchId: String?): BranchNameCodeCheckDto {
        val nameTaken = if (excludeBranchId != null)
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM branches WHERE name = ? AND id <> ?", Long::class.java, name.trim(), excludeBranchId
            ) ?: 0L
        else
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM branches WHERE name = ?", Long::class.java, name.trim()
            ) ?: 0L
        val codeTaken = if (code.isBlank()) 0L else if (excludeBranchId != null)
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM branches WHERE code = ? AND id <> ?", Long::class.java, code.trim(), excludeBranchId
            ) ?: 0L
        else
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM branches WHERE code = ?", Long::class.java, code.trim()
            ) ?: 0L
        return BranchNameCodeCheckDto(nameAvailable = nameTaken == 0L, codeAvailable = codeTaken == 0L)
    }

    /**
     * Spec 0036: soft-delete a branch. Only a truly untouched PRE_OPENING branch
     * qualifies — every leftover category blocks deletion, and ACTIVE branches are
     * never deletable. Auto-provisioned rows stay (soft-delete keeps the record).
     */
    @Transactional
    fun deleteBranch(branchId: String): Boolean {
        val branch = branchRepository.findById(branchId)
            .orElseThrow { IllegalArgumentException("Branch not found: $branchId") }

        if (branch.status == "ACTIVE") {
            throw IllegalStateException("สาขาที่เปิดขายจริงแล้ว (ACTIVE) ไม่สามารถลบได้")
        }

        val checklist = deletionChecklist(branchId)
        if (checklist.blockedByStatus) {
            throw IllegalStateException("สาขาที่เปิดขายจริงแล้ว (ACTIVE) ไม่สามารถลบได้")
        }
        if (checklist.items.isNotEmpty()) {
            val leftovers = checklist.items.joinToString(" · ") { "${it.label} (${it.count})" }
            throw IllegalStateException(
                "ยังลบสาขาไม่ได้ เพราะมีข้อมูลค้างอยู่: $leftovers — กรุณาย้าย/ลบรายการเหล่านี้ออกก่อน หรือใช้ทางปิดสาขา (CLOSED) แล้วเปลี่ยนชื่อ/รหัสเพื่อรีไซเคิลแทน"
            )
        }

        branch.isActive = false
        branch.updatedAt = Instant.now()
        branchRepository.save(branch)

        // Auto-provisioned artifacts belong to the branch record itself; retire them quietly.
        jdbcTemplate.update("UPDATE activation_codes SET status = 'REVOKED' WHERE branch_id = ? AND status <> 'REVOKED'", branchId)
        jdbcTemplate.update("UPDATE warehouses SET is_active = false WHERE branch_id = ?", branchId)

        log.info("Branch [{}] soft-deleted (Spec 0036)", branchId)
        return true
    }

    /**
     * Spec 0036 edit rules, enforced server-side:
     * - an ACTIVE branch can never go back to another status and its code is frozen;
     * - the code is editable only while the branch is CLOSED (recycling path);
     * - the name is always editable.
     */
    @Transactional
    fun updateBranchWithRules(branchId: String, status: String?, code: String?, name: String? = null): Branch {
        val branch = branchRepository.findById(branchId)
            .orElseThrow { IllegalArgumentException("Branch not found: $branchId") }

        if (branch.status == "ACTIVE" && status != null && status != "ACTIVE") {
            throw IllegalStateException("สาขาที่เปิดขายจริงแล้ว (ACTIVE) เปลี่ยนสถานะกลับเป็นสถานะอื่นไม่ได้")
        }
        if (!code.isNullOrBlank() && code != branch.code) {
            if (branch.status == "ACTIVE") {
                throw IllegalStateException("สาขาที่เปิดขายจริงแล้ว (ACTIVE) เปลี่ยนรหัสสาขาไม่ได้")
            }
            if (branch.status != "CLOSED") {
                throw IllegalStateException("เปลี่ยนรหัสสาขาได้เฉพาะสาขาที่ปิดแล้ว (CLOSED) เท่านั้น")
            }
            branch.code = code
        }
        if (!status.isNullOrBlank()) {
            branch.status = status
        }
        if (!name.isNullOrBlank()) {
            branch.name = name
        }
        branch.updatedAt = Instant.now()
        return branchRepository.save(branch)
    }
}

@RestController
@RequestMapping("/api/v1/branches")
class BranchLifecycleController(
    private val lifecycleService: BranchLifecycleService,
    private val seedingService: BranchDataSeedingService
) {
    @GetMapping("/{id}/deletion-checklist")
    @PreAuthorize("hasAuthority('ORGANIZATION_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun deletionChecklist(@PathVariable id: String): ApiResponse<BranchDeletionChecklistDto> {
        return ApiResponse.success(lifecycleService.deletionChecklist(id))
    }

    @GetMapping("/name-code-check")
    fun checkNameCode(
        @RequestParam name: String,
        @RequestParam(required = false) code: String?,
        @RequestParam(required = false) excludeBranchId: String?
    ): ApiResponse<BranchNameCodeCheckDto> {
        return ApiResponse.success(
            lifecycleService.checkNameAndCodeAvailable(
                name = name,
                code = code ?: "",
                excludeBranchId = excludeBranchId
            )
        )
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    fun deleteBranch(@PathVariable id: String): ApiResponse<Boolean> {
        lifecycleService.deleteBranch(id)
        return ApiResponse.success(true, "Branch deleted successfully")
    }

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

