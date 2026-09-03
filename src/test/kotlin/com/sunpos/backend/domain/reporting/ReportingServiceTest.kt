package com.sunpos.backend.domain.reporting

import com.sunpos.backend.domain.catalog.*
import com.sunpos.backend.domain.businessday.BusinessDayService
import com.sunpos.backend.domain.inventory.InventoryStock
import com.sunpos.backend.domain.inventory.InventoryStockRepository
import com.sunpos.backend.domain.order.CreateOrderRequest
import com.sunpos.backend.domain.order.OrderItemRequest
import com.sunpos.backend.domain.order.OrderService
import com.sunpos.backend.domain.order.OrderStatus
import com.sunpos.backend.domain.payment.PaymentMethod
import com.sunpos.backend.domain.payment.PaymentRequestDto
import com.sunpos.backend.domain.payment.PaymentService
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
class ReportingServiceTest {

    @Autowired
    private lateinit var orderService: OrderService

    @Autowired
    private lateinit var catalogService: CatalogService

    @Autowired
    private lateinit var businessDayService: BusinessDayService

    @Autowired
    private lateinit var paymentService: PaymentService

    @Autowired
    private lateinit var inventoryStockRepository: InventoryStockRepository

    @Autowired
    private lateinit var reportingService: ReportingService

    @Test
    fun `test read-only reporting calculations for sales, payment breakdown, gross profit, inventory valuation, and crm analytics`() {
        val category = catalogService.createCategory(MenuCategory(branchId = "branch-001", name = "Food"))
        val menuItem = catalogService.createMenuItem(
            MenuItemCreateDto(branchId = "branch-001", categoryId = category.id, name = "Pad Kra Pao", basePrice = BigDecimal("100.00"))
        )

        // Setup Business Day
        businessDayService.getOrCreateOpenBusinessDay("branch-001")

        // 1. Create and Complete Order 1 (฿200)
        val order1 = orderService.createOrder(
            CreateOrderRequest(
                branchId = "branch-001",
                items = listOf(
                    OrderItemRequest(
                        menuItemId = menuItem.id,
                        nameSnapshot = "Pad Kra Pao",
                        unitPriceSnapshot = BigDecimal("100.00"),
                        quantity = BigDecimal("2")
                    )
                )
            )
        )
        paymentService.processPayment(
            PaymentRequestDto(
                orderId = order1.id,
                branchId = "branch-001",
                deviceId = "pos-device-001",
                paymentMethod = PaymentMethod.CASH,
                amount = BigDecimal("200.00")
            )
        )

        // 2. Add Inventory Stock for Valuation Test
        val invStock = inventoryStockRepository.save(
            InventoryStock(
                warehouseId = "wh-001",
                inventoryItemId = "item-pork-01",
                quantity = BigDecimal("50.0000"),
                weightedAverageCost = BigDecimal("120.0000")
            )
        )

        // 3. Test Sales Report Summary
        val salesSummary = reportingService.getSalesReportSummary("branch-001")
        assertNotNull(salesSummary)
        assertTrue(salesSummary.totalSalesAmount >= BigDecimal("200.00"))
        assertTrue(salesSummary.totalOrderCount >= 1)

        // 4. Test Payment Method Breakdown
        val paymentBreakdown = reportingService.getPaymentMethodBreakdown("branch-001")
        assertFalse(paymentBreakdown.isEmpty())
        val cashPayment = paymentBreakdown.firstOrNull { it.paymentMethod == "CASH" }
        assertNotNull(cashPayment)
        assertTrue(cashPayment!!.totalAmount >= BigDecimal("200.00"))

        // 5. Test Gross Profit Formula (Net Sales - COGS)
        val profitReport = reportingService.getGrossProfitReport("branch-001")
        assertNotNull(profitReport)
        assertTrue(profitReport.netSales >= BigDecimal("200.00"))
        assertNotNull(profitReport.grossProfitAmount)

        // 6. Test Inventory Valuation Report
        val invReport = reportingService.getInventoryValuationReport("branch-001")
        assertNotNull(invReport)
        assertTrue(invReport.totalValuationAmount >= BigDecimal("6000.00")) // 50 * 120

        // 7. Test CRM Analytics Report
        val crmReport = reportingService.getCrmAnalytics()
        assertNotNull(crmReport)
        assertTrue(crmReport.totalCustomers >= 0)
    }
}
