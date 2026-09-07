package com.sunpos.backend.domain.payment

import com.sunpos.backend.domain.order.*
import com.sunpos.backend.domain.catalog.*
import com.sunpos.backend.domain.businessday.BusinessDayService
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
class PaymentServiceTest {

    @Autowired
    private lateinit var orderService: OrderService

    @Autowired
    private lateinit var catalogService: CatalogService

    @Autowired
    private lateinit var businessDayService: BusinessDayService

    @Autowired
    private lateinit var paymentService: PaymentService

    @Test
    fun `test multi-payment, change calculation, and order completion`() {
        val category = catalogService.createCategory(MenuCategory(branchId = "branch-001", name = "Food"))
        val menuItem = catalogService.createMenuItem(
            MenuItemCreateDto(branchId = "branch-001", categoryId = category.id, name = "Pad Kra Pao", basePrice = BigDecimal("250.00"))
        )

        // Open Business Day
        businessDayService.getOrCreateOpenBusinessDay("branch-001")

        val itemReq = OrderItemRequest(
            menuItemId = menuItem.id,
            nameSnapshot = "Pad Kra Pao",
            unitPriceSnapshot = BigDecimal("250.00"),
            quantity = BigDecimal("4.0")
        )
        val order = orderService.createOrder(
            CreateOrderRequest(branchId = "branch-001", items = listOf(itemReq))
        )
        // Total = (250) * 4 = 1,000.00
        assertEquals(BigDecimal("1000.0000"), order.totalAmount)

        // Payment 1: Cash 200.00
        val p1 = paymentService.processPayment(
            PaymentRequestDto(
                orderId = order.id,
                branchId = "branch-001",
                paymentMethod = PaymentMethod.CASH,
                amount = BigDecimal("200.00"),
                tenderedAmount = BigDecimal("500.00")
            )
        )
        assertEquals(PaymentStatus.SUCCESS, p1.status)
        assertEquals(BigDecimal("300.0000"), p1.changeAmount)

        // Order still OPEN
        val o1 = orderService.getOrderDetails(order.id)
        assertEquals(OrderStatus.OPEN, o1.status)

        // Payment 2: QR 300.00
        paymentService.processPayment(
            PaymentRequestDto(
                orderId = order.id,
                branchId = "branch-001",
                paymentMethod = PaymentMethod.QR,
                amount = BigDecimal("300.00")
            )
        )

        // Payment 3: Card 500.00
        paymentService.processPayment(
            PaymentRequestDto(
                orderId = order.id,
                branchId = "branch-001",
                paymentMethod = PaymentMethod.CARD,
                amount = BigDecimal("500.00")
            )
        )

        // Total payments = 200 + 300 + 500 = 1,000. Order should now be COMPLETED
        val completedOrder = orderService.getOrderDetails(order.id)
        assertEquals(OrderStatus.COMPLETED, completedOrder.status)
    }

    @Test
    fun `test partial refund reversal transaction`() {
        val category = catalogService.createCategory(MenuCategory(branchId = "branch-001", name = "Food"))
        val menuItem = catalogService.createMenuItem(
            MenuItemCreateDto(branchId = "branch-001", categoryId = category.id, name = "Tom Yum", basePrice = BigDecimal("500.00"))
        )

        // Open Business Day
        businessDayService.getOrCreateOpenBusinessDay("branch-001")

        val itemReq = OrderItemRequest(
            menuItemId = menuItem.id,
            nameSnapshot = "Tom Yum",
            unitPriceSnapshot = BigDecimal("500.00"),
            quantity = BigDecimal("1.0")
        )
        val order = orderService.createOrder(CreateOrderRequest(branchId = "branch-001", items = listOf(itemReq)))

        val payment = paymentService.processPayment(
            PaymentRequestDto(orderId = order.id, branchId = "branch-001", paymentMethod = PaymentMethod.CARD, amount = BigDecimal("500.00"))
        )

        // Partial Refund 200.00
        val refund = paymentService.refundPayment(
            payment.id,
            RefundRequestDto(paymentTransactionId = payment.id, amount = BigDecimal("200.00"), reason = "Customer request")
        )
        assertNotNull(refund.id)
        assertEquals(BigDecimal("200.0000"), refund.amount)

        val payments = paymentService.getPaymentsByOrder(order.id)
        assertEquals(PaymentStatus.PARTIALLY_REFUNDED, payments[0].status)
    }
}
