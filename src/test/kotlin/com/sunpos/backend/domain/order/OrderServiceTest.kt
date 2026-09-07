package com.sunpos.backend.domain.order

import com.sunpos.backend.domain.businessday.BusinessDay
import com.sunpos.backend.domain.businessday.BusinessDayRepository
import com.sunpos.backend.domain.businessday.BusinessDayStatus
import com.sunpos.backend.domain.catalog.*
import com.sunpos.backend.domain.crm.CreateCustomerDto
import com.sunpos.backend.domain.crm.CrmService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDate

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrderServiceTest {

    @Autowired
    private lateinit var orderService: OrderService

    @Autowired
    private lateinit var crmService: CrmService

    @Autowired
    private lateinit var catalogService: CatalogService

    @Autowired
    private lateinit var categoryRepository: MenuCategoryRepository

    @Autowired
    private lateinit var modifierGroupRepository: ModifierGroupRepository

    @Autowired
    private lateinit var modifierRepository: ModifierRepository

    @Autowired
    private lateinit var businessDayRepository: BusinessDayRepository

    private lateinit var branchId: String
    private lateinit var categoryId: String
    private lateinit var menuItemId: String
    private lateinit var modifierId: String

    @BeforeEach
    fun setUp() {
        branchId = "branch-001"

        // Open active business day for test branch
        businessDayRepository.save(
            BusinessDay(
                branchId = branchId,
                businessDate = LocalDate.now().toString(),
                status = BusinessDayStatus.OPEN
            )
        )

        val category = categoryRepository.save(
            MenuCategory(branchId = branchId, name = "A La Carte", sortOrder = 1)
        )
        categoryId = category.id

        val mg = modifierGroupRepository.save(
            ModifierGroup(branchId = branchId, name = "Spiciness", minSelection = 0, maxSelection = 1, isRequired = false)
        )
        val mod = modifierRepository.save(
            Modifier(modifierGroupId = mg.id, name = "Extra Spicy", price = BigDecimal("15.0000"))
        )
        modifierId = mod.id

        val menuItemDto = MenuItemCreateDto(
            branchId = branchId,
            categoryId = categoryId,
            name = "Pad Kra Pao",
            basePrice = BigDecimal("120.0000"),
            modifierGroupIds = listOf(mg.id)
        )
        val menuItem = catalogService.createMenuItem(menuItemDto)
        menuItemId = menuItem.id
    }

    @Test
    fun `test create order, snapshot pricing, and state machine transitions`() {
        val itemReq = OrderItemRequest(
            menuItemId = menuItemId,
            nameSnapshot = "Pad Kra Pao",
            unitPriceSnapshot = BigDecimal("120.0000"),
            quantity = BigDecimal("2"),
            modifiers = listOf(OrderItemModifierRequest(modifierId, "Extra Spicy", BigDecimal("15.0000")))
        )

        val createReq = CreateOrderRequest(
            branchId = branchId,
            orderType = OrderType.DINE_IN,
            channel = OrderChannel.POS,
            items = listOf(itemReq)
        )

        val order = orderService.createOrder(createReq)
        assertNotNull(order.id)
        assertEquals(OrderStatus.OPEN, order.status)
        assertEquals(KitchenStatus.NOT_SENT, order.kitchenStatus)

        // Subtotal = (120 + 15) * 2 = 270.00
        assertEquals(BigDecimal("270.0000"), order.totalAmount)
        assertEquals(1, order.items.size)
        assertEquals("Pad Kra Pao", order.items[0].nameSnapshot)

        // Send to kitchen -> Status becomes CONFIRMED, KitchenStatus becomes SENT
        val kitchenOrder = orderService.sendToKitchen(order.id)
        assertEquals(OrderStatus.CONFIRMED, kitchenOrder.status)
        assertEquals(KitchenStatus.SENT, kitchenOrder.kitchenStatus)

        // Transition to IN_KITCHEN
        val inKitchenOrder = orderService.transitionOrderStatus(order.id, OrderStatus.IN_KITCHEN)
        assertEquals(OrderStatus.IN_KITCHEN, inKitchenOrder.status)

        // Invalid transition: IN_KITCHEN directly to COMPLETED should fail
        assertThrows(IllegalArgumentException::class.java) {
            orderService.transitionOrderStatus(order.id, OrderStatus.COMPLETED)
        }
    }

    @Test
    fun `test manual cashier and manager discount application and audit trail`() {
        val itemReq = OrderItemRequest(
            menuItemId = menuItemId,
            nameSnapshot = "Pad Kra Pao",
            unitPriceSnapshot = BigDecimal("120.0000"),
            quantity = BigDecimal("2")
        )

        val createReq = CreateOrderRequest(
            branchId = branchId,
            orderType = OrderType.DINE_IN,
            channel = OrderChannel.POS,
            items = listOf(itemReq)
        )

        val order = orderService.createOrder(createReq)
        assertEquals(BigDecimal("240.0000"), order.totalAmount)

        // Apply Manual Discount of ฿40.00
        val discountReq = ApplyManualDiscountRequest(
            discountAmount = BigDecimal("40.0000"),
            reason = "Manager special customer loyalty",
            authorizedBy = "admin"
        )
        val discountedOrder = orderService.applyManualDiscount(order.id, discountReq)

        assertEquals(BigDecimal("40.0000"), discountedOrder.discountAmount)
        assertEquals(BigDecimal("200.0000"), discountedOrder.totalAmount) // 240 - 40 = ฿200.00
        assertEquals("Manager special customer loyalty", discountedOrder.manualDiscountReason)
        assertEquals("admin", discountedOrder.manualDiscountAuthorizedBy)

        // VAT 7% inclusive on ฿200.00 = 200 - (200 / 1.07) = 13.0841
        assertEquals(BigDecimal("13.0841"), discountedOrder.taxAmount)
    }

    @Test
    fun `test link customer to OPEN and COMPLETED order with role security and points transfer`() {
        val cust1 = crmService.createCustomer(
            CreateCustomerDto(displayName = "Customer A", phone = "081-111-9999")
        )
        val cust2 = crmService.createCustomer(
            CreateCustomerDto(displayName = "Customer B", phone = "089-999-6666")
        )

        val createReq = CreateOrderRequest(
            branchId = branchId,
            orderType = OrderType.DINE_IN,
            channel = OrderChannel.POS,
            customerId = cust1.customer.id,
            items = listOf(
                OrderItemRequest(
                    menuItemId = menuItemId,
                    nameSnapshot = "Pad Kra Pao",
                    unitPriceSnapshot = BigDecimal("120.0000"),
                    quantity = BigDecimal("2")
                )
            )
        )

        val order = orderService.createOrder(createReq)
        assertEquals(cust1.customer.id, order.customerId)

        // 1. Link new customer when order is OPEN (Cashier can do this)
        val updatedOpen = orderService.linkCustomer(order.id, LinkCustomerRequestDto(cust2.customer.id), username = "cashier")
        assertEquals(cust2.customer.id, updatedOpen.customerId)

        // 2. Complete order -> Cust2 earns points
        orderService.transitionOrderStatus(order.id, OrderStatus.COMPLETED)
        val cust2BalAfterComplete = crmService.calculatePointsBalance(cust2.customer.id)
        assertTrue(cust2BalAfterComplete > BigDecimal.ZERO)

        // 3. Changing customer on COMPLETED order by Non-Manager should FAIL
        assertThrows(IllegalArgumentException::class.java) {
            orderService.linkCustomer(order.id, LinkCustomerRequestDto(cust1.customer.id), username = "cashier_unknown_role")
        }

        // 4. Changing customer on COMPLETED order by Super Admin / Manager should SUCCEED and reallocate points
        val reallocated = orderService.linkCustomer(order.id, LinkCustomerRequestDto(cust1.customer.id), username = "admin")
        assertEquals(cust1.customer.id, reallocated.customerId)

        val cust1Bal = crmService.calculatePointsBalance(cust1.customer.id)
        val cust2Bal = crmService.calculatePointsBalance(cust2.customer.id)
        assertTrue(cust1Bal > BigDecimal.ZERO, "Cust 1 should have earned points after reassignment")
        assertEquals(BigDecimal.ZERO.setScale(4), cust2Bal, "Cust 2 points should have been reversed")
    }

    @Test
    fun `test member tier discount applied on order and tier upgraded automatically upon order completion`() {
        val testCompanyId = "comp-001"

        // 1. Create Customer -> initially SILVER (0% discount, 0 spent)
        val cust = crmService.createCustomer(
            com.sunpos.backend.domain.crm.CreateCustomerDto(displayName = "เสี่ยสมหวัง สายเปย์", phone = "086-777-9900"),
            companyId = testCompanyId
        )

        // Upgrade Customer to GOLD (5% discount, Min spent 5,000)
        crmService.evaluateAndUpgradeMembership(cust.customer.id, addedSpend = BigDecimal("6000.0000"))
        val membershipBefore = crmService.getCustomerMembership(cust.customer.id, testCompanyId)
        assertEquals("GOLD", membershipBefore.tierCode)
        assertEquals(BigDecimal("5.00"), membershipBefore.discountPercentage)

        // 2. Create Order with 2 items = 2 * 120 = 240 THB gross, linking customer
        val order = orderService.createOrder(
            CreateOrderRequest(
                branchId = branchId,
                customerId = cust.customer.id,
                orderType = OrderType.DINE_IN,
                channel = OrderChannel.POS,
                items = listOf(
                    OrderItemRequest(
                        menuItemId = menuItemId,
                        nameSnapshot = "Pad Kra Pao",
                        unitPriceSnapshot = BigDecimal("120.0000"),
                        quantity = BigDecimal("2")
                    )
                )
            )
        )

        // Expected Pricing Pipeline:
        // Gross = 240.0000
        // Member Tier Discount (Gold 5%) = 5% of 240 = 12.0000
        // Total Discount = 12.0000
        // Grand Total = 228.0000 (Inclusive VAT 7% = 14.9159)
        assertEquals(BigDecimal("240.0000"), order.subtotalAmount)
        assertEquals(BigDecimal("12.0000"), order.discountAmount)
        assertEquals(BigDecimal("228.0000"), order.totalAmount)

        // 3. Complete order -> Cumulative spend becomes 6,000 + 228 = 6,228 THB
        orderService.transitionOrderStatus(order.id, OrderStatus.COMPLETED)
        val membershipAfter = crmService.getCustomerMembership(cust.customer.id, testCompanyId)
        assertEquals(BigDecimal("6228.0000"), membershipAfter.currentSpent)
        assertEquals("GOLD", membershipAfter.tierCode)

        // 4. Create another large order for 14,000 THB (Gross 14,000 - 5% = 13,300)
        val order2 = orderService.createOrder(
            CreateOrderRequest(
                branchId = branchId,
                customerId = cust.customer.id,
                orderType = OrderType.DINE_IN,
                channel = OrderChannel.POS,
                items = listOf(
                    OrderItemRequest(
                        menuItemId = menuItemId,
                        nameSnapshot = "Special Seafood Banquet",
                        unitPriceSnapshot = BigDecimal("14000.0000"),
                        quantity = BigDecimal("1")
                    )
                )
            )
        )
        assertEquals(BigDecimal("14000.0000"), order2.subtotalAmount)
        assertEquals(BigDecimal("700.0000"), order2.discountAmount)
        assertEquals(BigDecimal("13300.0000"), order2.totalAmount)

        // Complete order2 -> Cumulative spend becomes 6,228 + 13,300 = 19,528 THB (< 20,000 THB) -> Still GOLD
        orderService.transitionOrderStatus(order2.id, OrderStatus.COMPLETED)
        val membershipAfterOrder2 = crmService.getCustomerMembership(cust.customer.id, testCompanyId)
        assertEquals("GOLD", membershipAfterOrder2.tierCode)
        assertEquals(BigDecimal("19528.0000"), membershipAfterOrder2.currentSpent)

        // 5. Complete one more order for 600 THB -> Cumulative spend exceeds 20,000 THB (20,098 THB) -> PLATINUM!
        val order3 = orderService.createOrder(
            CreateOrderRequest(
                branchId = branchId,
                customerId = cust.customer.id,
                orderType = OrderType.DINE_IN,
                channel = OrderChannel.POS,
                items = listOf(
                    OrderItemRequest(
                        menuItemId = menuItemId,
                        nameSnapshot = "Premium Wine",
                        unitPriceSnapshot = BigDecimal("600.0000"),
                        quantity = BigDecimal("1")
                    )
                )
            )
        )
        orderService.transitionOrderStatus(order3.id, OrderStatus.COMPLETED)
        val membershipPlatinum = crmService.getCustomerMembership(cust.customer.id, testCompanyId)
        assertEquals("PLATINUM", membershipPlatinum.tierCode)
        assertEquals(BigDecimal("20098.0000"), membershipPlatinum.currentSpent)
        assertEquals(BigDecimal("10.00"), membershipPlatinum.discountPercentage)
    }
}
