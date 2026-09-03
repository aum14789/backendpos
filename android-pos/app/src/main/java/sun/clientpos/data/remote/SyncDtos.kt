package sun.clientpos.data.remote

import java.time.Instant

/**
 * Standard API response wrapper matching backend's ApiResponse<T>.
 */
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
    val error: ApiError? = null,
    val timestamp: String? = null
)

data class ApiError(
    val code: String,
    val message: String,
    val details: List<Map<String, String>>? = null
)

// ──────────────────────────────────────────────────────
// Sync Push DTOs (POS → Backend)
// ──────────────────────────────────────────────────────

/**
 * Batch of outbox events to push to the backend.
 */
data class SyncPushRequest(
    val events: List<SyncEventDto>
)

/**
 * Single sync event DTO sent to the backend.
 * Mirrors backend's SyncEventDto structure.
 */
data class SyncEventDto(
    val eventId: String,
    val aggregateType: String,
    val aggregateId: String,
    val eventType: String,
    val deviceId: String,
    val branchId: String,
    val payload: String,
    val createdAt: String // ISO-8601 timestamp string
)

/**
 * Result returned by the backend after processing push events.
 */
data class SyncPushResult(
    val processedEventIds: List<String>,
    val duplicateEventIds: List<String>
)

// ──────────────────────────────────────────────────────
// Sync Pull DTOs (Backend → POS)
// ──────────────────────────────────────────────────────

/**
 * Delta master data response from the backend.
 * Contains updated menu items and categories since last sync.
 */
data class SyncDeltaResponse(
    val branchId: String,
    val sinceTimestamp: String,
    val menuItems: List<RemoteMenuItemDto>,
    val categories: List<RemoteCategoryDto>,
    val deviceCapabilities: List<String> = emptyList(),
    val crmPolicy: RemoteCrmPolicyDto = RemoteCrmPolicyDto(),
    val serverTime: String
)

data class RemoteCrmPolicyDto(
    val earnPointsOffline: Boolean = true,
    val redeemPointsOffline: Boolean = false,
    val useCouponOffline: Boolean = false
)

/**
 * Menu item from the backend delta response.
 * Price comes from backend as BigDecimal string — convert to satang on POS side.
 */
data class RemoteMenuItemDto(
    val id: String,
    val branchId: String,
    val categoryId: String,
    val name: String,
    val description: String?,
    val sku: String?,
    val basePrice: Double, // backend sends as decimal, POS converts to satang
    val availability: String,
    val imageUrl: String?,
    val sortOrder: Int,
    val isActive: Boolean
)

/**
 * Category from the backend delta response.
 */
data class RemoteCategoryDto(
    val id: String,
    val branchId: String,
    val name: String,
    val description: String?,
    val sortOrder: Int,
    val isActive: Boolean
)

// ──────────────────────────────────────────────────────
// Auth DTOs
// ──────────────────────────────────────────────────────

data class PinLoginRequest(
    val pinCode: String,
    val deviceId: String? = null,
    val branchId: String? = null,
    val username: String? = null
)

data class PinLoginResponse(
    val token: String,
    val user: RemoteUserDto
)

data class RemoteUserDto(
    val userId: String,
    val username: String,
    val fullName: String,
    val companyId: String,
    val roles: List<String>,
    val permissions: List<String>
)
