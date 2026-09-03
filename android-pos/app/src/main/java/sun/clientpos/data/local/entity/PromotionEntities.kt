package sun.clientpos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Cached promotion definition from Backend.
 * All monetary amounts stored in satang (minor units).
 */
@Entity(tableName = "room_promotions")
data class RoomPromotionEntity(
    @PrimaryKey val promotionId: String = UUID.randomUUID().toString(),
    val code: String,
    val name: String,
    val description: String? = null,
    val promoType: String, // PERCENTAGE, FIXED_AMOUNT, BUY_1_GET_1, SET_PRICE
    val priority: Int = 0,
    val isActive: Boolean = true,
    val startAt: Long = 0L,
    val endAt: Long = Long.MAX_VALUE,
    val branchId: String? = null,
    val brandId: String? = null,
    val discountRate: Double = 0.0, // e.g. 10.0 for 10%
    val discountAmount: Long = 0L, // satang
    val minOrderAmount: Long = 0L, // satang
    val stackingPolicy: String = "STACKABLE" // STACKABLE, NON_STACKABLE
)

/**
 * Eligible products for a promotion.
 */
@Entity(
    tableName = "room_promotion_eligible_products",
    primaryKeys = ["promotionId", "menuItemId"]
)
data class RoomPromotionEligibleProductEntity(
    val promotionId: String,
    val menuItemId: String
)

/**
 * Snapshot of promotions applied to a completed order.
 * Immutable financial audit record.
 */
@Entity(tableName = "room_order_applied_promotions")
data class RoomOrderAppliedPromotionEntity(
    @PrimaryKey val appliedId: String = UUID.randomUUID().toString(),
    val orderId: String,
    val promotionId: String,
    val promotionCode: String,
    val promotionName: String,
    val discountAmountSatang: Long, // satang
    val appliedAt: Long = System.currentTimeMillis()
)
