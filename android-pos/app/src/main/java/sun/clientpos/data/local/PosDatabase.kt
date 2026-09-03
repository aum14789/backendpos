package sun.clientpos.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import sun.clientpos.data.local.dao.*
import sun.clientpos.data.local.entity.*

@Database(
    entities = [
        SyncOutboxEntity::class,
        CachedUserEntity::class,
        RoomBrandEntity::class,
        CachedBranchEntity::class,
        CachedDeviceEntity::class,
        CachedPermissionEntity::class,
        RoomZoneEntity::class,
        RoomTableTypeEntity::class,
        RoomTableEntity::class,
        RoomTableSessionEntity::class,
        RoomMenuCategoryEntity::class,
        RoomMenuItemEntity::class,
        RoomModifierGroupEntity::class,
        RoomModifierEntity::class,
        RoomCustomerEntity::class,
        RoomOrderEntity::class,
        RoomOrderItemEntity::class,
        RoomOrderItemModifierEntity::class,
        RoomPaymentTransactionEntity::class,
        RoomRefundTransactionEntity::class,
        RoomCashierShiftEntity::class,
        RoomCashMovementEntity::class,
        RoomBusinessDayEntity::class,
        // Phase 3: Buffet
        RoomBuffetTierEntity::class,
        RoomBuffetTierMenuItemEntity::class,
        RoomBuffetSessionEntity::class,
        // Phase 4: Promotions & Discounts
        RoomPromotionEntity::class,
        RoomPromotionEligibleProductEntity::class,
        RoomOrderAppliedPromotionEntity::class,
        // Device Capabilities
        DeviceCapabilityEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class PosDatabase : RoomDatabase() {

    abstract fun syncOutboxDao(): SyncOutboxDao
    abstract fun userDao(): UserDao
    abstract fun brandDao(): BrandDao
    abstract fun branchDao(): BranchDao
    abstract fun deviceDao(): DeviceDao
    abstract fun permissionDao(): PermissionDao
    abstract fun deviceCapabilityDao(): DeviceCapabilityDao
    abstract fun tableDao(): TableDao
    abstract fun tableSessionDao(): TableSessionDao
    abstract fun menuDao(): MenuDao
    abstract fun customerDao(): CustomerDao
    abstract fun orderDao(): OrderDao
    abstract fun paymentDao(): PaymentDao
    abstract fun shiftDao(): ShiftDao
    abstract fun businessDayDao(): BusinessDayDao
    abstract fun buffetDao(): BuffetDao
    abstract fun promotionDao(): PromotionDao

    companion object {
        @Volatile
        private var INSTANCE: PosDatabase? = null

        fun getDatabase(context: Context): PosDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PosDatabase::class.java,
                    "sunpos_local.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
