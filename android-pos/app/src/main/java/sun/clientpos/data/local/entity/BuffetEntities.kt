package sun.clientpos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/**
 * Cached buffet promotion tier from backend.
 * Prices stored in satang (minor units).
 */
@Entity(tableName = "room_buffet_tiers")
data class RoomBuffetTierEntity(
    @PrimaryKey val tierId: String = UUID.randomUUID().toString(),
    val promotionId: String,
    val name: String,
    val adultPrice: Long = 0L, // satang
    val childPrice: Long = 0L, // satang
    val timeLimitMinutes: Int = 90,
    val brandId: String?,
    val branchId: String?,
    val isActive: Boolean = true
)

/**
 * Join table: which menu items are eligible for a buffet tier.
 */
@Entity(
    tableName = "room_buffet_tier_menu_items",
    primaryKeys = ["buffetTierId", "menuItemId"]
)
data class RoomBuffetTierMenuItemEntity(
    val buffetTierId: String,
    val menuItemId: String
)

/**
 * Active buffet session linked to an order.
 * Prices stored in satang (minor units).
 */
@Entity(tableName = "room_buffet_sessions")
data class RoomBuffetSessionEntity(
    @PrimaryKey val sessionId: String = UUID.randomUUID().toString(),
    val orderId: String,
    val branchId: String,
    val buffetTierId: String,
    val adultCount: Int = 1,
    val childCount: Int = 0,
    val adultPriceSnapshot: Long, // satang — frozen at session start
    val childPriceSnapshot: Long, // satang — frozen at session start
    val timeLimitMinutes: Int = 90,
    val startedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long, // epoch millis
    val closedAt: Long? = null,
    val status: String = "ACTIVE", // ACTIVE, TIME_WARNING, EXPIRED, CLOSED
    val createdBy: String?
) {
    /** Total buffet charge in satang */
    fun totalChargeSatang(): Long =
        (adultPriceSnapshot * adultCount) + (childPriceSnapshot * childCount)

    /** Remaining time in milliseconds (0 if expired) */
    fun remainingMillis(): Long =
        (expiresAt - System.currentTimeMillis()).coerceAtLeast(0)

    /** Remaining time in minutes */
    fun remainingMinutes(): Int =
        (remainingMillis() / 60_000).toInt()

    /** Check if the session time has expired */
    fun isExpired(): Boolean =
        System.currentTimeMillis() >= expiresAt
}
