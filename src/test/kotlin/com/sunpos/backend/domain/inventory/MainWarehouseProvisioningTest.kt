package com.sunpos.backend.domain.inventory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The main warehouse every branch starts with (Spec 0033 / ticket 04). Both branch-creation paths
 * go through this, so the shape is pinned here rather than discovered from whichever path ran.
 */
class MainWarehouseProvisioningTest {

    @Test
    fun `the main warehouse is named after its branch and carries the branch code`() {
        val warehouse = MainWarehouseProvisioning.warehouseFor("branch-9", "สาขาสยาม", "SIAM")

        assertEquals("branch-9", warehouse.branchId)
        assertEquals("คลังหลัก - สาขาสยาม", warehouse.name)
        assertEquals("WH-SIAM", warehouse.code)
        assertEquals(WarehouseRole.MAIN, warehouse.warehouseRole)
        assertTrue(warehouse.isActive)
    }

    @Test
    fun `a branch without a code still gets a usable warehouse code`() {
        val warehouse = MainWarehouseProvisioning.warehouseFor("branch-abcdef123", "Branch", "")

        assertEquals("WH-branch-a", warehouse.code)
    }

    @Test
    fun `two branches never share a warehouse id`() {
        val first = MainWarehouseProvisioning.warehouseFor("branch-1", "One", "B1")
        val second = MainWarehouseProvisioning.warehouseFor("branch-1", "One", "B1")

        assertTrue(first.id != second.id)
    }
}
