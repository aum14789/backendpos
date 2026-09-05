package com.sunpos.backend.domain.recipe

import com.sunpos.backend.domain.inventory.InventoryItem
import com.sunpos.backend.domain.inventory.InventoryService
import com.sunpos.backend.domain.inventory.PurchaseReceiveDto
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
class ProductionServiceTest {

    @Autowired
    private lateinit var recipeService: RecipeService

    @Autowired
    private lateinit var productionService: ProductionService

    @Autowired
    private lateinit var inventoryService: InventoryService

    @Test
    fun `test central kitchen production order completion, material consumption, finished goods creation and yield`() {
        val rawPork = inventoryService.createInventoryItem(InventoryItem(sku = "RAW-PORK-PROD", name = "Raw Pork", unit = "kg", baseUnit = "g"))
        val fgSoup = inventoryService.createInventoryItem(InventoryItem(sku = "FG-SOUP-PROD", name = "Finished Soup", unit = "L", baseUnit = "ml"))

        // Receive Raw Pork 50 kg @ 100.00 THB/kg into Central Warehouse
        inventoryService.processPurchaseReceive(
            PurchaseReceiveDto(warehouseId = "wh-central", inventoryItemId = rawPork.id, quantity = BigDecimal("50.0"), unit = "kg", unitCost = BigDecimal("100.00"))
        )

        // Create BOM for Soup (100L output requires 20kg Raw Pork)
        val bom = recipeService.createBom(
            CreateBomDto(
                finishedInventoryItemId = fgSoup.id,
                name = "Soup 100L BOM",
                plannedOutputQuantity = BigDecimal("100.0"),
                outputUnit = "L",
                items = listOf(BomItemDto(rawInventoryItemId = rawPork.id, quantity = BigDecimal("20.0"), unit = "kg"))
            )
        )

        // Create Production Order for 100L Soup
        val pOrder = productionService.createProductionOrder(
            CreateProductionOrderDto(warehouseId = "wh-central", bomId = bom.id, plannedQuantity = BigDecimal("100.0"), unit = "L")
        )
        assertEquals(ProductionStatus.APPROVED, pOrder.status)

        productionService.startProduction(pOrder.id)

        // Complete production with Actual Output = 95L (Yield = 95.00%)
        val completed = productionService.completeProduction(
            pOrder.id,
            CompleteProductionOrderDto(actualQuantity = BigDecimal("95.0"), laborCost = BigDecimal("500.00"))
        )
        assertEquals(ProductionStatus.COMPLETED, completed.status)
        assertEquals(BigDecimal("95.00"), completed.yieldPercentage)

        // Check Raw Pork Stock: 50 - 20 = 30 kg
        val rawStock = inventoryService.getStockOnHand("wh-central").first { it.inventoryItemId == rawPork.id }
        assertEquals(BigDecimal("30.0000"), rawStock.quantity)

        // Check Finished Goods Stock: 95 L
        val fgStock = inventoryService.getStockOnHand("wh-central").first { it.inventoryItemId == fgSoup.id }
        assertEquals(BigDecimal("95.0000"), fgStock.quantity)

        // Material Cost = 20 * 100 = 2000. Labor Cost = 500. Grand Total = 2500. Finished Unit Cost = 2500 / 95 = 26.3158 THB/L
        assertEquals(BigDecimal("2500.0000"), completed.totalMaterialCost.add(completed.laborCost))
    }
}
