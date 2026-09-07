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
