package com.sunpos.backend.domain.recipe

import com.sunpos.backend.common.TestFixtureFactory
import com.sunpos.backend.domain.businessday.BusinessDayService
import com.sunpos.backend.domain.businessday.BusinessDayStatus
import com.sunpos.backend.domain.catalog.CatalogService
import com.sunpos.backend.domain.catalog.MenuItemCreateDto
import com.sunpos.backend.domain.inventory.InventoryItem
import com.sunpos.backend.domain.inventory.InventoryService
import com.sunpos.backend.domain.inventory.MovementType
import com.sunpos.backend.domain.inventory.PurchaseReceiveDto
import com.sunpos.backend.domain.inventory.WarehouseRole
import com.sunpos.backend.domain.order.CreateOrderRequest
import com.sunpos.backend.domain.order.OrderItemRequest
import com.sunpos.backend.domain.order.OrderService
import com.sunpos.backend.domain.order.OrderStatus
import com.sunpos.backend.domain.organization.BranchRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * Closing a day is what turns a day of sales into stock movement, and it must not be refused because
 * an ingredient was not received yet (ADR 0032 / Spec 0034, ticket 01).
 *
 * The branch in these tests carries no special inventory settings, which is the case that used to
 * fail: the schema's default refuses negative stock, so the arithmetic threw before anything was
 * deducted. Note that "permits negative" is not "requires negative" -- the second test checks that
 * an ingredient with enough stock still ends the day positive.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PermissiveEodStockDeductionTest {

    @Autowired private lateinit var catalogService: CatalogService
    @Autowired private lateinit var inventoryService: InventoryService
    @Autowired private lateinit var recipeService: RecipeService
    @Autowired private lateinit var orderService: OrderService
    @Autowired private lateinit var businessDayService: BusinessDayService
    @Autowired private lateinit var testFixtureFactory: TestFixtureFactory
    @Autowired private lateinit var branchRepository: BranchRepository

    private val branchId = "branch-permissive-eod"
    private val warehouseId = "wh-permissive-eod"

    @BeforeEach
    fun setUp() {
        testFixtureFactory.ensureBranch(branchId)
        // The branch has not opened yet in the fixture, and a pre-opening branch skips recipe
        // deduction entirely, so the branch has to be operational for this to measure anything.
        branchRepository.findById(branchId).ifPresent { branch ->
            branch.status = "OPEN"
            branchRepository.save(branch)
        }
        // Orders belong to a business day, and one has to be open before any sale is recorded.
        businessDayService.getOrCreateOpenBusinessDay(branchId)
        testFixtureFactory.ensureMenuCategory("cat-permissive-eod", branchId, "Permissive EOD")
        testFixtureFactory.ensureWarehouse(
            id = warehouseId,
            branchId = branchId,
            name = "Permissive EOD Warehouse",
            code = "WH-PERM-EOD",
            warehouseRole = WarehouseRole.MAIN
        )
    }

    /** One portion of the menu consumes [perPortion] of the given ingredient. */
    private fun sellPortions(item: InventoryItem, perPortion: String, portions: String): String {
        val menuItem = catalogService.createMenuItem(
            MenuItemCreateDto(
                branchId = branchId,
                categoryId = "cat-permissive-eod",
                name = "Permissive EOD Dish ${item.sku}",
                sku = "SKU-${item.sku}",
                basePrice = BigDecimal("100.00")
            )
        )
        recipeService.createRecipe(
            CreateRecipeDto(
                menuItemId = menuItem.id,
                name = "Recipe ${item.sku}",
                version = "v1.0",
                ingredients = listOf(
                    RecipeIngredientDto(inventoryItemId = item.id, quantity = BigDecimal(perPortion), unit = item.unit)
                )
            )
        )
        val order = orderService.createOrder(
            CreateOrderRequest(
                branchId = branchId,
                items = listOf(
                    OrderItemRequest(
                        menuItemId = menuItem.id,
                        nameSnapshot = "Permissive EOD Dish",
                        unitPriceSnapshot = BigDecimal("100.00"),
                        quantity = BigDecimal(portions)
                    )
                )
            )
        )
        listOf(OrderStatus.CONFIRMED, OrderStatus.IN_KITCHEN, OrderStatus.READY, OrderStatus.SERVED, OrderStatus.COMPLETED)
            .forEach { orderService.transitionOrderStatus(order.id, it) }
        return order.id
    }

    @Test
    fun `an ingredient that was never received is deducted to negative instead of blocking the close`() {
        val pork = inventoryService.createInventoryItem(
            InventoryItem(sku = "RAW-PERMISSIVE-01", name = "Pork (never received)", unit = "kg", baseUnit = "g")
        )
        sellPortions(pork, perPortion = "0.1", portions = "2.0")

        val closed = businessDayService.closeBusinessDayEod(branchId, "mgr-01")

        assertEquals(BusinessDayStatus.CLOSED, closed.status, "ปิดวันต้องสำเร็จแม้วัตถุดิบยังไม่ถูกรับเข้า")
        val stock = inventoryService.getStockOnHand(warehouseId).first { it.inventoryItemId == pork.id }
        assertEquals(
            BigDecimal("-0.2000"),
            stock.quantity,
            "ต้องตัด 2 × 0.1 = 0.2 ออกจากยอด 0 แล้วเหลือติดลบ"
        )
        assertTrue(stock.isAutoProvisioned, "แถวสต็อกที่ระบบเปิดให้ควรถูกทำเครื่องหมายไว้")

        val movements = inventoryService.listMovements(warehouseId)
            .filter { it.inventoryItemId == pork.id && it.movementType == MovementType.SALE_CONSUMPTION }
        assertEquals(1, movements.size, "ต้องมีการเคลื่อนไหวสต็อกถูกบันทึกไว้ ไม่ใช่ข้ามรายการนี้")
        assertEquals(BigDecimal("-0.2000"), movements.first().quantity)
    }

    @Test
    fun `a branch that has not opened yet still skips recipe deduction`() {
        val trialBranchId = "branch-permissive-preopening"
        val trialWarehouseId = "wh-permissive-preopening"
        testFixtureFactory.ensureBranch(trialBranchId)
        testFixtureFactory.ensureMenuCategory("cat-permissive-preopening", trialBranchId, "Pre-Opening")
        testFixtureFactory.ensureWarehouse(
            id = trialWarehouseId,
            branchId = trialBranchId,
            name = "Pre-Opening Warehouse",
            code = "WH-PERM-PRE",
            warehouseRole = WarehouseRole.MAIN
        )
        businessDayService.getOrCreateOpenBusinessDay(trialBranchId)
        val pork = inventoryService.createInventoryItem(
            InventoryItem(sku = "RAW-PERMISSIVE-PRE", name = "Pork (trial period)", unit = "kg", baseUnit = "g")
        )
        val menuItem = catalogService.createMenuItem(
            MenuItemCreateDto(
                branchId = trialBranchId,
                categoryId = "cat-permissive-preopening",
                name = "Pre-Opening Dish",
                sku = "SKU-PRE-OPENING",
                basePrice = BigDecimal("100.00")
            )
        )
        recipeService.createRecipe(
            CreateRecipeDto(
                menuItemId = menuItem.id,
                name = "Pre-Opening Recipe",
                version = "v1.0",
                ingredients = listOf(RecipeIngredientDto(inventoryItemId = pork.id, quantity = BigDecimal("0.1"), unit = "kg"))
            )
        )
        val order = orderService.createOrder(
            CreateOrderRequest(
                branchId = trialBranchId,
                items = listOf(
                    OrderItemRequest(
                        menuItemId = menuItem.id,
                        nameSnapshot = "Pre-Opening Dish",
                        unitPriceSnapshot = BigDecimal("100.00"),
                        quantity = BigDecimal("2.0")
                    )
                )
            )
        )
        listOf(OrderStatus.CONFIRMED, OrderStatus.IN_KITCHEN, OrderStatus.READY, OrderStatus.SERVED, OrderStatus.COMPLETED)
            .forEach { orderService.transitionOrderStatus(order.id, it) }

        // A trial period is the PRE_OPENING status itself, and the fixture helpers above each set
        // the branch back to OPEN, so it has to be applied last, once the scene exists.
        branchRepository.findById(trialBranchId).ifPresent { branch ->
            branch.status = "PRE_OPENING"
            branch.isTestBranch = false
            branchRepository.save(branch)
        }
        assertEquals(
            "PRE_OPENING",
            branchRepository.findById(trialBranchId).orElseThrow().status,
            "เทสต์นี้ต้องรันบนสาขาที่ยังไม่เปิดร้านจริง ๆ"
        )
        assertTrue(
            inventoryService.getStockOnHand(trialWarehouseId).none { it.inventoryItemId == pork.id },
            "ต้องยังไม่มีแถวสต็อกก่อนปิดวัน เพื่อให้ผลลัพธ์มาจากการปิดวันเท่านั้น"
        )

        businessDayService.closeBusinessDayEod(trialBranchId, "mgr-01")

        assertTrue(
            inventoryService.getStockOnHand(trialWarehouseId).none { it.inventoryItemId == pork.id },
            "สาขาที่ยังไม่เปิดร้านต้องไม่ถูกตัดสต็อก และไม่ควรเปิดแถวสต็อกด้วย"
        )
    }

    @Test
    fun `an ingredient with enough stock still ends the day positive`() {
        val pork = inventoryService.createInventoryItem(
            InventoryItem(sku = "RAW-PERMISSIVE-02", name = "Pork (in stock)", unit = "kg", baseUnit = "g")
        )
        inventoryService.processPurchaseReceive(
            PurchaseReceiveDto(
                warehouseId = warehouseId,
                inventoryItemId = pork.id,
                quantity = BigDecimal("5.0"),
                unit = "kg",
                unitCost = BigDecimal("150.00")
            )
        )
        sellPortions(pork, perPortion = "0.1", portions = "2.0")

        businessDayService.closeBusinessDayEod(branchId, "mgr-01")

        val stock = inventoryService.getStockOnHand(warehouseId).first { it.inventoryItemId == pork.id }
        assertEquals(BigDecimal("4.8000"), stock.quantity, "ของที่มีพอก็ยังตัดจนเหลือบวกตามปกติ")
    }
}
