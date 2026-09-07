package com.sunpos.backend.domain.purchasing

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class POStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    ORDERED,
    PARTIALLY_RECEIVED,
    RECEIVED,
    CANCELLED
}

class Supplier(
    val id: String = UUID.randomUUID().toString(),
    var code: String = "",
    var name: String = "",
    var contactPerson: String? = null,
    var phone: String? = null,
    var email: String? = null,
    var address: String? = null,
    var paymentTerms: String = "Net 30",
    var taxId: String? = null,
    var isActive: Boolean = true,
    val createdAt: Instant = Instant.now()
)

class PurchaseOrder(
    val id: String = UUID.randomUUID().toString(),
    var poNumber: String = "",
    var supplierId: String = "",
    var warehouseId: String = "",
    var status: POStatus = POStatus.DRAFT,
    var totalExpectedAmount: BigDecimal = BigDecimal.ZERO,
    var expectedDate: Instant? = null,
    var notes: String? = null,
    var createdBy: String? = null,
    var approvedBy: String? = null,
    val createdAt: Instant = Instant.now()
)

class PurchaseOrderItem(
    val id: String = UUID.randomUUID().toString(),
    var purchaseOrderId: String = "",
    var inventoryItemId: String = "",
    var orderedQty: BigDecimal = BigDecimal.ZERO,
    var receivedQty: BigDecimal = BigDecimal.ZERO,
    var unit: String = "",
    var expectedPrice: BigDecimal = BigDecimal.ZERO,
    var totalPrice: BigDecimal = BigDecimal.ZERO
)

class GoodsReceive(
    val id: String = UUID.randomUUID().toString(),
    var grnNumber: String = "",
    var purchaseOrderId: String = "",
    var warehouseId: String = "",
    var totalReceivedAmount: BigDecimal = BigDecimal.ZERO,
    var receivedBy: String? = null,
    val receivedAt: Instant = Instant.now()
)

class GoodsReceiveItem(
    val id: String = UUID.randomUUID().toString(),
    var goodsReceiveId: String = "",
    var inventoryItemId: String = "",
    var receivedQty: BigDecimal = BigDecimal.ZERO,
    var damagedQty: BigDecimal = BigDecimal.ZERO,
    var unit: String = "",
    var actualUnitCost: BigDecimal = BigDecimal.ZERO,
    var totalCost: BigDecimal = BigDecimal.ZERO
)

class PurchaseReturn(
    val id: String = UUID.randomUUID().toString(),
    var returnNumber: String = "",
    var goodsReceiveId: String = "",
    var supplierId: String = "",
    var warehouseId: String = "",
    var totalReturnAmount: BigDecimal = BigDecimal.ZERO,
    var reason: String? = null,
    var createdBy: String? = null,
    val createdAt: Instant = Instant.now()
)

class PurchaseReturnItem(
    val id: String = UUID.randomUUID().toString(),
    var purchaseReturnId: String = "",
    var inventoryItemId: String = "",
    var returnQty: BigDecimal = BigDecimal.ZERO,
    var unit: String = "",
    var unitCost: BigDecimal = BigDecimal.ZERO,
    var totalCost: BigDecimal = BigDecimal.ZERO
)

class SupplierPriceHistory(
    val id: String = UUID.randomUUID().toString(),
    var supplierId: String = "",
    var inventoryItemId: String = "",
    var price: BigDecimal = BigDecimal.ZERO,
    val effectiveDate: Instant = Instant.now()
)

// DTOs
data class CreatePurchaseOrderDto(
    val supplierId: String = "",
    val warehouseId: String = "",
    val expectedDate: Instant? = null,
    val notes: String? = null,
    val items: List<POItemDto> = emptyList(),
    val createdBy: String? = null
)

data class POItemDto(
    val inventoryItemId: String = "",
    val orderedQty: BigDecimal = BigDecimal.ZERO,
    val unit: String = "",
    val expectedPrice: BigDecimal = BigDecimal.ZERO
)

data class CreateGoodsReceiveDto(
    val purchaseOrderId: String = "",
    val receivedBy: String? = null,
    val items: List<GRNItemDto> = emptyList()
)

data class GRNItemDto(
    val inventoryItemId: String = "",
    val receivedQty: BigDecimal = BigDecimal.ZERO,
    val damagedQty: BigDecimal = BigDecimal.ZERO,
    val unit: String = "",
    val actualUnitCost: BigDecimal = BigDecimal.ZERO
)

data class CreatePurchaseReturnDto(
    val goodsReceiveId: String = "",
    val reason: String? = null,
    val createdBy: String? = null,
    val items: List<PRItemDto> = emptyList()
)

data class PRItemDto(
    val inventoryItemId: String = "",
    val returnQty: BigDecimal = BigDecimal.ZERO,
    val unit: String = "",
    val unitCost: BigDecimal = BigDecimal.ZERO
)
