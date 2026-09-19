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
    private val pointLedgerRepository: PointLedgerRepository,
    private val jdbcTemplate: org.springframework.jdbc.core.JdbcTemplate? = null
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

    fun getVoidAuditReport(branchId: String?): VoidAuditReportDto {
        if (jdbcTemplate == null) return VoidAuditReportDto(emptyList(), 0, BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, BigDecimal.ZERO)

        val sql = """
            SELECT oi.id, oi.order_id, oi.name_snapshot, oi.quantity, oi.unit_price_snapshot,
                   oi.is_voided, oi.status, oi.voided_by, oi.void_approved_by, oi.voided_at,
                   oi.void_reason, oi.is_waste, oi.ordered_by, oi.ordered_at
            FROM order_items oi
            JOIN orders o ON o.id = oi.order_id
            WHERE (oi.is_voided = true OR oi.status = 'VOIDED')
              AND (? IS NULL OR o.branch_id = ?)
            ORDER BY oi.voided_at DESC NULLS LAST
        """.trimIndent()

        val items = jdbcTemplate.query(sql, { rs, _ ->
            val qty = rs.getBigDecimal("quantity") ?: BigDecimal.ONE
            val unitPrice = rs.getBigDecimal("unit_price_snapshot") ?: BigDecimal.ZERO
            val isWaste = rs.getBoolean("is_waste")
            val total = unitPrice.multiply(qty)
            VoidAuditItemDto(
                id = rs.getString("id"),
                orderId = rs.getString("order_id"),
                name = rs.getString("name_snapshot") ?: "",
                quantity = qty,
                unitPrice = unitPrice,
                totalPrice = total,
                voidedBy = rs.getString("voided_by"),
                voidApprovedBy = rs.getString("void_approved_by"),
                voidedAt = rs.getTimestamp("voided_at")?.toInstant()?.toString(),
                voidReason = rs.getString("void_reason"),
                isWaste = isWaste,
                orderedBy = rs.getString("ordered_by"),
                orderedAt = rs.getTimestamp("ordered_at")?.toInstant()?.toString()
            )
        }, branchId, branchId)

        val totalCount = items.size
        val totalAmount = items.fold(BigDecimal.ZERO) { acc, i -> acc.add(i.totalPrice) }
        val wasteItems = items.filter { it.isWaste }
        val wasteCount = wasteItems.size
        val wasteAmount = wasteItems.fold(BigDecimal.ZERO) { acc, i -> acc.add(i.totalPrice) }
        val restockItems = items.filter { !it.isWaste }
        val restockCount = restockItems.size
        val restockAmount = restockItems.fold(BigDecimal.ZERO) { acc, i -> acc.add(i.totalPrice) }

        return VoidAuditReportDto(
            items = items,
            totalCount = totalCount,
            totalAmount = totalAmount,
            wasteCount = wasteCount,
            wasteAmount = wasteAmount,
            restockCount = restockCount,
            restockAmount = restockAmount
        )
    }

    fun getTableTransferAuditReport(branchId: String?): List<TableTransferAuditDto> {
        if (jdbcTemplate == null) return emptyList()
        val sql = """
            SELECT ttl.id, ttl.order_id, ttl.from_table_id, ttl.to_table_id,
                   ttl.transferred_by, ttl.transferred_at, ttl.reason,
                   t1.name_number as from_table_name, t2.name_number as to_table_name
            FROM table_transfer_logs ttl
            LEFT JOIN tables t1 ON t1.id = ttl.from_table_id
            LEFT JOIN tables t2 ON t2.id = ttl.to_table_id
            ORDER BY ttl.transferred_at DESC
        """.trimIndent()

        return jdbcTemplate.query(sql) { rs, _ ->
            TableTransferAuditDto(
                id = rs.getString("id"),
                orderId = rs.getString("order_id"),
                fromTableId = rs.getString("from_table_id"),
                fromTableName = rs.getString("from_table_name") ?: rs.getString("from_table_id"),
                toTableId = rs.getString("to_table_id"),
                toTableName = rs.getString("to_table_name") ?: rs.getString("to_table_id"),
                transferredBy = rs.getString("transferred_by"),
                transferredAt = rs.getTimestamp("transferred_at")?.toInstant()?.toString(),
                reason = rs.getString("reason")
            )
        }
    }

    fun getBillCheckAuditReport(orderId: String?): List<BillCheckAuditDto> {
        if (jdbcTemplate == null) return emptyList()
        val sql = """
            SELECT bcl.id, bcl.order_id, bcl.check_sequence, bcl.checked_at,
                   bcl.checked_by, bcl.snapshot_total_satang
            FROM bill_check_logs bcl
            WHERE (? IS NULL OR bcl.order_id = ?)
            ORDER BY bcl.order_id, bcl.check_sequence ASC
        """.trimIndent()

        return jdbcTemplate.query(sql, { rs, _ ->
            val satang = rs.getLong("snapshot_total_satang")
            BillCheckAuditDto(
                id = rs.getString("id"),
                orderId = rs.getString("order_id"),
                checkSequence = rs.getInt("check_sequence"),
                checkedAt = rs.getTimestamp("checked_at")?.toInstant()?.toString(),
                checkedBy = rs.getString("checked_by"),
                snapshotTotalSatang = satang,
                snapshotTotalBaht = BigDecimal(satang).divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
            )
        }, orderId, orderId)
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

    @GetMapping("/audit/voids")
    fun getVoidAuditReport(@RequestParam(required = false) branchId: String?): ApiResponse<VoidAuditReportDto> {
        return ApiResponse.success(reportingService.getVoidAuditReport(branchId))
    }

    @GetMapping("/audit/table-transfers")
    fun getTableTransferAuditReport(@RequestParam(required = false) branchId: String?): ApiResponse<List<TableTransferAuditDto>> {
        return ApiResponse.success(reportingService.getTableTransferAuditReport(branchId))
    }

    @GetMapping("/audit/bill-checks")
    fun getBillCheckAuditReport(@RequestParam(required = false) orderId: String?): ApiResponse<List<BillCheckAuditDto>> {
        return ApiResponse.success(reportingService.getBillCheckAuditReport(orderId))
    }
}

