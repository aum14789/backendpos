package com.sunpos.backend.domain.purchasing

import com.sunpos.backend.domain.inventory.InventoryItem
import com.sunpos.backend.domain.inventory.InventoryService
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
class PurchasingServiceTest {

    @Autowired
    private lateinit var purchasingService: PurchasingService

    @Autowired
    private lateinit var inventoryService: InventoryService

    @Test
    fun `test PO approval lifecycle, partial and full receive, damaged goods, purchase return, and WAC cost recalculation`() {
        // 1. Setup Supplier & Raw Item
        val supplier = purchasingService.createSupplier(
            Supplier(code = "SUP-TEST", name = "Test Supplier Ltd", contactPerson = "John")
        )
        val rawPork = inventoryService.createInventoryItem(
            InventoryItem(sku = "RAW-PORK-PO", name = "Raw Pork PO", unit = "kg", baseUnit = "g")
        )

        // 2. Create PO for 10 kg @ 100.00 THB/kg
        val po = purchasingService.createPO(
            CreatePurchaseOrderDto(
                supplierId = supplier.id,
                warehouseId = "wh-sukhumvit",
                items = listOf(POItemDto(inventoryItemId = rawPork.id, orderedQty = BigDecimal("10.0"), unit = "kg", expectedPrice = BigDecimal("100.00")))
            )
        )
        assertEquals(POStatus.DRAFT, po.status)
        assertEquals(BigDecimal("1000.0000"), po.totalExpectedAmount)

        // 3. Approve and Order PO
        val approved = purchasingService.approvePO(po.id, approvedBy = "admin")
        assertEquals(POStatus.APPROVED, approved.status)

        val ordered = purchasingService.orderPO(po.id)
        assertEquals(POStatus.ORDERED, ordered.status)

        // 4. Partial Receive 6 kg (1 kg damaged -> net received = 5 kg @ 120.00 THB/kg)
        val grn1 = purchasingService.processGoodsReceive(
            CreateGoodsReceiveDto(
                purchaseOrderId = po.id,
                receivedBy = "john",
                items = listOf(GRNItemDto(inventoryItemId = rawPork.id, receivedQty = BigDecimal("6.0"), damagedQty = BigDecimal("1.0"), unit = "kg", actualUnitCost = BigDecimal("120.00")))
            )
        )
        assertNotNull(grn1.id)

        // Stock should now be 5 kg @ 120.00 THB/kg
        val stockPostGrn1 = inventoryService.getStockOnHand("wh-sukhumvit").first { it.inventoryItemId == rawPork.id }
        assertEquals(BigDecimal("5.0000"), stockPostGrn1.quantity)
        assertEquals(BigDecimal("120.0000"), stockPostGrn1.weightedAverageCost)

        // PO status should be PARTIALLY_RECEIVED
        val poPostGrn1 = purchasingService.listPOs().first { it.id == po.id }
        assertEquals(POStatus.PARTIALLY_RECEIVED, poPostGrn1.status)

        // 5. Full Receive Remaining 4 kg @ 150.00 THB/kg
        val grn2 = purchasingService.processGoodsReceive(
            CreateGoodsReceiveDto(
                purchaseOrderId = po.id,
                receivedBy = "john",
                items = listOf(GRNItemDto(inventoryItemId = rawPork.id, receivedQty = BigDecimal("4.0"), unit = "kg", actualUnitCost = BigDecimal("150.00")))
            )
        )
        assertNotNull(grn2.id)

        // Stock should now be 5 + 4 = 9 kg. New WAC = (5*120 + 4*150) / 9 = 1200 / 9 = 133.3333 THB/kg
        val stockPostGrn2 = inventoryService.getStockOnHand("wh-sukhumvit").first { it.inventoryItemId == rawPork.id }
        assertEquals(BigDecimal("9.0000"), stockPostGrn2.quantity)
        assertEquals(BigDecimal("133.3333"), stockPostGrn2.weightedAverageCost)

        // PO status should now be RECEIVED
        val poPostGrn2 = purchasingService.listPOs().first { it.id == po.id }
        assertEquals(POStatus.RECEIVED, poPostGrn2.status)

        // 6. Purchase Return 2 kg back to supplier
        val pr = purchasingService.processPurchaseReturn(
            CreatePurchaseReturnDto(
                goodsReceiveId = grn2.id,
                reason = "Defective quality",
                items = listOf(PRItemDto(inventoryItemId = rawPork.id, returnQty = BigDecimal("2.0"), unit = "kg", unitCost = BigDecimal("150.00")))
            )
        )
        assertNotNull(pr.id)

        // Stock should now be 9 - 2 = 7 kg
        val stockPostPR = inventoryService.getStockOnHand("wh-sukhumvit").first { it.inventoryItemId == rawPork.id }
        assertEquals(BigDecimal("7.0000"), stockPostPR.quantity)
    }
}
