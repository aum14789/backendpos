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
import java.time.Instant

/**
 * Route-contract fix: the backoffice web calls GET /inventory/transfers, /inventory/counts
 * and /inventory/writes to render the stock history pages, but the backend only exposed
 * POST endpoints (create) — the pages silently loaded empty lists.
 * These tests pin the read side of each record type.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StockRecordListingTest {

    @Autowired private lateinit var inventoryService: InventoryService
    @Autowired private lateinit var testFixtureFactory: TestFixtureFactory

    private val branchId = "branch-listing-01"
    private val whId = "wh-listing-01"
    private val whTargetId = "wh-listing-02"

    @BeforeEach
    fun setUp() {
        testFixtureFactory.ensureBranch(branchId)
        testFixtureFactory.ensureWarehouse(
            id = whId,
            branchId = branchId,
            name = "Listing Main WH",
            code = "WH-LST-01",
            warehouseRole = WarehouseRole.MAIN
        )
        testFixtureFactory.ensureWarehouse(
            id = whTargetId,
            branchId = branchId,
            name = "Listing Target WH",
            code = "WH-LST-02",
            warehouseRole = WarehouseRole.DESTROY
        )
        testFixtureFactory.ensureInventoryItem(id = "item-x", sku = "SKU-LST-X", name = "Listing Test Item")
    }

    @Test
    fun `list transfers returns recorded transfers`() {
        inventoryService.createTransfer(
            CreateTransferDto(
                sourceWarehouseId = whId,
                targetWarehouseId = whTargetId,
                items = listOf(TransferItemDto(inventoryItemId = "item-x", quantity = BigDecimal("1"), unit = "kg"))
            )
        )

        val transfers = inventoryService.listTransfers()
        assertTrue(transfers.any { it.sourceWarehouseId == whId && it.targetWarehouseId == whTargetId })
    }

    @Test
    fun `list counts returns recorded counts`() {
        inventoryService.recordStockCountAndAdjust(
            StockCountCreateDto(
                warehouseId = whId,
                items = listOf(StockCountItemDto(inventoryItemId = "item-x", actualQty = BigDecimal("5")))
            )
        )

        val counts = inventoryService.listStockCounts(whId)
        assertTrue(counts.any { it.warehouseId == whId })
    }

    @Test
    fun `list wastes returns recorded wastes`() {
        // recordWaste deducts from existing stock — the item must be assigned to the warehouse first
        inventoryService.assignItemToWarehouse(whId, "item-x")
        inventoryService.recordWaste(
            StockWasteCreateDto(
                warehouseId = whId,
                inventoryItemId = "item-x",
                quantity = BigDecimal("2"),
                unit = "kg"
            )
        )

        val wastes = inventoryService.listStockWastes(whId)
        assertTrue(wastes.any { it.warehouseId == whId })
    }
}
