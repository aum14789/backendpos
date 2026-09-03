package sun.clientpos.data.repository

import sun.clientpos.data.local.dao.OrderDao
import sun.clientpos.data.local.dao.PaymentDao
import sun.clientpos.data.local.dao.ShiftDao
import sun.clientpos.data.local.dao.SyncOutboxDao
import sun.clientpos.data.local.entity.RoomCashMovementEntity
import sun.clientpos.data.local.entity.RoomCashierShiftEntity
import sun.clientpos.data.local.entity.RoomPaymentTransactionEntity
import sun.clientpos.data.local.entity.SyncOutboxEntity
import java.util.UUID

class PaymentRepository(
    private val paymentDao: PaymentDao,
    private val orderDao: OrderDao,
    private val outboxDao: SyncOutboxDao
) {
    /**
     * Process a payment locally.
     * All amounts in satang (minor units). 1 Baht = 100 Satang.
     */
    suspend fun processPaymentLocal(
        orderId: String,
        branchId: String,
        deviceId: String?,
        shiftId: String?,
        paymentMethod: String,
        amount: Long, // satang
        tenderedAmount: Long, // satang
        orderTotalAmount: Long, // satang
        createdBy: String?
    ): RoomPaymentTransactionEntity {
        val paymentId = UUID.randomUUID().toString()
        val changeAmount = if (paymentMethod == "CASH" && tenderedAmount > amount) tenderedAmount - amount else 0L

        val paymentEntity = RoomPaymentTransactionEntity(
            paymentId = paymentId,
            orderId = orderId,
            branchId = branchId,
            deviceId = deviceId,
            shiftId = shiftId,
            paymentMethod = paymentMethod,
            amount = amount,
            tenderedAmount = tenderedAmount,
            changeAmount = changeAmount,
            status = "SUCCESS",
            idempotencyKey = UUID.randomUUID().toString(),
            externalRef = null,
            createdBy = createdBy
        )
        paymentDao.insertPayment(paymentEntity)

        // Enqueue PAYMENT_COMPLETED event into sync_outbox
        val paymentOutboxEvent = SyncOutboxEntity(
            aggregateType = "PAYMENT",
            aggregateId = paymentId,
            eventType = "PAYMENT_COMPLETED",
            payload = """{"paymentId":"$paymentId","orderId":"$orderId","method":"$paymentMethod","amountSatang":$amount,"tenderedSatang":$tenderedAmount,"changeSatang":$changeAmount,"createdBy":"$createdBy"}""",
            deviceId = deviceId ?: "POS-LOCAL",
            branchId = branchId
        )
        outboxDao.insertEvent(paymentOutboxEvent)

        // Check if total paid >= orderTotalAmount -> Update Order Status to COMPLETED and enqueue ORDER_COMPLETED
        val existingPayments = paymentDao.getPaymentsByOrder(orderId).filter { it.status == "SUCCESS" }
        val totalPaid = existingPayments.sumOf { it.amount }

        if (totalPaid >= orderTotalAmount) {
            orderDao.updateOrderStatus(orderId, "COMPLETED")

            val orderCompletedOutboxEvent = SyncOutboxEntity(
                aggregateType = "ORDER",
                aggregateId = orderId,
                eventType = "ORDER_COMPLETED",
                payload = """{"orderId":"$orderId","status":"COMPLETED","totalPaidSatang":$totalPaid,"orderTotalSatang":$orderTotalAmount,"financialStatus":"PAID"}""",
                deviceId = deviceId ?: "POS-LOCAL",
                branchId = branchId
            )
            outboxDao.insertEvent(orderCompletedOutboxEvent)
        }

        return paymentEntity
    }
}

class ShiftRepository(
    private val shiftDao: ShiftDao,
    private val outboxDao: SyncOutboxDao
) {
    /**
     * Open a new cashier shift.
     * openingCash in satang (minor units).
     */
    suspend fun openShiftLocal(
        branchId: String,
        deviceId: String,
        userId: String,
        openingCash: Long // satang
    ): RoomCashierShiftEntity {
        val active = shiftDao.getActiveShift(branchId, deviceId)
        if (active != null) return active

        val shiftId = UUID.randomUUID().toString()
        val shift = RoomCashierShiftEntity(
            shiftId = shiftId,
            branchId = branchId,
            deviceId = deviceId,
            userId = userId,
            openingCash = openingCash,
            expectedCash = openingCash
        )
        shiftDao.insertShift(shift)

        val outboxEvent = SyncOutboxEntity(
            aggregateType = "SHIFT",
            aggregateId = shiftId,
            eventType = "SHIFT_OPENED",
            payload = """{"shiftId":"$shiftId","branchId":"$branchId","deviceId":"$deviceId","userId":"$userId","openingCashSatang":$openingCash}""",
            deviceId = deviceId,
            branchId = branchId
        )
        outboxDao.insertEvent(outboxEvent)

        return shift
    }

    /**
     * Record cash in / cash out movement.
     */
    suspend fun recordCashMovementLocal(
        shiftId: String,
        movementType: String, // CASH_IN, CASH_OUT
        amount: Long, // satang
        reason: String,
        createdBy: String,
        deviceId: String,
        branchId: String
    ): RoomCashMovementEntity {
        val shift = shiftDao.getShiftById(shiftId) ?: throw IllegalArgumentException("Shift not found")
        require(shift.status == "OPEN") { "Cannot add movement to closed shift" }

        val movementId = UUID.randomUUID().toString()
        val movement = RoomCashMovementEntity(
            movementId = movementId,
            shiftId = shiftId,
            movementType = movementType,
            amount = amount,
            reason = reason,
            createdBy = createdBy
        )
        shiftDao.insertCashMovement(movement)

        val updatedShift = if (movementType == "CASH_IN") {
            val newCashIn = shift.cashIn + amount
            shift.copy(cashIn = newCashIn, expectedCash = shift.openingCash + newCashIn - shift.cashOut + shift.cashSales)
        } else {
            val newCashOut = shift.cashOut + amount
            shift.copy(cashOut = newCashOut, expectedCash = shift.openingCash + shift.cashIn - newCashOut + shift.cashSales)
        }
        shiftDao.insertShift(updatedShift)

        return movement
    }

    /**
     * Close cashier shift with expected vs actual variance calculation.
     */
    suspend fun closeShiftLocal(
        shiftId: String,
        actualCash: Long, // satang
        closingNotes: String?,
        deviceId: String,
        branchId: String
    ): RoomCashierShiftEntity {
        val shift = shiftDao.getShiftById(shiftId) ?: throw IllegalArgumentException("Shift not found")
        if (shift.status == "CLOSED") return shift

        val variance = actualCash - shift.expectedCash
        val varianceType = when {
            variance > 0L -> "OVER"
            variance < 0L -> "SHORT"
            else -> "ZERO"
        }

        val closedShift = shift.copy(
            actualCash = actualCash,
            variance = variance,
            varianceType = varianceType,
            closingNotes = closingNotes,
            status = "CLOSED",
            closedAt = System.currentTimeMillis()
        )
        shiftDao.insertShift(closedShift)

        val outboxEvent = SyncOutboxEntity(
            aggregateType = "SHIFT",
            aggregateId = shiftId,
            eventType = "SHIFT_CLOSED",
            payload = """{"shiftId":"$shiftId","expectedCashSatang":${shift.expectedCash},"actualCashSatang":$actualCash,"varianceSatang":$variance,"varianceType":"$varianceType","status":"CLOSED"}""",
            deviceId = deviceId,
            branchId = branchId
        )
        outboxDao.insertEvent(outboxEvent)

        return closedShift
    }
}
