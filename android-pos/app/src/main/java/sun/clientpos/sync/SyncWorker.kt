package sun.clientpos.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import sun.clientpos.data.local.PosDatabase
import sun.clientpos.data.local.entity.SyncStatus
import sun.clientpos.data.remote.RetrofitClient
import sun.clientpos.data.remote.SyncEventDto
import sun.clientpos.data.remote.SyncPushRequest
import java.text.SimpleDateFormat
import java.util.*

/**
 * WorkManager worker that pushes pending outbox events to the backend.
 *
 * Behavior:
 *   1. Fetches up to 50 PENDING events from Room sync_outbox
 *   2. Converts them to SyncEventDto and batches into a SyncPushRequest
 *   3. POSTs to /api/v1/sync/push via Retrofit
 *   4. Marks successfully processed events as SYNCED
 *   5. Marks duplicates as SYNCED (server already has them)
 *   6. On failure: retries with WorkManager's exponential backoff (up to 5 attempts)
 */
class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "SyncWorker"
        private const val BATCH_SIZE = 50
        private const val MAX_RETRIES = 5
    }

    private val db = PosDatabase.getDatabase(appContext)
    private val outboxDao = db.syncOutboxDao()
    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override suspend fun doWork(): Result {
        val pendingEvents = outboxDao.getPendingEvents(limit = BATCH_SIZE)
        if (pendingEvents.isEmpty()) {
            Log.d(TAG, "No pending events to sync")
            return Result.success()
        }

        Log.d(TAG, "Pushing ${pendingEvents.size} pending events to backend")

        return try {
            val syncApiService = RetrofitClient.getSyncApiService()

            // Convert Room entities to backend DTOs
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

                // Mark processed events as SYNCED
                if (result.processedEventIds.isNotEmpty()) {
                    outboxDao.updateStatus(result.processedEventIds, SyncStatus.SYNCED)
                    Log.d(TAG, "Synced ${result.processedEventIds.size} events")
                }

                // Mark duplicates as SYNCED too (server already has them)
                if (result.duplicateEventIds.isNotEmpty()) {
                    outboxDao.updateStatus(result.duplicateEventIds, SyncStatus.SYNCED)
                    Log.d(TAG, "Acknowledged ${result.duplicateEventIds.size} duplicate events")
                }

                Result.success()
            } else {
                val errorMsg = response.error?.message ?: "Unknown server error"
                Log.w(TAG, "Sync push failed: $errorMsg")
                handleRetry(pendingEvents.map { it.eventId }, errorMsg)
            }
        } catch (e: Exception) {
            val errorMsg = e.message ?: "Network error"
            Log.e(TAG, "Sync push exception: $errorMsg", e)
            handleRetry(pendingEvents.map { it.eventId }, errorMsg)
        }
    }

    private suspend fun handleRetry(eventIds: List<String>, errorMsg: String): Result {
        // Mark individual events as failed with error message
        for (eventId in eventIds) {
            outboxDao.markFailed(eventId, errorMsg)
        }

        return if (runAttemptCount < MAX_RETRIES) {
            Log.d(TAG, "Will retry (attempt ${runAttemptCount + 1}/$MAX_RETRIES)")
            Result.retry()
        } else {
            Log.e(TAG, "Max retries ($MAX_RETRIES) reached. Giving up on this batch.")
            Result.failure()
        }
    }
}
