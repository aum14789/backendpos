package com.sunpos.backend.domain.order

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class OrderType {
    DINE_IN,
    TAKEAWAY,
    DELIVERY,
    BUFFET
}

enum class OrderChannel {
    POS,
    QR,
    LINE,
    WEB,
    DELIVERY,
    OTHER
}

enum class OrderStatus {
    OPEN,
    CONFIRMED,
    IN_KITCHEN,
    READY,
    SERVED,
    COMPLETED,
    CANCELLED,
    VOIDED
}

enum class FinancialStatus {
    UNPAID,
    PARTIALLY_PAID,
    PAID,
    REFUNDED,
    PARTIALLY_REFUNDED
}

enum class KitchenStatus {
    NOT_SENT,
    SENT,
    PREPARING,
    READY,
    CANCELLED
}

class Order(
    val id: String = UUID.randomUUID().toString(),
    var branchId: String = "",
    var customerId: String? = null,
    var tableId: String? = null,
    var tableSessionId: String? = null,
    var orderNumber: String = "",
    var orderType: OrderType = OrderType.DINE_IN,
    var channel: OrderChannel = OrderChannel.POS,
    var status: OrderStatus = OrderStatus.OPEN,
    var financialStatus: FinancialStatus = FinancialStatus.UNPAID,
    var kitchenStatus: KitchenStatus = KitchenStatus.NOT_SENT,
    var subtotalAmount: BigDecimal = BigDecimal.ZERO,
    var discountAmount: BigDecimal = BigDecimal.ZERO,
    var taxAmount: BigDecimal = BigDecimal.ZERO,
    var totalAmount: BigDecimal = BigDecimal.ZERO,
    var manualDiscountReason: String? = null,
    var manualDiscountAuthorizedBy: String? = null,
    var manualDiscountPercent: BigDecimal? = null,
    var businessDayId: String = "",
    var createdBy: String? = null,
    val createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now()
)

class OrderItem(
    val id: String = UUID.randomUUID().toString(),
    var orderId: String = "",
    var menuItemId: String = "",
    var nameSnapshot: String = "",
    var unitPriceSnapshot: BigDecimal = BigDecimal.ZERO,
    var quantity: BigDecimal = BigDecimal.ONE,
    var notes: String? = null,
    var subtotal: BigDecimal = BigDecimal.ZERO,
    var kitchenStatus: KitchenStatus = KitchenStatus.NOT_SENT,
    var recipeIdSnapshot: String? = null,
    var recipeVersionSnapshot: String? = null,
    var comboDefinitionIdSnapshot: String? = null,
    val createdAt: Instant = Instant.now()
)

class OrderItemModifier(
    val id: String = UUID.randomUUID().toString(),
    var orderItemId: String = "",
    var modifierId: String = "",
    var nameSnapshot: String = "",
    var priceSnapshot: BigDecimal = BigDecimal.ZERO,
    val createdAt: Instant = Instant.now()
)

class OrderComboSnapshot(
    val id: String = UUID.randomUUID().toString(),
    var orderItemId: String = "",
    var comboChoiceId: String = "",
    var menuItemId: String = "",
    var nameSnapshot: String = "",
    var priceOverrideSnapshot: BigDecimal = BigDecimal.ZERO,
    var surchargeSnapshot: BigDecimal = BigDecimal.ZERO,
    val createdAt: Instant = Instant.now()
)

// DTOs
data class OrderItemModifierRequest(
    val modifierId: String = "",
    val nameSnapshot: String = "",
    val priceSnapshot: BigDecimal = BigDecimal.ZERO
)

data class OrderComboChoiceRequest(
    val comboChoiceId: String = "",
    val menuItemId: String = "",
    val nameSnapshot: String = "",
    val priceOverride: BigDecimal? = null,
    val surcharge: BigDecimal = BigDecimal.ZERO
)

data class OrderItemRequest(
    val menuItemId: String = "",
    val nameSnapshot: String = "",
    val unitPriceSnapshot: BigDecimal = BigDecimal.ZERO,
    val quantity: BigDecimal = BigDecimal.ONE,
    val notes: String? = null,
    val modifiers: List<OrderItemModifierRequest> = emptyList(),
    val comboChoices: List<OrderComboChoiceRequest> = emptyList()
)

data class CreateOrderRequest(
    val branchId: String = "",
    val customerId: String? = null,
    val tableId: String? = null,
    val tableSessionId: String? = null,
    val orderType: OrderType = OrderType.DINE_IN,
    val channel: OrderChannel = OrderChannel.POS,
    val createdBy: String? = null,
    val items: List<OrderItemRequest> = emptyList()
)

data class LinkCustomerRequestDto(
    val customerId: String? = null,
    val reason: String? = null
)

data class ApplyManualDiscountRequest(
    val discountAmount: BigDecimal? = null,
    val discountPercent: BigDecimal? = null,
    val reason: String = "",
    val authorizedBy: String = ""
)

data class OrderItemResponseDto(
    val id: String = "",
    val menuItemId: String = "",
    val nameSnapshot: String = "",
    val unitPriceSnapshot: BigDecimal = BigDecimal.ZERO,
    val quantity: BigDecimal = BigDecimal.ONE,
    val notes: String? = null,
    val subtotal: BigDecimal = BigDecimal.ZERO,
    val kitchenStatus: KitchenStatus = KitchenStatus.NOT_SENT,
    val modifiers: List<OrderItemModifierResponseDto> = emptyList(),
    val comboChoices: List<OrderComboSnapshotResponseDto> = emptyList()
)

data class OrderItemModifierResponseDto(
    val id: String = "",
    val modifierId: String = "",
    val nameSnapshot: String = "",
    val priceSnapshot: BigDecimal = BigDecimal.ZERO
)

data class OrderComboSnapshotResponseDto(
    val id: String = "",
    val comboChoiceId: String = "",
    val menuItemId: String = "",
    val nameSnapshot: String = "",
    val priceOverrideSnapshot: BigDecimal = BigDecimal.ZERO,
    val surchargeSnapshot: BigDecimal = BigDecimal.ZERO
)

data class OrderResponseDto(
    val id: String = "",
    val branchId: String = "",
    val customerId: String? = null,
    val tableId: String? = null,
    val tableSessionId: String? = null,
    val orderNumber: String = "",
    val orderType: OrderType = OrderType.DINE_IN,
    val channel: OrderChannel = OrderChannel.POS,
    val status: OrderStatus = OrderStatus.OPEN,
    val financialStatus: FinancialStatus = FinancialStatus.UNPAID,
    val kitchenStatus: KitchenStatus = KitchenStatus.NOT_SENT,
    val subtotalAmount: BigDecimal = BigDecimal.ZERO,
    val discountAmount: BigDecimal = BigDecimal.ZERO,
    val taxAmount: BigDecimal = BigDecimal.ZERO,
    val totalAmount: BigDecimal = BigDecimal.ZERO,
    val manualDiscountReason: String? = null,
    val manualDiscountAuthorizedBy: String? = null,
    val manualDiscountPercent: BigDecimal? = null,
    val businessDayId: String = "",
    val items: List<OrderItemResponseDto> = emptyList(),
    val createdAt: Instant = Instant.now()
)
