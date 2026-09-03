package sun.clientpos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "room_zones")
data class RoomZoneEntity(
    @PrimaryKey val zoneId: String = UUID.randomUUID().toString(),
    val branchId: String,
    val name: String,
    val zoneType: String = "DINE_IN", // DINE_IN, BUFFET
    val sortOrder: Int = 0,
    val isActive: Boolean = true
)

@Entity(tableName = "room_table_types")
data class RoomTableTypeEntity(
    @PrimaryKey val tableTypeId: String = UUID.randomUUID().toString(),
    val branchId: String,
    val name: String,
    val code: String,
    val isDefault: Boolean = false
)

@Entity(tableName = "room_tables")
data class RoomTableEntity(
    @PrimaryKey val tableId: String = UUID.randomUUID().toString(),
    val branchId: String,
    val zoneId: String?,
    val tableTypeId: String?,
    val nameNumber: String,
    val capacity: Int = 4,
    val status: String = "AVAILABLE", // AVAILABLE, OCCUPIED, WAITING_PAYMENT, RESERVED, CLEANING, OUT_OF_SERVICE
    val isActive: Boolean = true
)

@Entity(tableName = "room_table_sessions")
data class RoomTableSessionEntity(
    @PrimaryKey val sessionId: String = UUID.randomUUID().toString(),
    val tableId: String,
    val branchId: String,
    val openedAt: Long = System.currentTimeMillis(),
    val closedAt: Long? = null,
    val status: String = "ACTIVE",
    val openedBy: String? = null,
    val closedBy: String? = null
)

@Entity(tableName = "room_menu_categories")
data class RoomMenuCategoryEntity(
    @PrimaryKey val categoryId: String = UUID.randomUUID().toString(),
    val branchId: String,
    val name: String,
    val description: String?,
    val sortOrder: Int = 0,
    val isActive: Boolean = true
)

/**
 * Menu item entity.
 * basePrice is stored in satang (minor units). 1 Baht = 100 Satang.
 * e.g. ฿120.00 = 12000L
 */
@Entity(tableName = "room_menu_items")
data class RoomMenuItemEntity(
    @PrimaryKey val itemId: String = UUID.randomUUID().toString(),
    val branchId: String,
    val categoryId: String,
    val name: String,
    val description: String?,
    val sku: String?,
    val basePrice: Long = 0L, // satang (minor units)
    val availability: String = "AVAILABLE", // AVAILABLE, SOLD_OUT, DISABLED
    val imageUrl: String? = null,
    val sortOrder: Int = 0,
    val isActive: Boolean = true
)

@Entity(tableName = "room_modifier_groups")
data class RoomModifierGroupEntity(
    @PrimaryKey val modifierGroupId: String = UUID.randomUUID().toString(),
    val branchId: String,
    val name: String,
    val minSelection: Int = 0,
    val maxSelection: Int = 1,
    val isRequired: Boolean = false
)

/**
 * Modifier entity.
 * price is stored in satang (minor units).
 */
@Entity(tableName = "room_modifiers")
data class RoomModifierEntity(
    @PrimaryKey val modifierId: String = UUID.randomUUID().toString(),
    val modifierGroupId: String,
    val name: String,
    val price: Long = 0L, // satang (minor units)
    val isActive: Boolean = true
)

/**
 * Customer entity for offline CRM.
 */
@Entity(tableName = "room_customers")
data class RoomCustomerEntity(
    @PrimaryKey val customerId: String = UUID.randomUUID().toString(),
    val displayName: String,
    val phone: String,
    val memberId: String? = null,
    val lineId: String? = null,
    val email: String? = null,
    val tierCode: String = "SILVER",
    val tierName: String = "สมาชิกทั่วไป (Silver)",
    val discountPercent: Double = 0.0,
    val pointsBalance: Double = 0.0,
    val customerGroup: String = "GENERAL",
    val isSynced: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Order entity.
 * totalAmount is stored in satang (minor units).
 */
@Entity(tableName = "room_orders")
data class RoomOrderEntity(
    @PrimaryKey val orderId: String = UUID.randomUUID().toString(),
    val branchId: String,
    val customerId: String? = null,
    val tableId: String?,
    val tableSessionId: String?,
    val orderNumber: String,
    val orderType: String = "DINE_IN", // DINE_IN, TAKEAWAY, DELIVERY, BUFFET
    val channel: String = "POS", // POS, QR, LINE, WEB, DELIVERY
    val status: String = "OPEN", // OPEN, CONFIRMED, IN_KITCHEN, READY, SERVED, COMPLETED, CANCELLED, VOIDED
    val kitchenStatus: String = "NOT_SENT",
    val buffetSessionId: String? = null, // links to RoomBuffetSessionEntity for BUFFET orders
    val subtotalAmount: Long = 0L, // satang
    val discountAmount: Long = 0L, // satang
    val serviceChargeAmount: Long = 0L, // satang
    val taxAmount: Long = 0L, // satang
    val totalAmount: Long = 0L, // satang — grand total after discount + service charge + tax
    val createdBy: String?,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Order item entity.
 * All price fields stored in satang (minor units).
 * quantity is Int (whole units for POS restaurant items).
 */
@Entity(tableName = "room_order_items")
data class RoomOrderItemEntity(
    @PrimaryKey val orderItemId: String = UUID.randomUUID().toString(),
    val orderId: String,
    val menuItemId: String,
    val nameSnapshot: String,
    val unitPriceSnapshot: Long, // satang
    val quantity: Int = 1,
    val notes: String?,
    val subtotal: Long = 0L, // satang — (unitPrice + modifiers) * quantity
    val kitchenStatus: String = "NOT_SENT"
)

/**
 * Order item modifier entity.
 * priceSnapshot stored in satang (minor units).
 */
@Entity(tableName = "room_order_item_modifiers")
data class RoomOrderItemModifierEntity(
    @PrimaryKey val orderItemModifierId: String = UUID.randomUUID().toString(),
    val orderItemId: String,
    val modifierId: String,
    val nameSnapshot: String,
    val priceSnapshot: Long // satang
)
