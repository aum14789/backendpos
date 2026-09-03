package com.sunpos.backend.domain.table

import java.time.Instant
import java.util.UUID

class Zone(
    val id: String = UUID.randomUUID().toString(),
    var branchId: String = "",
    var name: String = "",
    var zoneType: String = "DINE_IN", // DINE_IN, BUFFET
    var sortOrder: Int = 0,
    var isActive: Boolean = true,
    val createdAt: Instant = Instant.now()
)

class TableType(
    val id: String = UUID.randomUUID().toString(),
    var branchId: String = "",
    var name: String = "",
    var code: String = "",
    var isDefault: Boolean = false,
    val createdAt: Instant = Instant.now()
)

class RestaurantTable(
    val id: String = UUID.randomUUID().toString(),
    var branchId: String = "",
    var zoneId: String? = null,
    var tableTypeId: String? = null,
    var nameNumber: String = "",
    var capacity: Int = 4,
    var status: String = "AVAILABLE", // AVAILABLE, OCCUPIED, WAITING_PAYMENT, RESERVED, CLEANING, OUT_OF_SERVICE
    var isActive: Boolean = true,
    val createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now(),
    var version: Long = 0
)

class TableSession(
    val id: String = UUID.randomUUID().toString(),
    var tableId: String = "",
    var branchId: String = "",
    val openedAt: Instant = Instant.now(),
    var closedAt: Instant? = null,
    var status: String = "ACTIVE", // ACTIVE, CLOSED
    var openedBy: String? = null,
    var closedBy: String? = null
)

// DTOs
data class ZoneCreateDto(
    val branchId: String = "",
    val name: String = "",
    val zoneType: String = "DINE_IN",
    val sortOrder: Int = 0
)

data class ZoneUpdateDto(
    val name: String = "",
    val zoneType: String = "DINE_IN",
    val sortOrder: Int = 0,
    val isActive: Boolean = true
)

data class TableCreateDto(
    val branchId: String = "",
    val zoneId: String? = null,
    val tableTypeId: String? = null,
    val nameNumber: String = "",
    val capacity: Int = 4,
    val isActive: Boolean = true
)

data class TableUpdateDto(
    val zoneId: String? = null,
    val tableTypeId: String? = null,
    val nameNumber: String = "",
    val capacity: Int = 4,
    val isActive: Boolean = true
)

data class OpenSessionDto(
    val tableId: String = "",
    val branchId: String = "",
    val openedBy: String? = null
)
