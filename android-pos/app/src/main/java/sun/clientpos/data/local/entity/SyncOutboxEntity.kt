package sun.clientpos.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class SyncStatus {
    PENDING,
    SYNCED,
    FAILED
}

@Entity(tableName = "sync_outbox")
data class SyncOutboxEntity(
    @PrimaryKey
    val eventId: String = UUID.randomUUID().toString(),
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val payload: String,
    val deviceId: String,
    val branchId: String,
    val createdAt: Long = System.currentTimeMillis(),
    val status: SyncStatus = SyncStatus.PENDING,
    val retryCount: Int = 0,
    val lastError: String? = null
)
