package com.sunpos.backend.domain.inventory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The rule that decides which warehouse receives daily sales consumption, stated in terms of the
 * warehouse's role (Spec 0033). The name matching that used to live here is gone: a warehouse's
 * name is a label for humans and nothing else.
 */
class SalesDeductionWarehousePureTest {

    private fun warehouse(
        id: String,
        name: String = "Warehouse $id",
        code: String = "WH-$id",
        role: WarehouseRole = WarehouseRole.MAIN,
        isActive: Boolean = true
    ) = Warehouse(id = id, branchId = "branch-1", name = name, code = code, warehouseRole = role, isActive = isActive)

    @Test
    fun `the main warehouse is the deduction target whatever it is called`() {
        val main = warehouse("wh-main", name = "Back Room", code = "WH-BACK")

        val resolution = SalesDeductionWarehouse.branchMainWarehouse(listOf(main))

        assertEquals(SalesDeductionWarehouse.Resolution.Found(main), resolution)
    }

    @Test
    fun `a main warehouse named like a waste room is still the deduction target`() {
        val misleadinglyNamed = warehouse("wh-main", name = "Food Waste Room", code = "WH-WASTE-99")

        val resolution = SalesDeductionWarehouse.branchMainWarehouse(listOf(misleadinglyNamed))

        assertEquals(SalesDeductionWarehouse.Resolution.Found(misleadinglyNamed), resolution)
    }

    /**
     * The bug this rule removes: a waste warehouse whose name says nothing about waste used to look
     * like an ordinary branch warehouse, so a branch could deduct sales from its loss stock.
     */
    @Test
    fun `a waste warehouse with an innocent name is never the deduction target`() {
        val waste = warehouse("wh-store", name = "Storage Room 1", code = "WH-1", role = WarehouseRole.WASTE)

        assertEquals(SalesDeductionWarehouse.Resolution.Missing, SalesDeductionWarehouse.branchMainWarehouse(listOf(waste)))
    }

    @Test
    fun `central and destroy warehouses are never the deduction target`() {
        val central = warehouse("wh-central", role = WarehouseRole.CENTRAL)
        val destroy = warehouse("wh-destroy", role = WarehouseRole.DESTROY)

        assertEquals(
            SalesDeductionWarehouse.Resolution.Missing,
            SalesDeductionWarehouse.branchMainWarehouse(listOf(central, destroy))
        )
    }

    @Test
    fun `an inactive main warehouse does not count`() {
        val retired = warehouse("wh-old", role = WarehouseRole.MAIN, isActive = false)

        assertEquals(SalesDeductionWarehouse.Resolution.Missing, SalesDeductionWarehouse.branchMainWarehouse(listOf(retired)))
    }

    @Test
    fun `a branch with no warehouse at all has no main warehouse`() {
        assertEquals(SalesDeductionWarehouse.Resolution.Missing, SalesDeductionWarehouse.branchMainWarehouse(emptyList()))
    }

    @Test
    fun `two active main warehouses are reported as ambiguous rather than picked between`() {
        val first = warehouse("wh-a")
        val second = warehouse("wh-b")

        val resolution = SalesDeductionWarehouse.branchMainWarehouse(listOf(first, second))

        assertEquals(SalesDeductionWarehouse.Resolution.Ambiguous(listOf(first, second)), resolution)
    }

    @Test
    fun `only an active main warehouse may receive sales consumption`() {
        assertTrue(SalesDeductionWarehouse.mayReceiveSalesConsumption(warehouse("wh-main")))
        assertFalse(SalesDeductionWarehouse.mayReceiveSalesConsumption(warehouse("wh-waste", role = WarehouseRole.WASTE)))
        assertFalse(SalesDeductionWarehouse.mayReceiveSalesConsumption(warehouse("wh-destroy", role = WarehouseRole.DESTROY)))
        assertFalse(SalesDeductionWarehouse.mayReceiveSalesConsumption(warehouse("wh-central", role = WarehouseRole.CENTRAL)))
        assertFalse(SalesDeductionWarehouse.mayReceiveSalesConsumption(warehouse("wh-old", isActive = false)))
    }
}
