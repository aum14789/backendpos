package sun.clientpos

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import sun.clientpos.data.local.dao.SyncOutboxDao
import sun.clientpos.data.local.entity.SyncOutboxEntity
import sun.clientpos.data.local.entity.SyncStatus
import sun.clientpos.sync.ConnectionStatus
import sun.clientpos.sync.OfflineSyncManager
import sun.clientpos.sync.POSSyncState

class FakeSyncOutboxDao : SyncOutboxDao {
    private val events = mutableListOf<SyncOutboxEntity>()

    override suspend fun insertEvent(event: SyncOutboxEntity) {
        events.removeAll { it.eventId == event.eventId }
        events.add(event)
    }

    override suspend fun insertEvents(events: List<SyncOutboxEntity>) {
        events.forEach { insertEvent(it) }
    }

    override suspend fun getPendingEvents(limit: Int): List<SyncOutboxEntity> {
        return events.filter { it.status == SyncStatus.PENDING }.take(limit)
    }

    override fun observePendingEvents(): kotlinx.coroutines.flow.Flow<List<SyncOutboxEntity>> {
        return kotlinx.coroutines.flow.flowOf(events.filter { it.status == SyncStatus.PENDING })
    }

    override suspend fun updateStatus(eventIds: List<String>, status: SyncStatus) {
        for (i in events.indices) {
            if (events[i].eventId in eventIds) {
                events[i] = events[i].copy(status = status)
            }
        }
    }

    override suspend fun markFailed(eventId: String, error: String) {
        for (i in events.indices) {
            if (events[i].eventId == eventId) {
                events[i] = events[i].copy(
                    status = SyncStatus.FAILED,
                    retryCount = events[i].retryCount + 1,
                    lastError = error
                )
            }
        }
    }

    override suspend fun purgeSyncedEvents() {
        events.removeAll { it.status == SyncStatus.SYNCED }
    }
}

class OfflineSyncManagerTest {

    private lateinit var fakeDao: FakeSyncOutboxDao
    private lateinit var syncManager: OfflineSyncManager

    @Before
    fun setUp() {
        fakeDao = FakeSyncOutboxDao()
        syncManager = OfflineSyncManager(fakeDao)
    }

    @Test
    fun testOfflineEnqueueAndOnlineSyncBatching() = runBlocking {
        // 1. Initially Online
        assertEquals(ConnectionStatus.ONLINE, syncManager.connectionStatus.value)

        // 2. Enqueue Offline Order Event
        val event = syncManager.enqueueEvent(
            aggregateType = "ORDER",
            aggregateId = "ord-offline-999",
            eventType = "ORDER_CREATED",
            payload = "{\"amountSatang\": 35000}",
            deviceId = "pos-device-001",
            branchId = "branch-001"
        )
        assertNotNull(event.eventId)
        assertEquals(POSSyncState.PENDING_CHANGES, syncManager.posSyncState.value)

        // 3. Test Offline Network Mode
        syncManager.setConnectionStatus(ConnectionStatus.OFFLINE)
        val offlineResult = syncManager.pushPendingEventsBatch()
        assertEquals(SyncStatus.FAILED, offlineResult)
        assertEquals(POSSyncState.SYNC_ERROR, syncManager.posSyncState.value)

        // 4. Test Reconnect & Pending Events State
        syncManager.setConnectionStatus(ConnectionStatus.ONLINE)
        assertEquals(ConnectionStatus.ONLINE, syncManager.connectionStatus.value)
    }
}
