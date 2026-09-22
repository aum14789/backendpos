package com.sunpos.backend.domain.organization

import com.sunpos.backend.common.TestFixtureFactory
import com.sunpos.backend.domain.inventory.WarehouseRole
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

/**
 * Spec 0036 Ticket 02:
 * - DELETE: only a truly untouched PRE_OPENING branch may be soft-deleted.
 * - PUT: ACTIVE branches can never go back (status/code locked), code changes only when CLOSED.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BranchDeletionTest {

    @Autowired private lateinit var branchLifecycleService: BranchLifecycleService
    @Autowired private lateinit var testFixtureFactory: TestFixtureFactory
    @Autowired private lateinit var jdbc: JdbcTemplate

    private val branchId = "branch-del-01"

    @BeforeEach
    fun setUp() {
        testFixtureFactory.ensureBranch(branchId)
    }

    private fun setStatus(status: String) {
        jdbc.update("UPDATE branches SET status = ? WHERE id = ?", status, branchId)
    }

    // ── DELETE ──

    @Test
    fun `untouched pre-opening branch is soft-deleted`() {
        val deleted = branchLifecycleService.deleteBranch(branchId)
        assertTrue(deleted)
        val row = jdbc.queryForMap("SELECT is_active FROM branches WHERE id = ?", branchId)
        assertEquals(false, row["is_active"])
    }

    @Test
    fun `active branch can never be deleted`() {
        setStatus("ACTIVE")
        val ex = assertThrows(IllegalStateException::class.java) {
            branchLifecycleService.deleteBranch(branchId)
        }
        assertTrue(ex.message!!.contains("เปิดขายจริง"))
    }

    @Test
    fun `branch with leftover menu assignments cannot be deleted`() {
        testFixtureFactory.ensureMenuItem(id = "mi-del-01", branchId = branchId, name = "Menu D1")
        jdbc.update(
            """INSERT INTO menu_item_branches (id, menu_item_id, brand_id, branch_id, is_active)
               VALUES ('mib-del-01', 'mi-del-01', 'brand-001', ?, true)""",
            branchId
        )
        val ex = assertThrows(IllegalStateException::class.java) {
            branchLifecycleService.deleteBranch(branchId)
        }
        assertTrue(ex.message!!.contains("เมนู"))
    }

    @Test
    fun `branch with received stock cannot be deleted`() {
        testFixtureFactory.ensureWarehouse(
            id = "wh-del-01", branchId = branchId, name = "Main WH", code = "WH-D1",
            warehouseRole = WarehouseRole.MAIN
        )
        testFixtureFactory.ensureInventoryItem(id = "item-del-01", sku = "SKU-D1", name = "Item D1")
        jdbc.update(
            """INSERT INTO inventory_stocks (id, warehouse_id, inventory_item_id, quantity)
               VALUES ('istock-del-01', 'wh-del-01', 'item-del-01', 3)"""
        )
        assertThrows(IllegalStateException::class.java) {
            branchLifecycleService.deleteBranch(branchId)
        }
    }

    @Test
    fun `closed branch with data cannot be deleted either`() {
        setStatus("CLOSED")
        testFixtureFactory.ensureOrder(id = "ord-del-01", branchId = branchId, orderNumber = "D-1")
        assertThrows(IllegalStateException::class.java) {
            branchLifecycleService.deleteBranch(branchId)
        }
    }

    // ── PUT rules ──

    @Test
    fun `active branch cannot go back to pre-opening`() {
        setStatus("ACTIVE")
        val ex = assertThrows(IllegalStateException::class.java) {
            branchLifecycleService.updateBranchWithRules(branchId, status = "PRE_OPENING", code = null)
        }
        assertTrue(ex.message!!.contains("เปิดขายจริง"))
    }

    @Test
    fun `non-closed branch cannot change its code`() {
        val ex = assertThrows(IllegalStateException::class.java) {
            branchLifecycleService.updateBranchWithRules(branchId, status = null, code = "NEWCODE")
        }
        assertTrue(ex.message!!.contains("รหัสสาขา"))
    }

    @Test
    fun `closed branch may change its code`() {
        setStatus("CLOSED")
        val name = branchLifecycleService.updateBranchWithRules(branchId, status = null, code = "RECYCLED")
        assertEquals("RECYCLED", name.code)
    }

    @Test
    fun `name is always editable`() {
        val name = branchLifecycleService.updateBranchWithRules(branchId, status = null, code = null, name = "ชื่อใหม่")
        assertEquals("ชื่อใหม่", name.name)
    }
}
