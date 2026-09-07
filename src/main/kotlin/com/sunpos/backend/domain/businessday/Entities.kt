package com.sunpos.backend.domain.businessday

import java.math.BigDecimal
import java.time.LocalDate
import java.time.Instant
import java.util.UUID

enum class BusinessDayStatus {
    OPEN,
    PROCESSING,
    CLOSED
}

class BusinessDay(
    val id: String = UUID.randomUUID().toString(),
    var branchId: String = "",
    var businessDate: String = LocalDate.now().toString(), // Stored as ISO-8601 string for Firestore compatibility
    var closingTimeSetting: String = "02:00",
    val openedAt: Instant = Instant.now(),
    var closedAt: Instant? = null,
    var status: BusinessDayStatus = BusinessDayStatus.OPEN,
    var totalSales: BigDecimal = BigDecimal.ZERO,
    var totalCashPayments: BigDecimal = BigDecimal.ZERO,
    var totalNonCashPayments: BigDecimal = BigDecimal.ZERO,
    var totalRefunds: BigDecimal = BigDecimal.ZERO,
    var closedBy: String? = null
)

// DTOs
data class CloseEodDto(
    val branchId: String = "",
    val closedBy: String? = null
)
