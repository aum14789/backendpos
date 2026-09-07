package com.sunpos.backend.domain.catalog

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class ScheduledCatalogStatus {
    DRAFT,
    SCHEDULED,
    ACTIVE,
    EXPIRED,
    CANCELLED
}

class ScheduledCatalog(
    val id: String = UUID.randomUUID().toString(),
    var branchId: String = "",
    var menuItemId: String = "",
    var scheduledPrice: BigDecimal = BigDecimal.ZERO,
    var startAt: Instant = Instant.now(),
    var endAt: Instant = Instant.now().plusSeconds(86400),
    var status: ScheduledCatalogStatus = ScheduledCatalogStatus.SCHEDULED,
    val createdAt: Instant = Instant.now()
)
