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
 * Spec 0036 Ticket 01: the deletion checklist reports what still lives in a branch
 * before it may be deleted. Auto-provisioned items (main warehouse, activation code)
 * never count as leftovers, and ACTIVE branches are always undeletable.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BranchDeletionChecklistTest {

    @Autowired private lateinit var branchLifecycleService: BranchLifecycleService
    @Autowired private lateinit var testFixtureFactory: TestFixtureFactory
    @Autowired private lateinit var jdbc: JdbcTemplate

    private val branchId = "branch-delcheck-01"

    @BeforeEach
    fun setUp() {
        testFixtureFactory.ensureBranch(branchId)
    }

    @Test
    fun `empty pre-opening branch has nothing left`() {
        val checklist = branchLifecycleService.deletionChecklist(branchId)
        assertFalse(checklist.deletable.not())
        assertTrue(checklist.blockedByStatus.not())
        assertEquals(0, checklist.items.sumOf { it.count })
    }

    @Test
    fun `auto-provisioned warehouse and activation code do not count`() {
        // ensureTable's zone/type belong to manual data, but the main warehouse is auto:
        // provision it like createBranch does and confirm it is ignored.
        testFixtureFactory.ensureWarehouse(
            id = "wh-delcheck-01",
            branchId = branchId,
            name = "Main WH",
            code = "WH-DC-01",
            warehouseRole = WarehouseRole.MAIN
        )
        jdbc.update(
            """INSERT INTO activation_codes (id, code, branch_id, branch_name, branch_code, device_code, device_name, company_id, company_name, status, created_at, expires_at)
               VALUES ('ac-delcheck-01', 'SUN-TEST', ?, 'B', 'BC', 'POS-01', 'POS Terminal (POS-01)', 'comp-001', 'Test Company', 'USED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '72 hours')""",
            branchId
        )
        val checklist = branchLifecycleService.deletionChecklist(branchId)
        assertEquals(0, checklist.items.sumOf { it.count })
    }

    @Test
    fun `branch with received stock reports leftovers`() {
        testFixtureFactory.ensureWarehouse(
            id = "wh-delcheck-02",
            branchId = branchId,
            name = "Main WH",
            code = "WH-DC-02",
            warehouseRole = WarehouseRole.MAIN
        )
        testFixtureFactory.ensureInventoryItem(id = "item-dc-01", sku = "SKU-DC-1", name = "Item DC")
        jdbc.update(
            """INSERT INTO inventory_stocks (id, warehouse_id, inventory_item_id, quantity)
               VALUES ('sl-dc-01', 'wh-delcheck-02', 'item-dc-01', 10)"""
        )
        val checklist = branchLifecycleService.deletionChecklist(branchId)
        assertTrue(checklist.items.sumOf { it.count } > 0)
        assertFalse(checklist.deletable)
    }

    @Test
    fun `branch with stock movement history reports leftovers`() {
        testFixtureFactory.ensureWarehouse(
            id = "wh-delcheck-03",
            branchId = branchId,
            name = "Main WH",
            code = "WH-DC-03",
            warehouseRole = WarehouseRole.MAIN
        )
        testFixtureFactory.ensureInventoryItem(id = "item-dc-02", sku = "SKU-DC-2", name = "Item DC 2")
        jdbc.update(
            """INSERT INTO stock_movements (id, warehouse_id, inventory_item_id, quantity, unit, movement_type)
               VALUES ('sm-dc-01', 'wh-delcheck-03', 'item-dc-02', 5, 'kg', 'RECEIVE')"""
        )
        val checklist = branchLifecycleService.deletionChecklist(branchId)
        assertTrue(checklist.items.sumOf { it.count } > 0)
        assertFalse(checklist.deletable)
    }

    @Test
    fun `branch with sales orders reports transaction leftovers`() {
        testFixtureFactory.ensureOrder(id = "ord-dc-01", branchId = branchId, orderNumber = "ORD-DC-1")
        val checklist = branchLifecycleService.deletionChecklist(branchId)
        assertTrue(checklist.items.sumOf { it.count } > 0)
        assertFalse(checklist.deletable)
    }

    @Test
    fun `active branch is always undeletable`() {
        jdbc.update("UPDATE branches SET status = 'ACTIVE' WHERE id = ?", branchId)
        val checklist = branchLifecycleService.deletionChecklist(branchId)
        assertTrue(checklist.blockedByStatus)
        assertFalse(checklist.deletable)
    }
}
