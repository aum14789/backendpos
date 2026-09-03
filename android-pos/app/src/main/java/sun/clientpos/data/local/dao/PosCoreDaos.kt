package sun.clientpos.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import sun.clientpos.data.local.entity.*

@Dao
interface TableDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTables(tables: List<RoomTableEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertZones(zones: List<RoomZoneEntity>)

    @Query("SELECT COUNT(*) FROM room_zones")
    suspend fun getZoneCount(): Int

    @Query("SELECT COUNT(*) FROM room_tables")
    suspend fun getTableCount(): Int

    @Query("SELECT * FROM room_zones WHERE branchId = :branchId ORDER BY sortOrder ASC")
    fun observeZones(branchId: String): Flow<List<RoomZoneEntity>>

    @Query("SELECT * FROM room_tables WHERE branchId = :branchId")
    fun observeTablesByBranch(branchId: String): Flow<List<RoomTableEntity>>

    @Query("SELECT * FROM room_tables WHERE tableId = :tableId")
    suspend fun getTableById(tableId: String): RoomTableEntity?

    @Query("UPDATE room_tables SET status = :status WHERE tableId = :tableId")
    suspend fun updateTableStatus(tableId: String, status: String)
}

@Dao
interface TableSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: RoomTableSessionEntity)

    @Query("SELECT * FROM room_table_sessions WHERE tableId = :tableId AND status = 'ACTIVE' LIMIT 1")
    suspend fun getActiveSession(tableId: String): RoomTableSessionEntity?

    @Query("UPDATE room_table_sessions SET status = 'CLOSED', closedAt = :closedAt, closedBy = :closedBy WHERE sessionId = :sessionId")
    suspend fun closeSession(sessionId: String, closedAt: Long = System.currentTimeMillis(), closedBy: String?)
}

@Dao
interface MenuDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<RoomMenuCategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMenuItems(items: List<RoomMenuItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertModifiers(modifiers: List<RoomModifierEntity>)

    @Query("SELECT COUNT(*) FROM room_menu_categories")
    suspend fun getCategoryCount(): Int

    @Query("SELECT COUNT(*) FROM room_menu_items")
    suspend fun getMenuItemCount(): Int

    @Query("SELECT * FROM room_menu_categories WHERE branchId = :branchId ORDER BY sortOrder ASC")
    fun observeCategories(branchId: String): Flow<List<RoomMenuCategoryEntity>>

    @Query("SELECT * FROM room_menu_items WHERE branchId = :branchId AND isActive = 1 ORDER BY sortOrder ASC")
    fun observeAllMenuItems(branchId: String): Flow<List<RoomMenuItemEntity>>

    @Query("SELECT * FROM room_menu_items WHERE categoryId = :categoryId AND isActive = 1")
    fun observeItemsByCategory(categoryId: String): Flow<List<RoomMenuItemEntity>>

    @Query("SELECT * FROM room_modifiers WHERE modifierGroupId = :groupId")
    suspend fun getModifiersByGroup(groupId: String): List<RoomModifierEntity>
}

@Dao
interface CustomerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: RoomCustomerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomers(customers: List<RoomCustomerEntity>)

    @Query("SELECT * FROM room_customers WHERE customerId = :customerId")
    suspend fun getCustomerById(customerId: String): RoomCustomerEntity?

    @Query("SELECT * FROM room_customers WHERE phone LIKE '%' || :phone || '%' OR displayName LIKE '%' || :phone || '%'")
    suspend fun searchCustomers(phone: String): List<RoomCustomerEntity>

    @Query("SELECT * FROM room_customers ORDER BY displayName ASC")
    fun observeAllCustomers(): Flow<List<RoomCustomerEntity>>
}

@Dao
interface OrderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: RoomOrderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItem(item: RoomOrderItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItemModifiers(modifiers: List<RoomOrderItemModifierEntity>)

    @Query("SELECT * FROM room_orders WHERE orderId = :orderId")
    suspend fun getOrderById(orderId: String): RoomOrderEntity?

    @Query("SELECT * FROM room_orders WHERE branchId = :branchId AND status IN ('OPEN', 'CONFIRMED', 'IN_KITCHEN', 'READY', 'SERVED') ORDER BY createdAt DESC")
    fun observeActiveOrders(branchId: String): Flow<List<RoomOrderEntity>>

    @Query("SELECT * FROM room_order_items WHERE orderId = :orderId")
    suspend fun getOrderItems(orderId: String): List<RoomOrderItemEntity>

    @Query("SELECT * FROM room_order_item_modifiers WHERE orderItemId = :orderItemId")
    suspend fun getItemModifiers(orderItemId: String): List<RoomOrderItemModifierEntity>

    @Query("UPDATE room_orders SET status = :status WHERE orderId = :orderId")
    suspend fun updateOrderStatus(orderId: String, status: String)

    @Query("UPDATE room_orders SET customerId = :customerId WHERE orderId = :orderId")
    suspend fun updateOrderCustomer(orderId: String, customerId: String?)

    @Query("UPDATE room_orders SET kitchenStatus = :kitchenStatus WHERE orderId = :orderId")
    suspend fun updateKitchenStatus(orderId: String, kitchenStatus: String)

    @Query("UPDATE room_orders SET tableId = :tableId, tableSessionId = :tableSessionId WHERE orderId = :orderId")
    suspend fun updateOrderTable(orderId: String, tableId: String?, tableSessionId: String?)

    @Query("UPDATE room_orders SET subtotalAmount = :subtotal, discountAmount = :discount, serviceChargeAmount = :serviceCharge, taxAmount = :tax, totalAmount = :total WHERE orderId = :orderId")
    suspend fun updateOrderAmounts(orderId: String, subtotal: Long, discount: Long, serviceCharge: Long, tax: Long, total: Long)

    @Query("UPDATE room_order_items SET quantity = :quantity, subtotal = :subtotal WHERE orderItemId = :orderItemId")
    suspend fun updateOrderItemQuantity(orderItemId: String, quantity: Int, subtotal: Long)

    @Query("UPDATE room_order_items SET kitchenStatus = :kitchenStatus, notes = :notes WHERE orderItemId = :orderItemId")
    suspend fun updateOrderItemStatus(orderItemId: String, kitchenStatus: String, notes: String?)

    @Query("UPDATE room_order_items SET kitchenStatus = :kitchenStatus WHERE orderItemId = :orderItemId")
    suspend fun updateOrderItemKitchenStatus(orderItemId: String, kitchenStatus: String)

    @Query("DELETE FROM room_order_items WHERE orderItemId = :orderItemId")
    suspend fun deleteOrderItem(orderItemId: String)

    @Query("UPDATE room_order_items SET orderId = :newOrderId WHERE orderItemId IN (:orderItemIds)")
    suspend fun reassignOrderItems(orderItemIds: List<String>, newOrderId: String)
}
