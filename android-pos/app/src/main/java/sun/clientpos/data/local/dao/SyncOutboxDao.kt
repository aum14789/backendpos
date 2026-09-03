package sun.clientpos.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import sun.clientpos.data.local.entity.SyncOutboxEntity
import sun.clientpos.data.local.entity.SyncStatus

@Dao
interface SyncOutboxDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: SyncOutboxEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<SyncOutboxEntity>)

    @Query("SELECT * FROM sync_outbox WHERE status = 'PENDING' ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getPendingEvents(limit: Int = 50): List<SyncOutboxEntity>

    @Query("SELECT * FROM sync_outbox WHERE status = 'PENDING'")
    fun observePendingEvents(): Flow<List<SyncOutboxEntity>>

    @Query("UPDATE sync_outbox SET status = :status WHERE eventId IN (:eventIds)")
    suspend fun updateStatus(eventIds: List<String>, status: SyncStatus)

    @Query("UPDATE sync_outbox SET status = 'FAILED', retryCount = retryCount + 1, lastError = :error WHERE eventId = :eventId")
    suspend fun markFailed(eventId: String, error: String)

    @Query("DELETE FROM sync_outbox WHERE status = 'SYNCED'")
    suspend fun purgeSyncedEvents()
}
