package sun.clientpos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Payment transaction entity.
 * All monetary fields stored in satang (minor units). 1 Baht = 100 Satang.
 */
@Entity(tableName = "room_payment_transactions")
data class RoomPaymentTransactionEntity(
    @PrimaryKey val paymentId: String = UUID.randomUUID().toString(),
    val orderId: String,
    val branchId: String,
    val deviceId: String?,
    val shiftId: String?,
    val paymentMethod: String = "CASH", // CASH, CARD, QR, PROMPTPAY, EWALLET, VOUCHER
    val amount: Long = 0L, // satang
    val tenderedAmount: Long = 0L, // satang
    val changeAmount: Long = 0L, // satang
    val status: String = "SUCCESS", // PENDING, SUCCESS, FAILED, REFUNDED
    val idempotencyKey: String?,
    val externalRef: String?,
    val createdBy: String?,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Refund transaction entity.
 * amount stored in satang (minor units).
 */
@Entity(tableName = "room_refund_transactions")
data class RoomRefundTransactionEntity(
    @PrimaryKey val refundId: String = UUID.randomUUID().toString(),
    val paymentTransactionId: String,
    val orderId: String,
    val branchId: String,
    val amount: Long = 0L, // satang
    val reason: String?,
    val status: String = "COMPLETED",
    val approvedBy: String?,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Cashier shift entity.
 * All monetary fields stored in satang (minor units).
 */
@Entity(tableName = "room_cashier_shifts")
data class RoomCashierShiftEntity(
    @PrimaryKey val shiftId: String = UUID.randomUUID().toString(),
    val branchId: String,
    val deviceId: String,
    val userId: String,
    val openedAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null,
    val status: String = "OPEN", // OPEN, CLOSED
    val openingCash: Long = 0L, // satang
    val cashSales: Long = 0L, // satang
    val cashIn: Long = 0L, // satang
    val cashOut: Long = 0L, // satang
    val refundCash: Long = 0L, // satang
    val expectedCash: Long = 0L, // satang
    val actualCash: Long = 0L, // satang
    val variance: Long = 0L, // satang
    val varianceType: String = "ZERO", // ZERO, OVER, SHORT
    val closingNotes: String? = null
)

/**
 * Cash movement entity (CASH_IN / CASH_OUT during shift).
 * amount stored in satang (minor units).
 */
@Entity(tableName = "room_cash_movements")
data class RoomCashMovementEntity(
    @PrimaryKey val movementId: String = UUID.randomUUID().toString(),
    val shiftId: String,
    val movementType: String = "CASH_IN", // CASH_IN, CASH_OUT
    val amount: Long = 0L, // satang
    val reason: String?,
    val createdBy: String?,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Business day entity.
 * All summary monetary fields stored in satang (minor units).
 */
@Entity(tableName = "room_business_days")
data class RoomBusinessDayEntity(
    @PrimaryKey val businessDayId: String = UUID.randomUUID().toString(),
    val branchId: String,
    val businessDate: String,
    val closingTimeSetting: String = "02:00",
    val openedAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null,
    val status: String = "OPEN", // OPEN, PROCESSING, CLOSED
    val totalSales: Long = 0L, // satang
    val totalCashPayments: Long = 0L, // satang
    val totalNonCashPayments: Long = 0L, // satang
    val totalRefunds: Long = 0L // satang
)
