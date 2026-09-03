package sun.clientpos.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import sun.clientpos.data.local.entity.*

@Dao
interface PaymentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: RoomPaymentTransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRefund(refund: RoomRefundTransactionEntity)

    @Query("SELECT * FROM room_payment_transactions WHERE orderId = :orderId")
    suspend fun getPaymentsByOrder(orderId: String): List<RoomPaymentTransactionEntity>

    @Query("SELECT * FROM room_payment_transactions WHERE branchId = :branchId ORDER BY createdAt DESC")
    fun observePayments(branchId: String): Flow<List<RoomPaymentTransactionEntity>>

    @Query("UPDATE room_payment_transactions SET status = :status WHERE paymentId = :paymentId")
    suspend fun updatePaymentStatus(paymentId: String, status: String)
}

@Dao
interface ShiftDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShift(shift: RoomCashierShiftEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCashMovement(movement: RoomCashMovementEntity)

    @Query("SELECT * FROM room_cashier_shifts WHERE branchId = :branchId AND deviceId = :deviceId AND status = 'OPEN' LIMIT 1")
    suspend fun getActiveShift(branchId: String, deviceId: String): RoomCashierShiftEntity?

    @Query("SELECT * FROM room_cashier_shifts WHERE shiftId = :shiftId")
    suspend fun getShiftById(shiftId: String): RoomCashierShiftEntity?

    @Query("SELECT * FROM room_cash_movements WHERE shiftId = :shiftId ORDER BY createdAt DESC")
    suspend fun getCashMovements(shiftId: String): List<RoomCashMovementEntity>
}

@Dao
interface BusinessDayDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBusinessDay(day: RoomBusinessDayEntity)

    @Query("SELECT * FROM room_business_days WHERE branchId = :branchId AND status = 'OPEN' LIMIT 1")
    suspend fun getOpenBusinessDay(branchId: String): RoomBusinessDayEntity?
}
