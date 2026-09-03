package com.sunpos.backend.domain.inventory

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class MovementType {
    OPENING,
    PURCHASE,
    SALE_CONSUMPTION,
    TRANSFER_OUT,
    TRANSFER_IN,
    PRODUCTION_IN,
    PRODUCTION_OUT,
    WASTE,
    ADJUSTMENT,
    STOCK_COUNT,
    RETURN
}

enum class TransferStatus {
    REQUESTED,
    APPROVED,
    SHIPPED,
    RECEIVED,
    CANCELLED
}

enum class CountStatus {
    DRAFT,
    REVIEW,
    APPROVED,
    CANCELLED
}

class Warehouse(
    val id: String = UUID.randomUUID().toString(),
    var branchId: String? = null,
    var name: String = "",
    var code: String = "",
    var isCentral: Boolean = false,
    var isActive: Boolean = true,
    val createdAt: Instant = Instant.now()
)

class InventoryItem(
    val id: String = UUID.randomUUID().toString(),
    var sku: String = "",
    var name: String = "",
    var categoryName: String = "GENERAL",
    var baseUnit: String = "g",
    var receivingUnit: String = "kg",
    var receivingUnitFactor: BigDecimal = BigDecimal("1000.0000"),
    var dispenseUnit: String = "g",
    var dispenseUnitFactor: BigDecimal = BigDecimal("1.0000"),
    var unit: String = "kg",
    var conversionFactor: BigDecimal = BigDecimal("1000.0000"),
    var minStockAlert: BigDecimal = BigDecimal.ZERO,
    var countFrequency: String = "DAILY",
    var countFrequencies: List<String> = listOf("DAILY"),
    var brandId: String? = null,
    var isActive: Boolean = true,
    val createdAt: Instant = Instant.now()
)

class UnitOfMeasure(
    val id: String = UUID.randomUUID().toString(),
    var code: String = "",
    var name: String = "",
    var category: String = "COUNT",
    var isBaseUnit: Boolean = false,
    var baseUnitCode: String? = null,
    var conversionFactor: BigDecimal = BigDecimal.ONE,
    var description: String? = null,
    var isActive: Boolean = true,
    val createdAt: Instant = Instant.now()
)

class InventoryStock(
    val id: String = UUID.randomUUID().toString(),
    var warehouseId: String = "",
    var inventoryItemId: String = "",
    var quantity: BigDecimal = BigDecimal.ZERO,
    var weightedAverageCost: BigDecimal = BigDecimal.ZERO,
    var updatedAt: Instant = Instant.now()
)

class StockMovement(
    val id: String = UUID.randomUUID().toString(),
    var warehouseId: String = "",
    var inventoryItemId: String = "",
    var quantity: BigDecimal = BigDecimal.ZERO,
    var unit: String = "",
    var unitCost: BigDecimal = BigDecimal.ZERO,
    var totalCost: BigDecimal = BigDecimal.ZERO,
    var movementType: MovementType = MovementType.OPENING,
    var referenceType: String? = null,
    var referenceId: String? = null,
    var createdBy: String? = null,
    var businessDayId: String? = null,
    val createdAt: Instant = Instant.now()
)

class StockTransfer(
    val id: String = UUID.randomUUID().toString(),
    var sourceWarehouseId: String = "",
    var targetWarehouseId: String = "",
    var transferNumber: String = "",
    var status: TransferStatus = TransferStatus.REQUESTED,
    var shippedAt: Instant? = null,
    var receivedAt: Instant? = null,
    var createdBy: String? = null,
    val createdAt: Instant = Instant.now()
)

class StockTransferItem(
    val id: String = UUID.randomUUID().toString(),
    var transferId: String = "",
    var inventoryItemId: String = "",
    var quantity: BigDecimal = BigDecimal.ZERO,
    var unit: String = "",
    var unitCost: BigDecimal = BigDecimal.ZERO
)

class StockCount(
    val id: String = UUID.randomUUID().toString(),
    var warehouseId: String = "",
    var countNumber: String = "",
    var status: CountStatus = CountStatus.DRAFT,
    val countedAt: Instant = Instant.now(),
    var approvedBy: String? = null,
    var notes: String? = null
)

class StockCountItem(
    val id: String = UUID.randomUUID().toString(),
    var countId: String = "",
    var inventoryItemId: String = "",
    var systemQty: BigDecimal = BigDecimal.ZERO,
    var actualQty: BigDecimal = BigDecimal.ZERO,
    var varianceQty: BigDecimal = BigDecimal.ZERO,
    var unitCost: BigDecimal = BigDecimal.ZERO
)

class StockWaste(
    val id: String = UUID.randomUUID().toString(),
    var warehouseId: String = "",
    var inventoryItemId: String = "",
    var quantity: BigDecimal = BigDecimal.ZERO,
    var unit: String = "",
    var unitCost: BigDecimal = BigDecimal.ZERO,
    var totalCost: BigDecimal = BigDecimal.ZERO,
    var reason: String? = null,
    var approvedBy: String? = null,
    val createdAt: Instant = Instant.now()
)

// DTOs
data class PurchaseReceiveDto(
    val warehouseId: String = "",
    val inventoryItemId: String = "",
    val quantity: BigDecimal = BigDecimal.ZERO,
    val unit: String = "",
    val unitCost: BigDecimal = BigDecimal.ZERO,
    val createdBy: String? = null
)

data class CreateTransferDto(
    val sourceWarehouseId: String = "",
    val targetWarehouseId: String = "",
    val items: List<TransferItemDto> = emptyList(),
    val createdBy: String? = null
)

data class TransferItemDto(
    val inventoryItemId: String = "",
    val quantity: BigDecimal = BigDecimal.ZERO,
    val unit: String = ""
)

data class StockCountCreateDto(
    val warehouseId: String = "",
    val notes: String? = null,
    val items: List<StockCountItemDto> = emptyList(),
    val approvedBy: String? = null
)

data class StockCountItemDto(
    val inventoryItemId: String = "",
    val actualQty: BigDecimal = BigDecimal.ZERO
)

data class StockWasteCreateDto(
    val warehouseId: String = "",
    val inventoryItemId: String = "",
    val quantity: BigDecimal = BigDecimal.ZERO,
    val unit: String = "",
    val reason: String? = null,
    val approvedBy: String? = null
)
