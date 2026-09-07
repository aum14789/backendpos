package com.sunpos.backend.domain.payment

import com.sunpos.backend.domain.businessday.BusinessDayService
import com.sunpos.backend.domain.catalog.CatalogService
import com.sunpos.backend.domain.catalog.MenuCategory
import com.sunpos.backend.domain.catalog.MenuItemCreateDto
import com.sunpos.backend.domain.crm.CreateCustomerDto
import com.sunpos.backend.domain.crm.CrmService
import com.sunpos.backend.domain.order.CreateOrderRequest
import com.sunpos.backend.domain.order.OrderItemRequest
import com.sunpos.backend.domain.order.OrderService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TaxInvoiceAndPaymentEnhancementTest {

    @Autowired
    private lateinit var taxInvoiceService: TaxInvoiceService

    @Autowired
    private lateinit var paymentService: PaymentService

    @Autowired
    private lateinit var orderService: OrderService

    @Autowired
    private lateinit var catalogService: CatalogService

    @Autowired
    private lateinit var businessDayService: BusinessDayService

    @Autowired
    private lateinit var crmService: CrmService

    @Test
    fun `test Merge Receipts to single Tax Invoice without recalculating discounts`() {
        businessDayService.getOrCreateOpenBusinessDay("branch-tax")

        val cat = catalogService.createCategory(MenuCategory(branchId = "branch-tax", name = "Food"))
        val item1 = catalogService.createMenuItem(
            MenuItemCreateDto(branchId = "branch-tax", categoryId = cat.id, name = "Steak", basePrice = BigDecimal("535.00"))
        )
        val item2 = catalogService.createMenuItem(
            MenuItemCreateDto(branchId = "branch-tax", categoryId = cat.id, name = "Salad", basePrice = BigDecimal("321.00"))
        )

        val order1 = orderService.createOrder(
            CreateOrderRequest(
                branchId = "branch-tax",
                items = listOf(OrderItemRequest(menuItemId = item1.id, nameSnapshot = "Steak", unitPriceSnapshot = BigDecimal("535.00"), quantity = BigDecimal.ONE))
            )
        )
        paymentService.processPayment(
            PaymentRequestDto(
                orderId = order1.id,
                branchId = "branch-tax",
                paymentMethod = PaymentMethod.CASH,
                amount = BigDecimal("535.00")
            )
        )

        val order2 = orderService.createOrder(
            CreateOrderRequest(
                branchId = "branch-tax",
                items = listOf(OrderItemRequest(menuItemId = item2.id, nameSnapshot = "Salad", unitPriceSnapshot = BigDecimal("321.00"), quantity = BigDecimal.ONE))
            )
        )
        paymentService.processPayment(
            PaymentRequestDto(
                orderId = order2.id,
                branchId = "branch-tax",
                paymentMethod = PaymentMethod.PROMPTPAY,
                amount = BigDecimal("321.00")
            )
        )

        // Issue Merged Tax Invoice: Order 1 (535 gross) + Order 2 (321 gross) = Total Gross 856
        // VAT Inclusive: Net = 856 / 1.07 = 800.00, Tax = 56.00
        val taxInvoice = taxInvoiceService.mergeReceiptsToTaxInvoice(
            CreateTaxInvoiceDto(
                branchId = "branch-tax",
                customerId = "cust-corp-99",
                taxpayerName = "Acme Corp Thailand Ltd.",
                taxId = "0105559999888",
                branchNumber = "00001",
                address = "123 Sukhumvit Rd, Bangkok",
                email = "finance@acmecorp.co.th",
                phone = "021234567",
                orderIds = listOf(order1.id, order2.id),
                createdBy = "cashier-001"
            )
        )

        assertNotNull(taxInvoice.id)
        assertTrue(taxInvoice.taxInvoiceNumber.startsWith("TI-"))
        assertEquals(BigDecimal("800.0000"), taxInvoice.totalNetAmount)
        assertEquals(BigDecimal("56.0000"), taxInvoice.totalTaxAmount) // 7% inclusive VAT

        // Prevent merging already issued receipt
        assertThrows<IllegalArgumentException> {
            taxInvoiceService.mergeReceiptsToTaxInvoice(
                CreateTaxInvoiceDto(
                    branchId = "branch-tax",
                    customerId = null,
                    taxpayerName = "Acme",
                    taxId = "123",
                    address = "Addr",
                    email = null,
                    phone = null,
                    orderIds = listOf(order1.id),
                    createdBy = "cashier"
                )
            )
        }
    }

    @Test
    fun `test Payment refund reverses CRM points`() {
        businessDayService.getOrCreateOpenBusinessDay("branch-crm")
        val customer = crmService.createCustomer(CreateCustomerDto(firstName = "Alice", lastName = "Loyalist", phone = "0819998877"))
        val initialPoints = crmService.calculatePointsBalance(customer.customer.id)

        val cat = catalogService.createCategory(MenuCategory(branchId = "branch-crm", name = "Coffee"))
        val item = catalogService.createMenuItem(
            MenuItemCreateDto(branchId = "branch-crm", categoryId = cat.id, name = "Latte", basePrice = BigDecimal("100.00"))
        )

        val order = orderService.createOrder(
            CreateOrderRequest(
                branchId = "branch-crm",
                customerId = customer.customer.id,
                items = listOf(OrderItemRequest(menuItemId = item.id, nameSnapshot = "Latte", unitPriceSnapshot = BigDecimal("100.00"), quantity = BigDecimal.ONE))
            )
        )

        val payment = paymentService.processPayment(
            PaymentRequestDto(
                orderId = order.id,
                branchId = "branch-crm",
                paymentMethod = PaymentMethod.CASH,
                amount = BigDecimal("100.00")
            )
        )

        // Refund payment
        paymentService.refundPayment(
            payment.id,
            RefundRequestDto(paymentTransactionId = payment.id, amount = BigDecimal("100.00"), reason = "Wrong order", approvedBy = "mgr-01")
        )

        val finalPoints = crmService.calculatePointsBalance(customer.customer.id)
        assertEquals(initialPoints, finalPoints)
    }
}
