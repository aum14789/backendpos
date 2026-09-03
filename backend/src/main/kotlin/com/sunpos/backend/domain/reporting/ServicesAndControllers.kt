package com.sunpos.backend.domain.reporting

import com.sunpos.backend.common.ApiResponse
import com.sunpos.backend.domain.crm.CustomerRepository
import com.sunpos.backend.domain.crm.PointLedgerRepository
import com.sunpos.backend.domain.crm.PointTransactionType
import com.sunpos.backend.domain.inventory.InventoryStockRepository
import com.sunpos.backend.domain.inventory.StockMovementRepository
import com.sunpos.backend.domain.inventory.MovementType
import com.sunpos.backend.domain.order.OrderRepository
import com.sunpos.backend.domain.order.OrderStatus
import com.sunpos.backend.domain.payment.PaymentTransactionRepository
import com.sunpos.backend.domain.payment.PaymentStatus
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant

@Service
class ReportingService(
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentTransactionRepository,
    private val inventoryStockRepository: InventoryStockRepository,
    private val stockMovementRepository: StockMovementRepository,
    private val customerRepository: CustomerRepository,
    private val pointLedgerRepository: PointLedgerRepository
) {

    fun getSalesReportSummary(branchId: String?): SalesReportSummaryDto {
        val allOrders = orderRepository.findAll().filter {
            it.status == OrderStatus.COMPLETED && (branchId == null || it.branchId == branchId)
        }

        val totalSales = allOrders.map { it.totalAmount }.fold(BigDecimal.ZERO) { acc, amt -> acc.add(amt) }
        val totalDiscount = BigDecimal.ZERO
        val orderCount = allOrders.size.toLong()
        val avgOrderVal = if (orderCount > 0) totalSales.divide(BigDecimal(orderCount), 2, RoundingMode.HALF_UP) else BigDecimal.ZERO
        val netSales = totalSales.subtract(totalDiscount)

        return SalesReportSummaryDto(
            totalSalesAmount = totalSales,
            totalOrderCount = orderCount,
            averageOrderValue = avgOrderVal,
            totalDiscountAmount = totalDiscount,
            netSalesAmount = netSales
        )
    }

    fun getPaymentMethodBreakdown(branchId: String?): List<PaymentMethodReportDto> {
        val payments = paymentRepository.findAll().filter {
            it.status == PaymentStatus.SUCCESS && (branchId == null || it.branchId == branchId)
        }

        return payments.groupBy { it.paymentMethod.name }.map { entry ->
            val totalAmt = entry.value.map { it.amount }.fold(BigDecimal.ZERO) { acc, amt -> acc.add(amt) }
            PaymentMethodReportDto(
                paymentMethod = entry.key,
                totalAmount = totalAmt,
                transactionCount = entry.value.size.toLong()
            )
        }.sortedByDescending { it.totalAmount }
    }

    fun getGrossProfitReport(branchId: String?): GrossProfitReportDto {
        val salesSummary = getSalesReportSummary(branchId)
        val grossSales = salesSummary.totalSalesAmount
        val discounts = salesSummary.totalDiscountAmount
        val netSales = salesSummary.netSalesAmount

        // Estimate COGS from Stock Movements (SALE_CONSUMPTION)
        val movements = stockMovementRepository.findAll().filter {
            it.movementType == MovementType.SALE_CONSUMPTION
        }
        val cogs = movements.map { it.quantity.multiply(it.unitCost) }
            .fold(BigDecimal.ZERO) { acc, amt -> acc.add(amt) }

        val grossProfit = netSales.subtract(cogs)
        val margin = if (netSales.compareTo(BigDecimal.ZERO) > 0) {
            grossProfit.multiply(BigDecimal(100)).divide(netSales, 2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        return GrossProfitReportDto(
            grossSales = grossSales,
            discounts = discounts,
            taxAmount = BigDecimal.ZERO,
            serviceChargeAmount = BigDecimal.ZERO,
            netSales = netSales,
            cogsAmount = cogs,
            grossProfitAmount = grossProfit,
            grossProfitMarginPercent = margin
        )
    }

    fun getInventoryValuationReport(branchId: String?): InventoryValuationReportDto {
        val stocks = inventoryStockRepository.findAll()
        val totalQty = stocks.map { it.quantity }.fold(BigDecimal.ZERO) { acc, amt -> acc.add(amt) }
        val totalValuation = stocks.map { it.quantity.multiply(it.weightedAverageCost) }.fold(BigDecimal.ZERO) { acc, amt -> acc.add(amt) }

        val wasteMovements = stockMovementRepository.findAll().filter { it.movementType == MovementType.WASTE }
        val wasteQty = wasteMovements.map { it.quantity }.fold(BigDecimal.ZERO) { acc, amt -> acc.add(amt) }
        val wasteVal = wasteMovements.map { it.quantity.multiply(it.unitCost) }.fold(BigDecimal.ZERO) { acc, amt -> acc.add(amt) }

        return InventoryValuationReportDto(
            totalOnHandQuantity = totalQty,
            totalValuationAmount = totalValuation,
            totalWasteQuantity = wasteQty,
            totalWasteValue = wasteVal
        )
    }

    fun getCrmAnalytics(): CrmAnalyticsDto {
        val customers = customerRepository.findAll()
        val ledgers = pointLedgerRepository.findAll()

        val earned = ledgers.filter { it.transactionType == PointTransactionType.EARN }
            .map { it.points.toLong() }.sum()
        val redeemed = ledgers.filter { it.transactionType == PointTransactionType.REDEEM }
            .map { kotlin.math.abs(it.points.toLong()) }.sum()

        return CrmAnalyticsDto(
            totalCustomers = customers.size.toLong(),
            newCustomersCount = customers.size.toLong(),
            totalPointsEarned = earned,
            totalPointsRedeemed = redeemed,
            activeMemberCount = customers.size.toLong()
        )
    }
}

@RestController
@RequestMapping("/api/v1/reports")
class ReportingController(
    private val reportingService: ReportingService
) {

    @GetMapping("/sales")
    fun getSalesReport(@RequestParam(required = false) branchId: String?): ApiResponse<SalesReportSummaryDto> {
        return ApiResponse.success(reportingService.getSalesReportSummary(branchId))
    }

    @GetMapping("/payments")
    fun getPaymentReport(@RequestParam(required = false) branchId: String?): ApiResponse<List<PaymentMethodReportDto>> {
        return ApiResponse.success(reportingService.getPaymentMethodBreakdown(branchId))
    }

    @GetMapping("/financial")
    fun getFinancialReport(@RequestParam(required = false) branchId: String?): ApiResponse<GrossProfitReportDto> {
        return ApiResponse.success(reportingService.getGrossProfitReport(branchId))
    }

    @GetMapping("/inventory")
    fun getInventoryValuation(@RequestParam(required = false) branchId: String?): ApiResponse<InventoryValuationReportDto> {
        return ApiResponse.success(reportingService.getInventoryValuationReport(branchId))
    }

    @GetMapping("/crm")
    fun getCrmAnalytics(): ApiResponse<CrmAnalyticsDto> {
        return ApiResponse.success(reportingService.getCrmAnalytics())
    }
}
