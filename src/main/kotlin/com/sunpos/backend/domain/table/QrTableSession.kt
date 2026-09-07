package com.sunpos.backend.domain.table

import java.time.Instant
import java.util.UUID

enum class QrTableSessionStatus {
    ACTIVE,
    CLOSED
}

data class QrTableSession(
    val id: String = UUID.randomUUID().toString(),
    var branchId: String = "",
    var tableId: String = "",
    var tableNumber: String = "",
    var sessionToken: String = "",
    var status: String = QrTableSessionStatus.ACTIVE.name,
    val openedAt: Instant = Instant.now(),
    var closedAt: Instant? = null,
    var openedBy: String? = null,
    var expiresAt: Instant? = null
)

// ── DTOs ──

data class OpenQrSessionRequest(
    val openedBy: String? = null,
    val expiresAt: Instant? = null
)

data class QrSessionResponseDto(
    val id: String,
    val branchId: String,
    val tableId: String,
    val tableNumber: String,
    val sessionToken: String,
    val qrOrderUrl: String,
    val status: String,
    val openedAt: Instant,
    val closedAt: Instant?,
    val openedBy: String?,
    val expiresAt: Instant?
)
