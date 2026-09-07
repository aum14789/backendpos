package com.sunpos.backend.domain.payment

import com.sunpos.backend.common.ApiResponse
import com.sunpos.backend.common.JdbcRepository
import org.springframework.jdbc.core.JdbcTemplate
import com.sunpos.backend.domain.order.OrderRepository
import com.sunpos.backend.domain.order.OrderStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Optional

import com.sunpos.backend.domain.crm.CrmService

@Repository
class PaymentTransactionRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<PaymentTransaction>(jdbcTemplate, "payment_transactions", PaymentTransaction::class.java) {
    fun findByOrderId(orderId: String): List<PaymentTransaction> = findByField("orderId", orderId)
    fun findByIdempotencyKey(idempotencyKey: String): Optional<PaymentTransaction> = findOneByField("idempotencyKey", idempotencyKey)
    fun findByBranchId(branchId: String): List<PaymentTransaction> = findByField("branchId", branchId)
}

@Repository
class RefundTransactionRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<RefundTransaction>(jdbcTemplate, "refund_transactions", RefundTransaction::class.java) {
    fun findByPaymentTransactionId(paymentTransactionId: String): List<RefundTransaction> = findByField("paymentTransactionId", paymentTransactionId)
}

@Service
class PaymentService(
    private val paymentRepository: PaymentTransactionRepository,
    private val refundRepository: RefundTransactionRepository,
    private val orderRepository: OrderRepository,
    private val crmService: CrmService
) {
    companion object {
        const val SCALE = 4
        val ROUNDING = RoundingMode.HALF_UP
    }

    @Transactional
    fun processPayment(req: PaymentRequestDto): PaymentResponseDto {
        // Idempotency check
        if (!req.idempotencyKey.isNullOrBlank()) {
            val existingOpt = paymentRepository.findByIdempotencyKey(req.idempotencyKey)
            if (existingOpt.isPresent) {
                return toResponseDto(existingOpt.get())
            }
        }

        val order = orderRepository.findById(req.orderId).orElseThrow { IllegalArgumentException("Order not found") }

        // Validate amount > 0
        if (req.amount <= BigDecimal.ZERO) {
            throw IllegalArgumentException("Payment amount must be greater than zero")
        }

        // Check total successful payments for order
        val existingPayments = paymentRepository.findByOrderId(req.orderId)
            .filter { it.status == PaymentStatus.SUCCESS || it.status == PaymentStatus.PARTIALLY_REFUNDED }
        val totalPaidSoFar = existingPayments.map { it.amount }.fold(BigDecimal.ZERO) { acc, a -> acc.add(a) }

        val remainingBalance = order.totalAmount.subtract(totalPaidSoFar)
        val amount = req.amount.setScale(SCALE, ROUNDING)

        if (amount > remainingBalance) {
            throw IllegalArgumentException("Payment amount exceeds remaining order balance. Remaining: $remainingBalance, Attempted: $amount")
        }

        val tendered = (req.tenderedAmount ?: req.amount).setScale(SCALE, ROUNDING)
        if (tendered < amount) {
            throw IllegalArgumentException("Tendered amount cannot be less than payment amount")
        }

        val change = if (req.paymentMethod == PaymentMethod.CASH && tendered > amount) {
            tendered.subtract(amount).setScale(SCALE, ROUNDING)
        } else {
            BigDecimal.ZERO.setScale(SCALE, ROUNDING)
        }

        val payment = PaymentTransaction(
            orderId = req.orderId,
            branchId = req.branchId,
            deviceId = req.deviceId,
            shiftId = req.shiftId,
            paymentMethod = req.paymentMethod,
            amount = amount,
            tenderedAmount = tendered,
            changeAmount = change,
            status = PaymentStatus.SUCCESS,
            idempotencyKey = req.idempotencyKey,
            externalRef = req.externalRef,
            createdBy = req.createdBy
        )
        val savedPayment = paymentRepository.save(payment)

        val totalPaid = totalPaidSoFar.add(amount)
        // If total payments >= order total, transition order to COMPLETED
        if (totalPaid >= order.totalAmount && order.status != OrderStatus.COMPLETED) {
            order.status = OrderStatus.COMPLETED
            orderRepository.save(order)
        }

        return toResponseDto(savedPayment)
    }

    @Transactional
    fun refundPayment(paymentId: String, req: RefundRequestDto): RefundTransaction {
        val payment = paymentRepository.findById(paymentId).orElseThrow { IllegalArgumentException("Payment not found") }
        if (payment.status == PaymentStatus.REFUNDED) {
            throw IllegalArgumentException("Payment already fully refunded")
        }

        // Prevent negative refunds
        if (req.amount <= BigDecimal.ZERO) {
            throw IllegalArgumentException("Refund amount must be greater than zero")
        }

        val refundAmount = req.amount.setScale(SCALE, ROUNDING)
        val existingRefunds = refundRepository.findByPaymentTransactionId(payment.id)
        val totalRefundedSoFar = existingRefunds.map { it.amount }.fold(BigDecimal.ZERO) { acc, a -> acc.add(a) }

        val newTotalRefunded = totalRefundedSoFar.add(refundAmount)
        if (newTotalRefunded > payment.amount) {
            throw IllegalArgumentException("Refund amount exceeds original payment amount")
        }

        val refund = RefundTransaction(
            paymentTransactionId = payment.id,
            orderId = payment.orderId,
            branchId = payment.branchId,
            amount = refundAmount,
            reason = req.reason,
            status = RefundStatus.COMPLETED,
            approvedBy = req.approvedBy
        )
        val savedRefund = refundRepository.save(refund)

        payment.status = if (newTotalRefunded >= payment.amount) PaymentStatus.REFUNDED else PaymentStatus.PARTIALLY_REFUNDED
        paymentRepository.save(payment)

        // Trigger CRM points reverse
        try {
            val orderOpt = orderRepository.findById(payment.orderId)
            if (orderOpt.isPresent) {
                val order = orderOpt.get()
                order.customerId?.let { cid ->
                    crmService.reversePoints(order.id, cid)
                }
            }
        } catch (e: Exception) {
            // Log warning or suppress if CRM service not initialized
        }

        return savedRefund
    }

    fun getPaymentsByOrder(orderId: String): List<PaymentResponseDto> {
        return paymentRepository.findByOrderId(orderId).map { toResponseDto(it) }
    }

    fun listPayments(branchId: String): List<PaymentResponseDto> {
        return paymentRepository.findByBranchId(branchId).map { toResponseDto(it) }
    }

    private fun toResponseDto(p: PaymentTransaction) = PaymentResponseDto(
        id = p.id,
        orderId = p.orderId,
        branchId = p.branchId,
        paymentMethod = p.paymentMethod,
        amount = p.amount,
        tenderedAmount = p.tenderedAmount,
        changeAmount = p.changeAmount,
        status = p.status,
        idempotencyKey = p.idempotencyKey,
        externalRef = p.externalRef,
        createdAt = p.createdAt
    )
}

@RestController
@RequestMapping("/api/v1/payments")
class PaymentController(
    private val paymentService: PaymentService
) {
    @PostMapping
    @PreAuthorize("hasAuthority('ORDER_CREATE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_CASHIER')")
    fun processPayment(@RequestBody req: PaymentRequestDto): ApiResponse<PaymentResponseDto> {
        return ApiResponse.success(paymentService.processPayment(req), "Payment processed successfully")
    }

    @GetMapping("/order/{orderId}")
    fun getPaymentsByOrder(@PathVariable orderId: String): ApiResponse<List<PaymentResponseDto>> {
        return ApiResponse.success(paymentService.getPaymentsByOrder(orderId))
    }

    @GetMapping
    fun listPayments(@RequestParam branchId: String): ApiResponse<List<PaymentResponseDto>> {
        return ApiResponse.success(paymentService.listPayments(branchId))
    }

    @PostMapping("/{id}/refund")
    @PreAuthorize("hasAuthority('PAYMENT_REFUND') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun refundPayment(@PathVariable id: String, @RequestBody req: RefundRequestDto): ApiResponse<RefundTransaction> {
        return ApiResponse.success(paymentService.refundPayment(id, req), "Refund processed successfully")
    }
}
