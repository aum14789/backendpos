package com.sunpos.backend.domain.reporting

import java.math.BigDecimal
import java.time.Instant

data class SalesReportSummaryDto(
    val totalSalesAmount: BigDecimal,
    val totalOrderCount: Long,
    val averageOrderValue: BigDecimal,
    val totalDiscountAmount: BigDecimal,
    val netSalesAmount: BigDecimal
)

data class HourlySalesDto(
    val hourOfDay: Int,
    val salesAmount: BigDecimal,
    val orderCount: Long
)

data class CategorySalesDto(
    val categoryName: String,
    val salesAmount: BigDecimal,
    val itemCount: Long
)

data class PaymentMethodReportDto(
    val paymentMethod: String,
    val totalAmount: BigDecimal,
    val transactionCount: Long
)

data class GrossProfitReportDto(
    val grossSales: BigDecimal,
    val discounts: BigDecimal,
    val taxAmount: BigDecimal,
    val serviceChargeAmount: BigDecimal,
    val netSales: BigDecimal,
    val cogsAmount: BigDecimal,
    val grossProfitAmount: BigDecimal,
    val grossProfitMarginPercent: BigDecimal
)

data class InventoryValuationReportDto(
    val totalOnHandQuantity: BigDecimal,
    val totalValuationAmount: BigDecimal,
    val totalWasteQuantity: BigDecimal,
    val totalWasteValue: BigDecimal
)

data class CrmAnalyticsDto(
    val totalCustomers: Long,
    val newCustomersCount: Long,
    val totalPointsEarned: Long,
    val totalPointsRedeemed: Long,
    val activeMemberCount: Long
)

data class VoidAuditItemDto(
    val id: String,
    val orderId: String,
    val name: String,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val totalPrice: BigDecimal,
    val voidedBy: String?,
    val voidApprovedBy: String?,
    val voidedAt: String?,
    val voidReason: String?,
    val isWaste: Boolean,
    val orderedBy: String?,
    val orderedAt: String?
)

data class VoidAuditReportDto(
    val items: List<VoidAuditItemDto>,
    val totalCount: Int,
    val totalAmount: BigDecimal,
    val wasteCount: Int,
    val wasteAmount: BigDecimal,
    val restockCount: Int,
    val restockAmount: BigDecimal
)

data class TableTransferAuditDto(
    val id: String,
    val orderId: String,
    val fromTableId: String,
    val fromTableName: String,
    val toTableId: String,
    val toTableName: String,
    val transferredBy: String?,
    val transferredAt: String?,
    val reason: String?
)

data class BillCheckAuditDto(
    val id: String,
    val orderId: String,
    val checkSequence: Int,
    val checkedAt: String?,
    val checkedBy: String?,
    val snapshotTotalSatang: Long,
    val snapshotTotalBaht: BigDecimal
)

