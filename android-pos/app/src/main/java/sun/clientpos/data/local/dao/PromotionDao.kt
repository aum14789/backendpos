package sun.clientpos.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import sun.clientpos.data.local.entity.RoomOrderAppliedPromotionEntity
import sun.clientpos.data.local.entity.RoomPromotionEligibleProductEntity
import sun.clientpos.data.local.entity.RoomPromotionEntity

@Dao
interface PromotionDao {

    // ── Promotions ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPromotions(promotions: List<RoomPromotionEntity>)

    @Query("SELECT * FROM room_promotions WHERE (branchId = :branchId OR branchId IS NULL) AND isActive = 1 ORDER BY priority ASC")
    suspend fun getActivePromotions(branchId: String): List<RoomPromotionEntity>

    @Query("SELECT * FROM room_promotions WHERE (branchId = :branchId OR branchId IS NULL) AND isActive = 1 ORDER BY priority ASC")
    fun observeActivePromotions(branchId: String): Flow<List<RoomPromotionEntity>>

    // ── Eligible Products ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEligibleProducts(links: List<RoomPromotionEligibleProductEntity>)

    @Query("SELECT menuItemId FROM room_promotion_eligible_products WHERE promotionId = :promotionId")
    suspend fun getEligibleMenuItemIds(promotionId: String): List<String>

    @Query("SELECT * FROM room_promotion_eligible_products")
    suspend fun getAllEligibleProducts(): List<RoomPromotionEligibleProductEntity>

    // ── Order Applied Promotions ──

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAppliedPromotions(applied: List<RoomOrderAppliedPromotionEntity>)

    @Query("SELECT * FROM room_order_applied_promotions WHERE orderId = :orderId")
    suspend fun getAppliedPromotionsByOrder(orderId: String): List<RoomOrderAppliedPromotionEntity>
}
