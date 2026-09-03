package sun.clientpos.data.remote

import retrofit2.http.*

/**
 * Retrofit interface for SunPOS backend sync endpoints.
 *
 * Endpoints:
 *   POST /api/v1/sync/push    — Push outbox events from POS to cloud
 *   GET  /api/v1/sync/pull     — Pull master data delta from cloud to POS
 *   POST /api/v1/auth/pin-login — Authenticate cashier by PIN
 */
interface SyncApiService {

    /**
     * Push a batch of outbox events to the backend.
     * Backend processes idempotently (duplicate event_ids are acknowledged but not re-processed).
     */
    @POST("sync/push")
    suspend fun pushEvents(
        @Body request: SyncPushRequest
    ): ApiResponse<SyncPushResult>

    /**
     * Pull master data delta (menu items, categories, promotions) updated since [sinceTimestamp].
     * If sinceTimestamp is null, fetches all master data for the branch.
     */
    @GET("sync/pull")
    suspend fun pullDelta(
        @Query("branchId") branchId: String,
        @Query("sinceTimestamp") sinceTimestamp: String? = null,
        @Query("deviceId") deviceId: String? = null
    ): ApiResponse<SyncDeltaResponse>

    /**
     * Authenticate a POS user by PIN code.
     * Returns JWT token + user details for offline session caching.
     */
    @POST("auth/pin-login")
    suspend fun pinLogin(
        @Body request: PinLoginRequest
    ): ApiResponse<PinLoginResponse>
}
