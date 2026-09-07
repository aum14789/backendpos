package com.sunpos.backend.domain.shift

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class ShiftStatus {
    OPEN,
    CLOSED
}

enum class CashMovementType {
    CASH_IN,
    CASH_OUT
}

enum class VarianceType {
    ZERO,
    OVER,
    SHORT
}

class CashierShift(
    val id: String = UUID.randomUUID().toString(),
    var branchId: String = "",
    var deviceId: String = "",
    var userId: String = "",
    val openedAt: Instant = Instant.now(),
    var closedAt: Instant? = null,
    var status: ShiftStatus = ShiftStatus.OPEN,
    var openingCash: BigDecimal = BigDecimal.ZERO,
    var cashSales: BigDecimal = BigDecimal.ZERO,
    var cashIn: BigDecimal = BigDecimal.ZERO,
    var cashOut: BigDecimal = BigDecimal.ZERO,
    var refundCash: BigDecimal = BigDecimal.ZERO,
    var expectedCash: BigDecimal = BigDecimal.ZERO,
    var actualCash: BigDecimal = BigDecimal.ZERO,
    var variance: BigDecimal = BigDecimal.ZERO,
    var varianceType: VarianceType = VarianceType.ZERO,
    var closingNotes: String? = null
)

class CashMovement(
    val id: String = UUID.randomUUID().toString(),
    var shiftId: String = "",
    var movementType: CashMovementType = CashMovementType.CASH_IN,
    var amount: BigDecimal = BigDecimal.ZERO,
    var reason: String? = null,
    var createdBy: String? = null,
    val createdAt: Instant = Instant.now()
)

// DTOs
data class OpenShiftDto(
    val branchId: String = "",
    val deviceId: String = "",
    val userId: String = "",
    val openingCash: BigDecimal = BigDecimal.ZERO
)

data class CashMovementDto(
    val shiftId: String = "",
    val movementType: CashMovementType = CashMovementType.CASH_IN,
    val amount: BigDecimal = BigDecimal.ZERO,
    val reason: String? = null,
    val createdBy: String? = null
)

data class CloseShiftDto(
    val shiftId: String = "",
    val actualCash: BigDecimal = BigDecimal.ZERO,
    val closingNotes: String? = null
)
