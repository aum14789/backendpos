package sun.clientpos.sync

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import sun.clientpos.data.local.dao.MenuDao
import sun.clientpos.data.local.dao.SyncOutboxDao
import sun.clientpos.data.local.entity.*
import sun.clientpos.data.remote.RetrofitClient
import sun.clientpos.data.remote.SyncEventDto
import sun.clientpos.data.remote.SyncPushRequest
import java.text.SimpleDateFormat
import java.util.*

enum class ConnectionStatus {
    ONLINE,
    OFFLINE
}

enum class POSSyncState {
    SYNCED,
    SYNCING,
    PENDING_CHANGES,
    SYNC_ERROR
}

/**
 * Manages the offline sync lifecycle for the POS device.
 *
 * Responsibilities:
 *   - Track connection status and sync state
 *   - Enqueue outbox events for offline durability
 *   - Push pending events to backend via Retrofit
 *   - Pull master data delta from backend and update local Room
 */
class OfflineSyncManager(
    private val outboxDao: SyncOutboxDao,
    private val menuDao: MenuDao? = null,
    private val deviceCapabilityDao: sun.clientpos.data.local.dao.DeviceCapabilityDao? = null
) {
    companion object {
        private const val TAG = "OfflineSyncManager"
        private const val BATCH_SIZE = 50
    }

    private val _connectionStatus = MutableStateFlow(ConnectionStatus.ONLINE)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _posSyncState = MutableStateFlow(POSSyncState.SYNCED)
    val posSyncState: StateFlow<POSSyncState> = _posSyncState.asStateFlow()

    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /** Last successful pull timestamp (ISO-8601). Null = never pulled. */
    private var lastPullTimestamp: String? = null

    fun setConnectionStatus(status: ConnectionStatus) {
        _connectionStatus.value = status
        if (status == ConnectionStatus.OFFLINE) {
            _posSyncState.value = POSSyncState.PENDING_CHANGES
        }
    }

    /**
     * Enqueue a sync outbox event for offline durability.
     */
    suspend fun enqueueEvent(
        aggregateType: String,
        aggregateId: String,
        eventType: String,
        payload: String,
        deviceId: String,
        branchId: String
    ): SyncOutboxEntity {
        val event = SyncOutboxEntity(
            aggregateType = aggregateType,
            aggregateId = aggregateId,
            eventType = eventType,
            payload = payload,
            deviceId = deviceId,
            branchId = branchId,
            status = SyncStatus.PENDING
        )
        outboxDao.insertEvent(event)
        _posSyncState.value = POSSyncState.PENDING_CHANGES
        return event
    }

    /**
     * Push pending outbox events to the backend via Retrofit.
     * Returns the sync status after the operation.
     */
    suspend fun pushPendingEventsBatch(): SyncStatus {
        val pendingEvents = outboxDao.getPendingEvents(limit = BATCH_SIZE)
        if (pendingEvents.isEmpty()) {
            _posSyncState.value = POSSyncState.SYNCED
            return SyncStatus.SYNCED
        }

        if (_connectionStatus.value == ConnectionStatus.OFFLINE) {
            _posSyncState.value = POSSyncState.SYNC_ERROR
            for (event in pendingEvents) {
                outboxDao.markFailed(event.eventId, "Network connection unavailable (Offline Mode)")
            }
            return SyncStatus.FAILED
        }

        _posSyncState.value = POSSyncState.SYNCING

        return try {
            val syncApiService = RetrofitClient.getSyncApiService()

            val eventDtos = pendingEvents.map { event ->
                SyncEventDto(
                    eventId = event.eventId,
                    aggregateType = event.aggregateType,
                    aggregateId = event.aggregateId,
                    eventType = event.eventType,
                    deviceId = event.deviceId,
                    branchId = event.branchId,
                    payload = event.payload,
                    createdAt = isoDateFormat.format(Date(event.createdAt))
                )
            }

            val request = SyncPushRequest(events = eventDtos)
            val response = syncApiService.pushEvents(request)

            if (response.success && response.data != null) {
                val result = response.data
                val allAcknowledged = result.processedEventIds + result.duplicateEventIds
                if (allAcknowledged.isNotEmpty()) {
                    outboxDao.updateStatus(allAcknowledged, SyncStatus.SYNCED)
                }

                val remaining = outboxDao.getPendingEvents(limit = 1)
                _posSyncState.value = if (remaining.isEmpty()) POSSyncState.SYNCED else POSSyncState.PENDING_CHANGES
                Log.d(TAG, "Push successful: ${result.processedEventIds.size} processed, ${result.duplicateEventIds.size} duplicates")
                SyncStatus.SYNCED
            } else {
                _posSyncState.value = POSSyncState.SYNC_ERROR
                Log.w(TAG, "Push failed: ${response.error?.message}")
                SyncStatus.FAILED
            }
        } catch (e: Exception) {
            _posSyncState.value = POSSyncState.SYNC_ERROR
            Log.e(TAG, "Push exception: ${e.message}", e)
            for (event in pendingEvents) {
                outboxDao.markFailed(event.eventId, e.message ?: "Network error")
            }
            SyncStatus.FAILED
        }
    }

    /**
     * Pull master data delta from the backend and update local Room database.
     * This is the "Cloud Authority" pull for master data (catalog, prices, etc.).
     *
     * @param branchId The branch to pull data for
     * @return true if pull was successful
     */
    suspend fun pullMasterDataDelta(branchId: String, deviceId: String? = null): Boolean {
        if (_connectionStatus.value == ConnectionStatus.OFFLINE) {
            Log.d(TAG, "Cannot pull: device is offline")
            return false
        }

        return try {
            val syncApiService = RetrofitClient.getSyncApiService()
            val response = syncApiService.pullDelta(branchId, lastPullTimestamp, deviceId)

            if (response.success && response.data != null) {
                val delta = response.data

                // Update local Room with pulled menu categories
                if (delta.categories.isNotEmpty()) {
                    val categoryEntities = delta.categories.map { cat ->
                        RoomMenuCategoryEntity(
                            categoryId = cat.id,
                            branchId = cat.branchId,
                            name = cat.name,
                            description = cat.description,
                            sortOrder = cat.sortOrder,
                            isActive = cat.isActive
                        )
                    }
                    menuDao?.insertCategories(categoryEntities)
                    Log.d(TAG, "Pulled ${categoryEntities.size} categories")
                }

                // Update local Room with pulled menu items
                // Convert backend decimal price to satang (Long)
                if (delta.menuItems.isNotEmpty()) {
                    val menuItemEntities = delta.menuItems.map { item ->
                        RoomMenuItemEntity(
                            itemId = item.id,
                            branchId = item.branchId,
                            categoryId = item.categoryId,
                            name = item.name,
                            description = item.description,
                            sku = item.sku,
                            basePrice = Math.round(item.basePrice * 100), // Convert baht → satang
                            availability = item.availability,
                            imageUrl = item.imageUrl,
                            sortOrder = item.sortOrder,
                            isActive = item.isActive
                        )
                    }
                    menuDao?.insertMenuItems(menuItemEntities)
                    Log.d(TAG, "Pulled ${menuItemEntities.size} menu items")
                }

                // Update local Room with pulled device capabilities
                if (deviceId != null && delta.deviceCapabilities.isNotEmpty() && deviceCapabilityDao != null) {
                    val capabilityEntities = delta.deviceCapabilities.map { cap ->
                        DeviceCapabilityEntity(
                            deviceId = deviceId,
                            capability = cap,
                            isActive = true
                        )
                    }
                    deviceCapabilityDao.replaceCapabilities(deviceId, capabilityEntities)
                    Log.d(TAG, "Pulled ${capabilityEntities.size} device capabilities for device $deviceId")
                }

                // Update last pull timestamp for next delta
                lastPullTimestamp = delta.serverTime
                Log.d(TAG, "Pull complete. Server time: ${delta.serverTime}")
                true
            } else {
                Log.w(TAG, "Pull failed: ${response.error?.message}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pull exception: ${e.message}", e)
            false
        }
    }

    /**
     * Purge events that have been successfully synced.
     * Call periodically to keep the outbox table lean.
     */
    suspend fun purgeSyncedEvents() {
        outboxDao.purgeSyncedEvents()
        Log.d(TAG, "Purged synced events")
    }
}
