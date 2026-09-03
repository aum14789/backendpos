package com.sunpos.backend.domain.promotion

import com.sunpos.backend.domain.businessday.BusinessDayService
import com.sunpos.backend.domain.catalog.*
import com.sunpos.backend.domain.order.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PromotionAndCatalogEnhancementTest {

    @Autowired
    private lateinit var catalogService: CatalogService

    @Autowired
    private lateinit var orderService: OrderService

    @Autowired
    private lateinit var businessDayService: BusinessDayService

    @Autowired
    private lateinit var promotionService: PromotionService

    @Autowired
    private lateinit var promotionRepository: PromotionRepository

    @Autowired
    private lateinit var promotionEligibleProductRepository: PromotionEligibleProductRepository

    @Autowired
    private lateinit var couponRepository: CouponRepository

    @Test
    fun `test Modifier min and max selection bounds validation`() {
        val cat = catalogService.createCategory(MenuCategory(branchId = "branch-promo", name = "Test Category"))
        
        // Group with min=1, max=1 (Required Choice)
        val modGroupReq = catalogService.createModifierGroup(
            ModifierGroup(branchId = "branch-promo", name = "Spiciness", minSelection = 1, maxSelection = 1, isRequired = true)
        )
        val modMild = catalogService.createModifier(Modifier(modifierGroupId = modGroupReq.id, name = "Mild", price = BigDecimal.ZERO))
        val modSpicy = catalogService.createModifier(Modifier(modifierGroupId = modGroupReq.id, name = "Spicy", price = BigDecimal.ZERO))

        // Group with min=0, max=1 (Optional Choice)
        val modGroupOpt = catalogService.createModifierGroup(
            ModifierGroup(branchId = "branch-promo", name = "Egg", minSelection = 0, maxSelection = 1, isRequired = false)
        )
        val modEgg1 = catalogService.createModifier(Modifier(modifierGroupId = modGroupOpt.id, name = "Fried Egg", price = BigDecimal("15.00")))
        val modEgg2 = catalogService.createModifier(Modifier(modifierGroupId = modGroupOpt.id, name = "Omelette", price = BigDecimal("20.00")))

        val item = catalogService.createMenuItem(
            MenuItemCreateDto(
                branchId = "branch-promo",
                categoryId = cat.id,
                name = "Stir Fry",
                basePrice = BigDecimal("100.00"),
                modifierGroupIds = listOf(modGroupReq.id, modGroupOpt.id)
            )
        )

        businessDayService.getOrCreateOpenBusinessDay("branch-promo")

        val order = orderService.createOrder(
            CreateOrderRequest(
                branchId = "branch-promo",
                orderType = OrderType.DINE_IN,
                channel = OrderChannel.POS,
                items = emptyList()
            )
        )

        // 1. Missing Required Modifier Choice (minSelection = 1 not met)
        val missingReq = OrderItemRequest(
            menuItemId = item.id,
            nameSnapshot = item.name,
            unitPriceSnapshot = item.basePrice,
            quantity = BigDecimal.ONE,
            modifiers = emptyList()
        )
        val exMin = assertThrows<IllegalArgumentException> {
            orderService.addItemToOrder(order.id, missingReq)
        }
        assertTrue(exMin.message!!.contains("requires at least 1 selections"))

        // 2. Exceeding Max Selections (maxSelection = 1 exceeded with 2 choices)
        val exceedingReq = OrderItemRequest(
            menuItemId = item.id,
            nameSnapshot = item.name,
            unitPriceSnapshot = item.basePrice,
            quantity = BigDecimal.ONE,
            modifiers = listOf(
                OrderItemModifierRequest(modMild.id, "Mild", BigDecimal.ZERO),
                OrderItemModifierRequest(modEgg1.id, "Fried Egg", BigDecimal("15.00")),
                OrderItemModifierRequest(modEgg2.id, "Omelette", BigDecimal("20.00"))
            )
        )
        val exMax = assertThrows<IllegalArgumentException> {
            orderService.addItemToOrder(order.id, exceedingReq)
        }
        assertTrue(exMax.message!!.contains("allows at most 1 selections"))

        // 3. Valid Modifier Selection
        val validReq = OrderItemRequest(
            menuItemId = item.id,
            nameSnapshot = item.name,
            unitPriceSnapshot = item.basePrice,
            quantity = BigDecimal.ONE,
            modifiers = listOf(
                OrderItemModifierRequest(modMild.id, "Mild", BigDecimal.ZERO),
                OrderItemModifierRequest(modEgg1.id, "Fried Egg", BigDecimal("15.00"))
            )
        )
        val itemRes = orderService.addItemToOrder(order.id, validReq)
        assertNotNull(itemRes.id)
        assertEquals(BigDecimal("115.0000"), itemRes.subtotal)
    }

    @Test
    fun `test Promotion Engine Buy 1 Get 1 and percentage discount evaluation`() {
        val cat = catalogService.createCategory(MenuCategory(branchId = "branch-promo-2", name = "Fast Food"))
        val burger = catalogService.createMenuItem(
            MenuItemCreateDto(
                branchId = "branch-promo-2",
                categoryId = cat.id,
                name = "Cheeseburger",
                basePrice = BigDecimal("150.00")
            )
        )
        val drink = catalogService.createMenuItem(
            MenuItemCreateDto(
                branchId = "branch-promo-2",
                categoryId = cat.id,
                name = "Cola",
                basePrice = BigDecimal("50.00")
            )
        )

        businessDayService.getOrCreateOpenBusinessDay("branch-promo-2")

        // 1. Setup Buy 1 Get 1 Promo for Burger
        val bogoPromo = promotionRepository.save(
            Promotion(
                code = "BOGO-BURGER",
                name = "Buy 1 Burger Get 1 Free",
                promoType = PromotionType.BUY_1_GET_1,
                priority = 10,
                isActive = true,
                startAt = Instant.now().minus(1, ChronoUnit.DAYS),
                endAt = Instant.now().plus(10, ChronoUnit.DAYS),
                branchId = "branch-promo-2",
                minQuantity = BigDecimal("2.0"),
                stackingPolicy = StackingPolicy.STACKABLE
            )
        )
        promotionEligibleProductRepository.save(
            PromotionEligibleProduct(promotionId = bogoPromo.id, menuItemId = burger.id)
        )

        // 2. Setup 10% Off on total bill promo
        val percentPromo = promotionRepository.save(
            Promotion(
                code = "DISCOUNT10",
                name = "10 Percent Off",
                promoType = PromotionType.PERCENTAGE,
                priority = 5,
                isActive = true,
                startAt = Instant.now().minus(1, ChronoUnit.DAYS),
                endAt = Instant.now().plus(10, ChronoUnit.DAYS),
                branchId = "branch-promo-2",
                discountRate = BigDecimal("10.00"),
                stackingPolicy = StackingPolicy.STACKABLE
            )
        )

        // Create Order with 2 Burgers + 1 Cola (Total: 150*2 + 50 = 350)
        val order = orderService.createOrder(
            CreateOrderRequest(
                branchId = "branch-promo-2",
                orderType = OrderType.DINE_IN,
                channel = OrderChannel.POS,
                items = listOf(
                    OrderItemRequest(burger.id, "Cheeseburger", BigDecimal("150.00"), BigDecimal("2.0")),
                    OrderItemRequest(drink.id, "Cola", BigDecimal("50.00"), BigDecimal("1.0"))
                )
            )
        )
        assertEquals(BigDecimal("350.0000"), order.totalAmount)

        // Apply Promotions:
        // Expected: BOGO discounts 1 Burger (-150.00), Percent Promo discounts 10% of total (-35.00)
        // Total discount: 150 + 35 = 185.00
        val totalDiscount = promotionService.applyPromotionsToOrder(order.id, "POS", null)
        assertEquals(BigDecimal("185.0000"), totalDiscount)

        val updatedOrder = orderService.getOrderDetails(order.id)
        assertEquals(BigDecimal("165.0000"), updatedOrder.totalAmount)
    }

    @Test
    fun `test Single-use Coupon Redemption Ledger Tracking`() {
        val cat = catalogService.createCategory(MenuCategory(branchId = "branch-promo-3", name = "Beverage Cat"))
        val coffee = catalogService.createMenuItem(
            MenuItemCreateDto(
                branchId = "branch-promo-3",
                categoryId = cat.id,
                name = "Iced Latte",
                basePrice = BigDecimal("100.00")
            )
        )

        businessDayService.getOrCreateOpenBusinessDay("branch-promo-3")

        val fixedPromo = promotionRepository.save(
            Promotion(
                code = "PROMO-COUPON-50",
                name = "50 THB Off Coupon",
                promoType = PromotionType.FIXED_AMOUNT,
                priority = 10,
                isActive = true,
                startAt = Instant.now().minus(1, ChronoUnit.DAYS),
                endAt = Instant.now().plus(10, ChronoUnit.DAYS),
                branchId = "branch-promo-3",
                discountAmount = BigDecimal("50.00"),
                stackingPolicy = StackingPolicy.NON_STACKABLE
            )
        )

        val coupon = couponRepository.save(
            Coupon(
                promotionId = fixedPromo.id,
                code = "SAVE50NOW",
                isUsed = false,
                maxUses = 1,
                currentUses = 0,
                expiresAt = Instant.now().plus(5, ChronoUnit.DAYS)
            )
        )

        val order = orderService.createOrder(
            CreateOrderRequest(
                branchId = "branch-promo-3",
                orderType = OrderType.DINE_IN,
                channel = OrderChannel.POS,
                items = listOf(
                    OrderItemRequest(coffee.id, "Iced Latte", BigDecimal("100.00"), BigDecimal("1.0"))
                )
            )
        )

        // 1. Redeem Coupon Successfully
        val discount = promotionService.applyPromotionsToOrder(order.id, "POS", "cust-001", "SAVE50NOW")
        assertEquals(BigDecimal("50.0000"), discount)

        val refreshedCoupon = couponRepository.findById(coupon.id).get()
        assertTrue(refreshedCoupon.isUsed)
        assertEquals(1, refreshedCoupon.currentUses)

        // 2. Try redeeming same coupon again on second order -> Expected Failure
        val order2 = orderService.createOrder(
            CreateOrderRequest(
                branchId = "branch-promo-3",
                orderType = OrderType.DINE_IN,
                channel = OrderChannel.POS,
                items = listOf(
                    OrderItemRequest(coffee.id, "Iced Latte", BigDecimal("100.00"), BigDecimal("1.0"))
                )
            )
        )
        assertThrows<IllegalArgumentException> {
            promotionService.applyPromotionsToOrder(order2.id, "POS", "cust-001", "SAVE50NOW")
        }
    }
}
