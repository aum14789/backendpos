package com.sunpos.backend.domain.e2e

import com.sunpos.backend.domain.businessday.BusinessDayService
import com.sunpos.backend.domain.businessday.BusinessDayStatus
import com.sunpos.backend.domain.catalog.*
import com.sunpos.backend.domain.inventory.*
import com.sunpos.backend.domain.order.*
import com.sunpos.backend.domain.payment.*
import com.sunpos.backend.domain.promotion.*
import com.sunpos.backend.domain.recipe.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MasterRequirementEndToEndTest {

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
    private lateinit var paymentService: PaymentService

    @Autowired
    private lateinit var promotionService: PromotionService

    @Autowired
    private lateinit var promotionRepository: PromotionRepository

    @Autowired
    private lateinit var promotionEligibleProductRepository: PromotionEligibleProductRepository

    @Autowired
    private lateinit var promotionRewardProductRepository: PromotionRewardProductRepository

    @Autowired
    private lateinit var businessDayService: BusinessDayService

    @Autowired
    private lateinit var taxInvoiceService: TaxInvoiceService

    @Test
    fun `PHASE 35 E2E Scenario - Burger x6 with Buy 1 Burger Get 1 Coke promo, Historical Recipe Versioning, and EOD stock consumption`() {
        val branchId = "branch-e2e-master"
        val warehouseId = "wh-kitchen-master"

        // 1. Setup Business Day and Warehouse
        val bday = businessDayService.getOrCreateOpenBusinessDay(branchId)
        val whRepo = applicationContext.getBean(WarehouseRepository::class.java)
        whRepo.save(Warehouse(id = warehouseId, branchId = branchId, name = "Master Kitchen WH", code = "WH-KITCHEN"))

        // 2. Setup Ingredients: Beef, Bun, Coke
        val beef = inventoryService.createInventoryItem(InventoryItem(sku = "RAW-BEEF", name = "Beef Patty", unit = "g", baseUnit = "g"))
        val bun = inventoryService.createInventoryItem(InventoryItem(sku = "RAW-BUN", name = "Burger Bun", unit = "piece", baseUnit = "piece"))
        val cokeCan = inventoryService.createInventoryItem(InventoryItem(sku = "RAW-COKE", name = "Coke Can", unit = "piece", baseUnit = "piece"))

        // Receive Initial Stocks: Beef = 2000g, Bun = 20 pieces, Coke = 20 pieces
        inventoryService.processPurchaseReceive(PurchaseReceiveDto(warehouseId = warehouseId, inventoryItemId = beef.id, quantity = BigDecimal("2000.0000"), unit = "g", unitCost = BigDecimal("0.5000")))
        inventoryService.processPurchaseReceive(PurchaseReceiveDto(warehouseId = warehouseId, inventoryItemId = bun.id, quantity = BigDecimal("20.0000"), unit = "piece", unitCost = BigDecimal("5.0000")))
        inventoryService.processPurchaseReceive(PurchaseReceiveDto(warehouseId = warehouseId, inventoryItemId = cokeCan.id, quantity = BigDecimal("20.0000"), unit = "piece", unitCost = BigDecimal("12.0000")))

        // 3. Setup Catalog: Burger & Coke Menu Items
        val cat = catalogService.createCategory(MenuCategory(branchId = branchId, name = "Main Meals"))
        val burgerItem = catalogService.createMenuItem(MenuItemCreateDto(branchId = branchId, categoryId = cat.id, name = "Classic Burger", basePrice = BigDecimal("150.0000")))
        val cokeItem = catalogService.createMenuItem(MenuItemCreateDto(branchId = branchId, categoryId = cat.id, name = "Coca Cola", basePrice = BigDecimal("30.0000")))

        // 4. Setup Recipes
        // Burger Recipe V1: Beef = 120g, Bun = 1 piece
        val burgerRecipe = recipeService.createRecipe(
            CreateRecipeDto(
                menuItemId = burgerItem.id,
                name = "Classic Burger Recipe v1.0",
                version = "v1.0",
                ingredients = listOf(
                    RecipeIngredientDto(inventoryItemId = beef.id, quantity = BigDecimal("120.0000"), unit = "g"),
                    RecipeIngredientDto(inventoryItemId = bun.id, quantity = BigDecimal("1.0000"), unit = "piece")
                )
            )
        )

        // Coke Recipe: Coke = 1 piece
        recipeService.createRecipe(
            CreateRecipeDto(
                menuItemId = cokeItem.id,
                name = "Coke Portion Recipe",
                version = "v1.0",
                ingredients = listOf(
                    RecipeIngredientDto(inventoryItemId = cokeCan.id, quantity = BigDecimal("1.0000"), unit = "piece")
                )
            )
        )

        // 5. Setup Promotion: BUY 1 Burger GET 1 Coke Free (BUY_1_GET_N)
        val promo = promotionRepository.save(
            Promotion(
                code = "BURGER-GET-COKE",
                name = "Buy Burger Get Free Coke",
                promoType = PromotionType.BUY_1_GET_N,
                priority = 10,
                isActive = true,
                startAt = Instant.now().minus(1, ChronoUnit.DAYS),
                endAt = Instant.now().plus(10, ChronoUnit.DAYS),
                branchId = branchId,
                minQuantity = BigDecimal.ONE,
                stackingPolicy = StackingPolicy.STACKABLE
            )
        )
        promotionEligibleProductRepository.save(PromotionEligibleProduct(promotionId = promo.id, menuItemId = burgerItem.id))
        promotionRewardProductRepository.save(PromotionRewardProduct(promotionId = promo.id, menuItemId = cokeItem.id, quantity = BigDecimal.ONE))

        // 6. Execute Sales Orders:
        // Order 001: Burger x2
        val order1 = orderService.createOrder(
            CreateOrderRequest(
                branchId = branchId,
                items = listOf(OrderItemRequest(menuItemId = burgerItem.id, nameSnapshot = "Classic Burger", unitPriceSnapshot = BigDecimal("150.00"), quantity = BigDecimal("2.0")))
            )
        )
        promotionService.applyPromotionsToOrder(order1.id, "POS", null)
        paymentService.processPayment(PaymentRequestDto(orderId = order1.id, branchId = branchId, paymentMethod = PaymentMethod.CASH, amount = order1.totalAmount))

        // Order 002: Burger x3
        val order2 = orderService.createOrder(
            CreateOrderRequest(
                branchId = branchId,
                items = listOf(OrderItemRequest(menuItemId = burgerItem.id, nameSnapshot = "Classic Burger", unitPriceSnapshot = BigDecimal("150.00"), quantity = BigDecimal("3.0")))
            )
        )
        promotionService.applyPromotionsToOrder(order2.id, "POS", null)
        paymentService.processPayment(PaymentRequestDto(orderId = order2.id, branchId = branchId, paymentMethod = PaymentMethod.CARD, amount = order2.totalAmount))

        // Order 003: Burger x1 + Coke x2 (Sold directly)
        val order3 = orderService.createOrder(
            CreateOrderRequest(
                branchId = branchId,
                items = listOf(
                    OrderItemRequest(menuItemId = burgerItem.id, nameSnapshot = "Classic Burger", unitPriceSnapshot = BigDecimal("150.00"), quantity = BigDecimal("1.0")),
                    OrderItemRequest(menuItemId = cokeItem.id, nameSnapshot = "Coca Cola", unitPriceSnapshot = BigDecimal("30.00"), quantity = BigDecimal("2.0"))
                )
            )
        )
        promotionService.applyPromotionsToOrder(order3.id, "POS", null)
        paymentService.processPayment(PaymentRequestDto(orderId = order3.id, branchId = branchId, paymentMethod = PaymentMethod.PROMPTPAY, amount = order3.totalAmount))

        // 7. Verify Inventory stock is NOT deducted during daytime sales
        val stockBeefMidDay = inventoryService.getStockOnHand(warehouseId).first { it.inventoryItemId == beef.id }
        assertEquals(BigDecimal("2000.0000"), stockBeefMidDay.quantity)

        // 8. Change Burger Recipe to V2 (Beef = 150g) to verify Historical Recipe Snapshotting
        recipeService.createRecipe(
            CreateRecipeDto(
                menuItemId = burgerItem.id,
                name = "Classic Burger Recipe v2.0",
                version = "v2.0",
                ingredients = listOf(
                    RecipeIngredientDto(inventoryItemId = beef.id, quantity = BigDecimal("150.0000"), unit = "g"),
                    RecipeIngredientDto(inventoryItemId = bun.id, quantity = BigDecimal("1.0000"), unit = "piece")
                )
            )
        )

        // 9. Close Business Day EOD
        val closedDay = businessDayService.closeBusinessDayEod(branchId, "manager-001")
        assertEquals(BusinessDayStatus.CLOSED, closedDay.status)

        // 10. Verify Stock Deduction Quantities:
        // Total Burger Sold = 2 + 3 + 1 = 6 Burgers
        // Using Recipe V1 (120g Beef): Expected Beef consumption = 6 * 120g = 720g -> Remaining: 2000 - 720 = 1280g
        val stockBeefPostEod = inventoryService.getStockOnHand(warehouseId).first { it.inventoryItemId == beef.id }
        assertEquals(BigDecimal("1280.0000"), stockBeefPostEod.quantity)

        // Bun consumption = 6 pieces -> Remaining: 20 - 6 = 14 pieces
        val stockBunPostEod = inventoryService.getStockOnHand(warehouseId).first { it.inventoryItemId == bun.id }
        assertEquals(BigDecimal("14.0000"), stockBunPostEod.quantity)

        // Coke consumption = 2 (sold in order 3) + 6 (free promo reward from 6 burgers) = 8 Cokes -> Remaining: 20 - 8 = 12 pieces
        val stockCokePostEod = inventoryService.getStockOnHand(warehouseId).first { it.inventoryItemId == cokeCan.id }
        assertEquals(BigDecimal("12.0000"), stockCokePostEod.quantity)

        // 11. Test Idempotency: Duplicate Close EOD does not deduct stock again
        businessDayService.closeBusinessDayEod(branchId, "manager-001")
        val stockBeefPostRetry = inventoryService.getStockOnHand(warehouseId).first { it.inventoryItemId == beef.id }
        assertEquals(BigDecimal("1280.0000"), stockBeefPostRetry.quantity)
    }

    @Test
    fun `PHASE 36 & 37 Critical Tax and Merge Promotion Scenario`() {
        val branchId = "branch-tax-critical"
        businessDayService.getOrCreateOpenBusinessDay(branchId)

        val cat = catalogService.createCategory(MenuCategory(branchId = branchId, name = "Dining"))
        val itemA = catalogService.createMenuItem(MenuItemCreateDto(branchId = branchId, categoryId = cat.id, name = "Dinner A", basePrice = BigDecimal("1070.0000")))
        val itemB = catalogService.createMenuItem(MenuItemCreateDto(branchId = branchId, categoryId = cat.id, name = "Dinner B", basePrice = BigDecimal("535.0000")))

        // Receipt A: Gross = 1,070 (inclusive VAT)
        val orderA = orderService.createOrder(
            CreateOrderRequest(branchId = branchId, items = listOf(OrderItemRequest(itemA.id, "Dinner A", BigDecimal("1070.0000"), BigDecimal.ONE)))
        )
        paymentService.processPayment(PaymentRequestDto(orderId = orderA.id, branchId = branchId, paymentMethod = PaymentMethod.CASH, amount = BigDecimal("1070.0000")))

        // Receipt B: Gross = 535 (inclusive VAT)
        val orderB = orderService.createOrder(
            CreateOrderRequest(branchId = branchId, items = listOf(OrderItemRequest(itemB.id, "Dinner B", BigDecimal("535.0000"), BigDecimal.ONE)))
        )
        paymentService.processPayment(PaymentRequestDto(orderId = orderB.id, branchId = branchId, paymentMethod = PaymentMethod.CASH, amount = BigDecimal("535.0000")))

        // Merge into single Tax Invoice (Total Gross = 1,605)
        val taxInvoice = taxInvoiceService.mergeReceiptsToTaxInvoice(
            CreateTaxInvoiceDto(
                branchId = branchId,
                customerId = null,
                taxpayerName = "Siam Gourmet Corp",
                taxId = "0105558888999",
                address = "99 Ploenchit Rd, Bangkok",
                email = "tax@siamgourmet.co.th",
                phone = "026543210",
                orderIds = listOf(orderA.id, orderB.id),
                createdBy = "cashier-001"
            )
        )

        // Taxable Net Amount = 1,500.00, Tax Amount = 105.00 (Total = 1605.00)
        assertEquals(BigDecimal("1500.0000"), taxInvoice.totalNetAmount)
        assertEquals(BigDecimal("105.0000"), taxInvoice.totalTaxAmount)
        assertTrue(taxInvoice.taxInvoiceNumber.startsWith("TI-"))
    }
}
