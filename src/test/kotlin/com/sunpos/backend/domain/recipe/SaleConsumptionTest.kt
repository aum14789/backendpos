package com.sunpos.backend.domain.recipe

import com.sunpos.backend.domain.catalog.CatalogService
import com.sunpos.backend.domain.catalog.MenuItemCreateDto
import com.sunpos.backend.domain.inventory.InventoryItem
import com.sunpos.backend.domain.inventory.InventoryService
import com.sunpos.backend.domain.inventory.PurchaseReceiveDto
import com.sunpos.backend.domain.order.CreateOrderRequest
import com.sunpos.backend.domain.order.OrderItemRequest
import com.sunpos.backend.domain.order.OrderService
import com.sunpos.backend.domain.businessday.BusinessDayService
import com.sunpos.backend.domain.organization.BranchRepository
import com.sunpos.backend.domain.payment.PaymentService
import com.sunpos.backend.domain.payment.PaymentMethod
import com.sunpos.backend.domain.payment.PaymentRequestDto
import com.sunpos.backend.domain.payment.PaymentStatus
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import org.springframework.context.ApplicationContext
import java.math.BigDecimal

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SaleConsumptionTest {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @Autowired
    private lateinit var catalogService: CatalogService

    @Autowired
    private lateinit var inventoryService: InventoryService

    @Autowired
    private lateinit var recipeService: RecipeService

    @Autowired
    private lateinit var orderService: OrderService

    @Autowired
    private lateinit var saleConsumptionService: SaleConsumptionService

    @Autowired
    private lateinit var traceabilityService: TraceabilityService

    @Autowired
    private lateinit var businessDayService: BusinessDayService

    /**
     * The baseline migration seeds `branch-001` and leaves its status at the column default,
     * PRE_OPENING. [com.sunpos.backend.domain.recipe.InventoryEodConsumptionService] deliberately
     * skips stock deduction for a branch that has not opened yet, so a test that asserts real
     * end-of-day consumption has to put the branch into an operational state first.
     */
    private fun markBranchOperational(branchId: String) {
        val branches = applicationContext.getBean(BranchRepository::class.java)
        branches.findById(branchId).ifPresent { branch ->
            branch.status = "OPEN"
            branches.save(branch)
        }
    }

    @Test
    fun `test sale order completion triggers recipe ingredient consumption and is idempotent`() {
        val pork = inventoryService.createInventoryItem(InventoryItem(sku = "RAW-PORK-SALE", name = "Raw Pork", unit = "kg", baseUnit = "g"))

        markBranchOperational("branch-001")

        // Create and open Business Day
        val bday = businessDayService.getOrCreateOpenBusinessDay("branch-001")

        // Receive Pork 20 kg into Sukhumvit Branch Warehouse
        inventoryService.processPurchaseReceive(
            PurchaseReceiveDto(warehouseId = "wh-sukhumvit", inventoryItemId = pork.id, quantity = BigDecimal("20.0"), unit = "kg", unitCost = BigDecimal("150.00"))
        )

        val menuItem = catalogService.createMenuItem(
            MenuItemCreateDto(branchId = "branch-001", categoryId = "cat-001", name = "Pad Kra Pao Sale", sku = "SKU-KRAPAO-SALE", basePrice = BigDecimal("120.00"))
        )

        // Create Recipe (1 Portion requires 0.1 kg Pork)
        recipeService.createRecipe(
            CreateRecipeDto(
                menuItemId = menuItem.id,
                name = "Kra Pao Recipe v1.0",
                version = "v1.0",
                ingredients = listOf(RecipeIngredientDto(inventoryItemId = pork.id, quantity = BigDecimal("0.1"), unit = "kg"))
            )
        )

        // Create Order for 2 Portions
        val order = orderService.createOrder(
            CreateOrderRequest(
                branchId = "branch-001",
                items = listOf(OrderItemRequest(menuItemId = menuItem.id, nameSnapshot = "Pad Kra Pao Sale", unitPriceSnapshot = BigDecimal("120.00"), quantity = BigDecimal("2.0")))
            )
        )

        // Simulate complete order status for consumption
        orderService.transitionOrderStatus(order.id, com.sunpos.backend.domain.order.OrderStatus.CONFIRMED)
        orderService.transitionOrderStatus(order.id, com.sunpos.backend.domain.order.OrderStatus.IN_KITCHEN)
        orderService.transitionOrderStatus(order.id, com.sunpos.backend.domain.order.OrderStatus.READY)
        orderService.transitionOrderStatus(order.id, com.sunpos.backend.domain.order.OrderStatus.SERVED)
        orderService.transitionOrderStatus(order.id, com.sunpos.backend.domain.order.OrderStatus.COMPLETED)

        // Process Sale Consumption
        val movs1 = saleConsumptionService.processSaleConsumption(order.id, "wh-sukhumvit")
        assertEquals(1, movs1.size)
        // Needed: 2 * 0.1 = 0.2 kg. Movement quantity should be -0.2000 kg
        assertEquals(BigDecimal("-0.2000"), movs1[0].quantity)

        // Check Sukhumvit Stock: 20 - 0.2 = 19.8 kg
        val stockPostSale = inventoryService.getStockOnHand("wh-sukhumvit").first { it.inventoryItemId == pork.id }
        assertEquals(BigDecimal("19.8000"), stockPostSale.quantity)

        // Test Idempotency: Processing sale consumption again on same order should not duplicate movements!
        val movs2 = saleConsumptionService.processSaleConsumption(order.id, "wh-sukhumvit")
        assertEquals(1, movs2.size)

        val stockPostRetry = inventoryService.getStockOnHand("wh-sukhumvit").first { it.inventoryItemId == pork.id }
        assertEquals(BigDecimal("19.8000"), stockPostRetry.quantity)
    }

    @Autowired
    private lateinit var paymentService: PaymentService

    @Test
    fun `test comprehensive end-to-end business EOD scenario`() {
        val pork = inventoryService.createInventoryItem(InventoryItem(sku = "RAW-PORK-E2E", name = "Raw Pork", unit = "kg", baseUnit = "g"))

        markBranchOperational("branch-001")

        // 1. Open Business Day
        val bday = businessDayService.getOrCreateOpenBusinessDay("branch-001")
        assertEquals(com.sunpos.backend.domain.businessday.BusinessDayStatus.OPEN, bday.status)

        val warehouseRepository = applicationContext.getBean(com.sunpos.backend.domain.inventory.WarehouseRepository::class.java)

        // branch-001 ships with a seeded main warehouse (wh-branch-001), and end-of-day consumption
        // runs once for every main warehouse of the branch. Retire the seeded one so this scenario
        // deducts from exactly the warehouse it asserts on.
        warehouseRepository.findById("wh-branch-001").ifPresent { seeded ->
            seeded.isActive = false
            warehouseRepository.save(seeded)
        }

        // Seed Warehouse for branch-001
        warehouseRepository.save(com.sunpos.backend.domain.inventory.Warehouse(id = "wh-sukhumvit", branchId = "branch-001", name = "Sukhumvit Warehouse", code = "WH-SUK-01"))

        // Setup pork stock in wh-sukhumvit: 10 kg
        inventoryService.processPurchaseReceive(
            PurchaseReceiveDto(warehouseId = "wh-sukhumvit", inventoryItemId = pork.id, quantity = BigDecimal("10.0000"), unit = "kg", unitCost = BigDecimal("150.0000"))
        )

        val menuItem = catalogService.createMenuItem(
            MenuItemCreateDto(branchId = "branch-001", categoryId = "cat-001", name = "Pad Kra Pao", basePrice = BigDecimal("100.0000"))
        )

        // Create Recipe (1 Portion = 0.1 kg Pork)
        recipeService.createRecipe(
            CreateRecipeDto(
                menuItemId = menuItem.id,
                name = "Kra Pao Recipe",
                ingredients = listOf(RecipeIngredientDto(inventoryItemId = pork.id, quantity = BigDecimal("0.1"), unit = "kg"))
            )
        )

        // 2. Create Order 1: 5 portions
        val order1 = orderService.createOrder(
            CreateOrderRequest(
                branchId = "branch-001",
                items = listOf(OrderItemRequest(menuItemId = menuItem.id, nameSnapshot = "Pad Kra Pao", unitPriceSnapshot = BigDecimal("100.0000"), quantity = BigDecimal("5.0")))
            )
        )

        // 3. Pay Order 1 (automatically completes the order because total payments = order total)
        paymentService.processPayment(
            PaymentRequestDto(orderId = order1.id, branchId = "branch-001", paymentMethod = PaymentMethod.CASH, amount = BigDecimal("500.0000"))
        )

        // 4. Assert stock is unchanged
        val stockPostPay1 = inventoryService.getStockOnHand("wh-sukhumvit").first { it.inventoryItemId == pork.id }
        assertEquals(BigDecimal("10.0000"), stockPostPay1.quantity)

        // 5. Create Order 2: 3 portions
        val order2 = orderService.createOrder(
            CreateOrderRequest(
                branchId = "branch-001",
                items = listOf(OrderItemRequest(menuItemId = menuItem.id, nameSnapshot = "Pad Kra Pao", unitPriceSnapshot = BigDecimal("100.0000"), quantity = BigDecimal("3.0")))
            )
        )

        // 6. Pay Order 2 (automatically completes the order)
        paymentService.processPayment(
            PaymentRequestDto(orderId = order2.id, branchId = "branch-001", paymentMethod = PaymentMethod.CASH, amount = BigDecimal("300.0000"))
        )

        // 7. Assert stock is unchanged
        val stockPostPay2 = inventoryService.getStockOnHand("wh-sukhumvit").first { it.inventoryItemId == pork.id }
        assertEquals(BigDecimal("10.0000"), stockPostPay2.quantity)

        // 8. Close Business Day
        val closedBday = businessDayService.closeBusinessDayEod("branch-001", "mgr-01")

        // 9. Verify that total Pad Thai quantity is processed, EOD batch is COMPLETED, BusinessDay is CLOSED
        assertEquals(com.sunpos.backend.domain.businessday.BusinessDayStatus.CLOSED, closedBday.status)

        // Stock must have been deducted once: (5 + 3) * 0.1 = 0.8 kg. Remaining: 10 - 0.8 = 9.2 kg
        val stockPostEod = inventoryService.getStockOnHand("wh-sukhumvit").first { it.inventoryItemId == pork.id }
        assertEquals(BigDecimal("9.2000"), stockPostEod.quantity)

        // 10. Call close again to verify idempotency (no additional consumption)
        businessDayService.closeBusinessDayEod("branch-001", "mgr-01")
        val stockPostSecondClose = inventoryService.getStockOnHand("wh-sukhumvit").first { it.inventoryItemId == pork.id }
        assertEquals(BigDecimal("9.2000"), stockPostSecondClose.quantity)
    }
}
