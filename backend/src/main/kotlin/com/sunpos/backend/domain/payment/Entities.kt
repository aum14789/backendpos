package com.sunpos.backend.domain.payment

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class PaymentMethod {
    CASH,
    CARD,
    QR,
    PROMPTPAY,
    EWALLET,
    VOUCHER,
    OTHER
}

enum class PaymentStatus {
    PENDING,
    SUCCESS,
    FAILED,
    CANCELLED,
    REFUNDED,
    PARTIALLY_REFUNDED
}

enum class RefundStatus {
    COMPLETED,
    FAILED
}

class PaymentTransaction(
    val id: String = UUID.randomUUID().toString(),
    var orderId: String = "",
    var branchId: String = "",
    var deviceId: String? = null,
    var shiftId: String? = null,
    var paymentMethod: PaymentMethod = PaymentMethod.CASH,
    var amount: BigDecimal = BigDecimal.ZERO,
    var tenderedAmount: BigDecimal? = BigDecimal.ZERO,
    var changeAmount: BigDecimal? = BigDecimal.ZERO,
    var status: PaymentStatus = PaymentStatus.SUCCESS,
    var idempotencyKey: String? = null,
    var externalRef: String? = null,
    var createdBy: String? = null,
    val createdAt: Instant = Instant.now()
)

class RefundTransaction(
    val id: String = UUID.randomUUID().toString(),
    var paymentTransactionId: String = "",
    var orderId: String = "",
    var branchId: String = "",
    var amount: BigDecimal = BigDecimal.ZERO,
    var reason: String? = null,
    var status: RefundStatus = RefundStatus.COMPLETED,
    var approvedBy: String? = null,
    val createdAt: Instant = Instant.now()
)

// DTOs
data class PaymentRequestDto(
    val orderId: String = "",
    val branchId: String = "",
    val deviceId: String? = null,
    val shiftId: String? = null,
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val amount: BigDecimal = BigDecimal.ZERO,
    val tenderedAmount: BigDecimal? = null,
    val idempotencyKey: String? = null,
    val externalRef: String? = null,
    val createdBy: String? = null
)

data class RefundRequestDto(
    val paymentTransactionId: String = "",
    val amount: BigDecimal = BigDecimal.ZERO,
    val reason: String? = null,
    val approvedBy: String? = null
)

data class PaymentResponseDto(
    val id: String = "",
    val orderId: String = "",
    val branchId: String = "",
    val paymentMethod: PaymentMethod = PaymentMethod.CASH,
    val amount: BigDecimal = BigDecimal.ZERO,
    val tenderedAmount: BigDecimal? = null,
    val changeAmount: BigDecimal? = null,
    val status: PaymentStatus = PaymentStatus.SUCCESS,
    val idempotencyKey: String? = null,
    val externalRef: String? = null,
    val createdAt: Instant = Instant.now()
)
