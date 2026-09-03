package sun.clientpos.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import sun.clientpos.data.local.entity.*

@Dao
interface BuffetDao {

    // ── Buffet Tiers / Promotions ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTiers(tiers: List<RoomBuffetTierEntity>)

    @Query("SELECT * FROM room_buffet_tiers WHERE isActive = 1 AND (branchId = :branchId OR branchId IS NULL)")
    fun observeActiveTiersByBranch(branchId: String): Flow<List<RoomBuffetTierEntity>>

    @Query("SELECT COUNT(*) FROM room_buffet_tiers")
    suspend fun getTierCount(): Int

    @Query("SELECT * FROM room_buffet_tiers WHERE isActive = 1 AND (branchId = :branchId OR branchId IS NULL)")
    suspend fun getActiveTiersByBranch(branchId: String): List<RoomBuffetTierEntity>

    @Query("SELECT * FROM room_buffet_tiers WHERE isActive = 1 AND (branchId = :branchId OR (branchId IS NULL AND brandId = :brandId))")
    suspend fun getActiveTiersByBranchAndBrand(branchId: String, brandId: String?): List<RoomBuffetTierEntity>

    @Query("SELECT * FROM room_buffet_tiers WHERE tierId = :tierId")
    suspend fun getTierById(tierId: String): RoomBuffetTierEntity?

    // ── Tier Menu Items ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTierMenuItems(items: List<RoomBuffetTierMenuItemEntity>)

    @Query("SELECT menuItemId FROM room_buffet_tier_menu_items WHERE buffetTierId = :tierId")
    suspend fun getEligibleMenuItemIds(tierId: String): List<String>

    @Query("DELETE FROM room_buffet_tier_menu_items WHERE buffetTierId = :tierId")
    suspend fun deleteTierMenuItems(tierId: String)

    // ── Buffet Sessions ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: RoomBuffetSessionEntity)

    @Query("SELECT * FROM room_buffet_sessions WHERE orderId = :orderId LIMIT 1")
    suspend fun getSessionByOrder(orderId: String): RoomBuffetSessionEntity?

    @Query("SELECT * FROM room_buffet_sessions WHERE branchId = :branchId AND status = 'ACTIVE'")
    fun observeActiveSessions(branchId: String): Flow<List<RoomBuffetSessionEntity>>

    @Query("UPDATE room_buffet_sessions SET status = :status, closedAt = :closedAt WHERE sessionId = :sessionId")
    suspend fun updateSessionStatus(sessionId: String, status: String, closedAt: Long? = null)
}
