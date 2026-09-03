package com.sunpos.backend.domain.inventory

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InventoryServiceTest {

    @Autowired
    private lateinit var inventoryService: InventoryService

    @Test
    fun `test purchase receive, WAC update, transfer in-transit, count adjustment, and waste`() {
        val item = inventoryService.createInventoryItem(
            InventoryItem(sku = "RAW-BEEF", name = "Raw Beef", unit = "kg", baseUnit = "g")
        )

        // 1. Purchase Receive 10 kg @ 200.00 THB/kg into Central Warehouse
        val mov1 = inventoryService.processPurchaseReceive(
            PurchaseReceiveDto(warehouseId = "wh-central", inventoryItemId = item.id, quantity = BigDecimal("10.0"), unit = "kg", unitCost = BigDecimal("200.00"))
        )
        assertNotNull(mov1.id)
        assertEquals(MovementType.PURCHASE, mov1.movementType)

        val stocksCentral = inventoryService.getStockOnHand("wh-central").filter { it.inventoryItemId == item.id }
        assertEquals(1, stocksCentral.size)
        assertEquals(BigDecimal("10.0000"), stocksCentral[0].quantity)
        assertEquals(BigDecimal("200.0000"), stocksCentral[0].weightedAverageCost)

        // 2. Transfer 4 kg from Central to Sukhumvit (Shipped -> Received)
        val transfer = inventoryService.createTransfer(
            CreateTransferDto(
                sourceWarehouseId = "wh-central",
                targetWarehouseId = "wh-sukhumvit",
                items = listOf(TransferItemDto(inventoryItemId = item.id, quantity = BigDecimal("4.0"), unit = "kg"))
            )
        )
        assertEquals(TransferStatus.REQUESTED, transfer.status)

        val shipped = inventoryService.shipTransfer(transfer.id)
        assertEquals(TransferStatus.SHIPPED, shipped.status)

        // Central stock should now be 10 - 4 = 6 kg
        val stocksCentralPostShip = inventoryService.getStockOnHand("wh-central").first { it.inventoryItemId == item.id }
        assertEquals(BigDecimal("6.0000"), stocksCentralPostShip.quantity)

        val received = inventoryService.receiveTransfer(transfer.id)
        assertEquals(TransferStatus.RECEIVED, received.status)

        // Sukhumvit stock should now be 4 kg
        val stocksSukhumvit = inventoryService.getStockOnHand("wh-sukhumvit").first { it.inventoryItemId == item.id }
        assertEquals(BigDecimal("4.0000"), stocksSukhumvit.quantity)

        // 3. Record Waste 1 kg on Sukhumvit
        val waste = inventoryService.recordWaste(
            StockWasteCreateDto(warehouseId = "wh-sukhumvit", inventoryItemId = item.id, quantity = BigDecimal("1.0"), unit = "kg", reason = "Spoiled")
        )
        assertNotNull(waste.id)
        val stocksSukhumvitPostWaste = inventoryService.getStockOnHand("wh-sukhumvit").first { it.inventoryItemId == item.id }
        assertEquals(BigDecimal("3.0000"), stocksSukhumvitPostWaste.quantity)

        // 4. Physical Stock Count: Counted 5 kg actual on Sukhumvit -> Creates +2 kg Adjustment
        val count = inventoryService.recordStockCountAndAdjust(
            StockCountCreateDto(
                warehouseId = "wh-sukhumvit",
                items = listOf(StockCountItemDto(inventoryItemId = item.id, actualQty = BigDecimal("5.0")))
            )
        )
        assertEquals(CountStatus.APPROVED, count.status)
        val stocksSukhumvitPostCount = inventoryService.getStockOnHand("wh-sukhumvit").first { it.inventoryItemId == item.id }
        assertEquals(BigDecimal("5.0000"), stocksSukhumvitPostCount.quantity)
    }
}
