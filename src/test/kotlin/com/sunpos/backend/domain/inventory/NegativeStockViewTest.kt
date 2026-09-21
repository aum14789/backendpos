package com.sunpos.backend.domain.inventory

import com.sunpos.backend.common.TestFixtureFactory
import com.sunpos.backend.domain.organization.BranchRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * Ticket 04 (Spec 0034 / ADR 0032): Negative Stock View.
 * Displays only stocks with quantity < 0, separated by auto-provisioned status,
 * and items disappear when goods are received to bring balance >= 0.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NegativeStockViewTest {

    @Autowired private lateinit var inventoryService: InventoryService
    @Autowired private lateinit var stockRepository: InventoryStockRepository
    @Autowired private lateinit var testFixtureFactory: TestFixtureFactory
    @Autowired private lateinit var branchRepository: BranchRepository

    private val branch1 = "branch-neg-01"
    private val branch2 = "branch-neg-02"
    private val wh1 = "wh-neg-01"
    private val wh2 = "wh-neg-02"

    @BeforeEach
    fun setUp() {
        testFixtureFactory.ensureBranch(branch1)
        testFixtureFactory.ensureBranch(branch2)
        testFixtureFactory.ensureWarehouse(
            id = wh1,
            branchId = branch1,
            name = "Main Warehouse 01",
            code = "WH-NEG-01",
            warehouseRole = WarehouseRole.MAIN
        )
        testFixtureFactory.ensureWarehouse(
            id = wh2,
            branchId = branch2,
            name = "Main Warehouse 02",
            code = "WH-NEG-02",
            warehouseRole = WarehouseRole.MAIN
        )
    }

    @Test
    fun `negative stock view returns only items with balance below zero`() {
        val item1 = inventoryService.createInventoryItem(
            InventoryItem(sku = "RAW-NEG-01", name = "Beef Shank", unit = "kg", standardCost = BigDecimal("250.00"))
        )
        val item2 = inventoryService.createInventoryItem(
            InventoryItem(sku = "RAW-POS-01", name = "Chicken Breast", unit = "kg", standardCost = BigDecimal("80.00"))
        )

        // Item 1 has negative stock (auto-provisioned)
        stockRepository.save(
            InventoryStock(
                warehouseId = wh1,
                inventoryItemId = item1.id,
                quantity = BigDecimal("-3.5000"),
                weightedAverageCost = BigDecimal("250.00"),
                isAutoProvisioned = true
            )
        )

        // Item 2 has positive stock
        stockRepository.save(
            InventoryStock(
                warehouseId = wh1,
                inventoryItemId = item2.id,
                quantity = BigDecimal("10.0000"),
                weightedAverageCost = BigDecimal("80.00"),
                isAutoProvisioned = false
            )
        )

        val results = inventoryService.getNegativeStocks(branch1)
        assertEquals(1, results.size, "ต้องมีเฉพาะรายการที่ยอดคงเหลือติดลบเท่านั้น")

        val negItem = results.first()
        assertEquals(wh1, negItem.warehouseId)
        assertEquals(item1.id, negItem.inventoryItemId)
        assertEquals("RAW-NEG-01", negItem.itemSku)
        assertEquals(0, BigDecimal("-3.50").compareTo(negItem.quantity))
        assertEquals(0, BigDecimal("250.00").compareTo(negItem.unitCost))
        assertTrue(negItem.isAutoProvisioned, "ต้องระบุธง auto-provisioned ได้ถูกต้อง")
    }

    @Test
    fun `filtering by branchId scopes results to that branch`() {
        val item = inventoryService.createInventoryItem(
            InventoryItem(sku = "RAW-NEG-SCOPE", name = "Pork Neck", unit = "kg")
        )

        stockRepository.save(
            InventoryStock(
                warehouseId = wh1,
                inventoryItemId = item.id,
                quantity = BigDecimal("-2.0000")
            )
        )
        stockRepository.save(
            InventoryStock(
                warehouseId = wh2,
                inventoryItemId = item.id,
                quantity = BigDecimal("-4.0000")
            )
        )

        val branch1Results = inventoryService.getNegativeStocks(branch1)
        assertEquals(1, branch1Results.size)
        assertEquals(wh1, branch1Results.first().warehouseId)
        assertEquals(BigDecimal("-2.0000"), branch1Results.first().quantity)

        val branch2Results = inventoryService.getNegativeStocks(branch2)
        assertEquals(1, branch2Results.size)
        assertEquals(wh2, branch2Results.first().warehouseId)
        assertEquals(BigDecimal("-4.0000"), branch2Results.first().quantity)

        val allResults = inventoryService.getNegativeStocks(null)
        assertEquals(2, allResults.size, "เมื่อไม่ระบุสาขา ต้องแสดงทุกสาขา")
    }

    @Test
    fun `items disappear from negative stock view when stock is received to non-negative balance`() {
        val item = inventoryService.createInventoryItem(
            InventoryItem(sku = "RAW-NEG-RESOLVE", name = "Butter", unit = "kg")
        )

        val stock = stockRepository.save(
            InventoryStock(
                warehouseId = wh1,
                inventoryItemId = item.id,
                quantity = BigDecimal("-5.0000"),
                weightedAverageCost = BigDecimal("100.00")
            )
        )

        var results = inventoryService.getNegativeStocks(branch1)
        assertEquals(1, results.size)

        // Receive goods to cover the negative balance: receive 5 kg
        inventoryService.processPurchaseReceive(
            PurchaseReceiveDto(
                warehouseId = wh1,
                inventoryItemId = item.id,
                quantity = BigDecimal("5.0000"),
                unit = "kg",
                unitCost = BigDecimal("100.00")
            )
        )

        results = inventoryService.getNegativeStocks(branch1)
        assertTrue(results.isEmpty(), "เมื่อรับของเข้าจนยอดกลับเป็น 0 หรือบวก รายการต้องหายไปจากมุมมองโดยอัตโนมัติ")
    }
}
