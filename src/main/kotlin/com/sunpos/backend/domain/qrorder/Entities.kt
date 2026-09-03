package com.sunpos.backend.domain.qrorder

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class QrOrderStatus {
    pending,
    sent_to_branch,
    received,
    preparing,
    ready,
    completed,
    cancelled
}

data class QrOrder(
    val id: String = UUID.randomUUID().toString(),
    var branchId: String = "",
    var tableNumber: String = "",
    var status: QrOrderStatus = QrOrderStatus.pending,
    var customerNote: String? = null,
    var totalAmount: BigDecimal = BigDecimal.ZERO,
    var source: String = "qr",
    var idempotencyKey: String? = null,
    val createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now()
)

data class QrOrderItem(
    val id: String = UUID.randomUUID().toString(),
    var orderId: String = "",
    var productId: String = "",
    var productName: String = "",
    var quantity: Int = 1,
    var unitPrice: BigDecimal = BigDecimal.ZERO,
    var options: String? = null, // JSON string
    var note: String? = null
)

// ── DTOs ──

data class CreateQrOrderItemDto(
    val productId: String,
    val productName: String,
    val quantity: Int = 1,
    val unitPrice: BigDecimal = BigDecimal.ZERO,
    val options: String? = null,
    val note: String? = null
)

data class CreateQrOrderDto(
    val branchId: String,
    val tableNumber: String,
    val customerNote: String? = null,
    val items: List<CreateQrOrderItemDto> = emptyList()
)

data class QrOrderDetailsDto(
    val order: QrOrder,
    val items: List<QrOrderItem>
)

data class PublicOrderItemRequest(
    val productId: String,
    val productName: String,
    val quantity: Int = 1,
    val unitPrice: BigDecimal = BigDecimal.ZERO,
    val options: Any? = null,
    val note: String? = null
)

data class CreatePublicOrderRequest(
    val branchId: String,
    val tableNumber: String,
    val customerNote: String? = null,
    val items: List<PublicOrderItemRequest> = emptyList()
)

data class PublicOrderResponse(
    val orderId: String,
    val status: String = "pending",
    val message: String = "Order received successfully"
)

data class UpdateQrOrderStatusDto(
    val status: QrOrderStatus
)

data class QrMenuProductDto(
    val id: String,
    val categoryId: String,
    val name: String,
    val description: String? = null,
    val price: BigDecimal,
    val imageUrl: String? = null,
    val isAvailable: Boolean = true
)

data class QrMenuCategoryDto(
    val id: String,
    val name: String,
    val sortOrder: Int = 0,
    val products: List<QrMenuProductDto> = emptyList()
)

data class QrMenuResponseDto(
    val branchId: String,
    val branchName: String,
    val categories: List<QrMenuCategoryDto>
)
