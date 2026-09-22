package com.sunpos.backend.domain.inventory

import com.sunpos.backend.common.TestFixtureFactory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * Bug report from the deployed backoffice: the stock overview page calls
 * GET /inventory/stocks and GET /inventory/movements without a warehouseId
 * (to list across all warehouses), but both endpoints required the parameter
 * and returned 500 ("Required request parameter 'warehouseId' is not present").
 * The fix mirrors listTransfers/listStockCounts: the parameter becomes optional
 * and omitting it lists across every warehouse.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StockOverviewListingTest {

    @Autowired private lateinit var inventoryService: InventoryService
    @Autowired private lateinit var testFixtureFactory: TestFixtureFactory

    private val branchId = "branch-overview-01"
    private val whAId = "wh-overview-01"
    private val whBId = "wh-overview-02"

    @BeforeEach
    fun setUp() {
        testFixtureFactory.ensureBranch(branchId)
        testFixtureFactory.ensureWarehouse(
            id = whAId,
            branchId = branchId,
            name = "Overview WH A",
            code = "WH-OVR-01",
            warehouseRole = WarehouseRole.MAIN
        )
        testFixtureFactory.ensureWarehouse(
            id = whBId,
            branchId = branchId,
            name = "Overview WH B",
            code = "WH-OVR-02",
            warehouseRole = WarehouseRole.MAIN
        )
        testFixtureFactory.ensureInventoryItem(id = "item-ovr", sku = "SKU-OVR-1", name = "Overview Item")
    }

    @Test
    fun `stock on hand without warehouseId lists every warehouse`() {
        inventoryService.assignItemToWarehouse(whAId, "item-ovr")
        inventoryService.assignItemToWarehouse(whBId, "item-ovr")

        val all = inventoryService.getStockOnHand(null)
        assertTrue(all.any { it.warehouseId == whAId })
        assertTrue(all.any { it.warehouseId == whBId })
    }

    @Test
    fun `stock on hand with warehouseId lists only that warehouse`() {
        inventoryService.assignItemToWarehouse(whAId, "item-ovr")
        inventoryService.assignItemToWarehouse(whBId, "item-ovr")

        val onlyA = inventoryService.getStockOnHand(whAId)
        assertTrue(onlyA.all { it.warehouseId == whAId })
        assertTrue(onlyA.any { it.warehouseId == whAId })
    }

    @Test
    fun `movements without warehouseId list every warehouse`() {
        inventoryService.assignItemToWarehouse(whAId, "item-ovr")
        inventoryService.recordWaste(
            StockWasteCreateDto(
                warehouseId = whAId,
                inventoryItemId = "item-ovr",
                quantity = BigDecimal("1"),
                unit = "kg"
            )
        )

        val all = inventoryService.listMovements(null)
        assertTrue(all.any { it.warehouseId == whAId })
    }
}
