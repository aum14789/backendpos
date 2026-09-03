package sun.clientpos.data.repository

import sun.clientpos.data.local.dao.BuffetDao
import sun.clientpos.data.local.dao.OrderDao
import sun.clientpos.data.local.dao.PromotionDao
import sun.clientpos.data.local.dao.SyncOutboxDao
import sun.clientpos.data.local.entity.*
import sun.clientpos.domain.pricing.PricingCalculationResult
import sun.clientpos.domain.pricing.PricingEngine
import sun.clientpos.domain.pricing.PricingItemInput
import java.util.UUID

/**
 * Request DTO for creating an order item locally.
 * All prices in satang (minor units).
 */
data class LocalOrderItemRequest(
    val menuItemId: String,
    val nameSnapshot: String,
    val unitPriceSnapshot: Long, // satang
    val quantity: Int = 1,
    val notes: String? = null,
    val modifiers: List<LocalModifierRequest> = emptyList(),
    val isBuffetIncluded: Boolean = false
)

data class LocalModifierRequest(
    val modifierId: String,
    val nameSnapshot: String,
    val priceSnapshot: Long // satang
)

data class OrderPricingDiscountOptions(
    val manualDiscountSatang: Long = 0L,
    val manualDiscountPercent: Double = 0.0,
    val memberDiscountPercent: Double = 0.0,
    val couponDiscountSatang: Long = 0L,
    val serviceChargeRatePercent: Double = 0.0,
    val taxRatePercent: Double = 7.0,
    val isVatInclusive: Boolean = true
)

data class SplitItemSelection(
    val orderItemId: String,
    val splitQuantity: Int
)

class OrderRepository(
    private val orderDao: OrderDao,
    private val outboxDao: SyncOutboxDao,
    private val buffetDao: BuffetDao? = null,
    private val promotionDao: PromotionDao? = null
) {

    /**
     * Create a standard order (DINE_IN, TAKEAWAY, DELIVERY) locally with price snapshots and Outbox sync.
     */
    suspend fun createOrderLocal(
        branchId: String,
        tableId: String?,
        tableSessionId: String?,
        orderType: String,
        channel: String,
        createdBy: String?,
        items: List<LocalOrderItemRequest>,
        discountOptions: OrderPricingDiscountOptions = OrderPricingDiscountOptions(),
        deviceId: String,
        customerId: String? = null
    ): Pair<RoomOrderEntity, PricingCalculationResult> {
        val orderId = UUID.randomUUID().toString()
        val orderNumber = "OFF-${System.currentTimeMillis() % 10000}"

        val activePromotions = promotionDao?.getActivePromotions(branchId) ?: emptyList()
        val eligibleProductIdsByPromo = mutableMapOf<String, Set<String>>()
        if (promotionDao != null) {
            for (p in activePromotions) {
                eligibleProductIdsByPromo[p.promotionId] = promotionDao.getEligibleMenuItemIds(p.promotionId).toSet()
            }
        }

        val pricingInputs = items.map { item ->
            PricingItemInput(
                menuItemId = item.menuItemId,
                name = item.nameSnapshot,
                unitPriceSatang = item.unitPriceSnapshot,
                quantity = item.quantity,
                modifierPricesSatang = item.modifiers.map { it.priceSnapshot },
                isBuffetIncluded = item.isBuffetIncluded
            )
        }

        val pricingResult = PricingEngine.calculatePricing(
            items = pricingInputs,
            buffetHeadChargeSatang = 0L,
            activePromotions = activePromotions,
            eligibleProductIdsByPromo = eligibleProductIdsByPromo,
            manualDiscountSatang = discountOptions.manualDiscountSatang,
            manualDiscountPercent = discountOptions.manualDiscountPercent,
            memberDiscountPercent = discountOptions.memberDiscountPercent,
            couponDiscountSatang = discountOptions.couponDiscountSatang,
            serviceChargeRatePercent = discountOptions.serviceChargeRatePercent,
            taxRatePercent = discountOptions.taxRatePercent,
            isVatInclusive = discountOptions.isVatInclusive
        )

        for (itemReq in items) {
            val modPrices = itemReq.modifiers.map { it.priceSnapshot }
            val unitBase = if (itemReq.isBuffetIncluded) 0L else itemReq.unitPriceSnapshot
            val itemSubtotal = (unitBase + modPrices.sum()) * itemReq.quantity

            val orderItemId = UUID.randomUUID().toString()
            val itemEntity = RoomOrderItemEntity(
                orderItemId = orderItemId,
                orderId = orderId,
                menuItemId = itemReq.menuItemId,
                nameSnapshot = itemReq.nameSnapshot,
                unitPriceSnapshot = itemReq.unitPriceSnapshot,
                quantity = itemReq.quantity,
                notes = itemReq.notes,
                subtotal = itemSubtotal
            )
            orderDao.insertOrderItem(itemEntity)

            val modEntities = itemReq.modifiers.map {
                RoomOrderItemModifierEntity(
                    orderItemId = orderItemId,
                    modifierId = it.modifierId,
                    nameSnapshot = it.nameSnapshot,
                    priceSnapshot = it.priceSnapshot
                )
            }
            orderDao.insertOrderItemModifiers(modEntities)
        }

        val appliedEntities = pricingResult.appliedPromotions.map { promo ->
            RoomOrderAppliedPromotionEntity(
                orderId = orderId,
                promotionId = promo.promotionId,
                promotionCode = promo.code,
                promotionName = promo.name,
                discountAmountSatang = promo.discountSatang
            )
        }
        if (appliedEntities.isNotEmpty()) {
            promotionDao?.insertAppliedPromotions(appliedEntities)
        }

        val orderEntity = RoomOrderEntity(
            orderId = orderId,
            branchId = branchId,
            customerId = customerId,
            tableId = tableId,
            tableSessionId = tableSessionId,
            orderNumber = orderNumber,
            orderType = orderType,
            channel = channel,
            status = "OPEN",
            kitchenStatus = "NOT_SENT",
            subtotalAmount = pricingResult.grossAmount,
            discountAmount = pricingResult.totalDiscount,
            serviceChargeAmount = pricingResult.serviceChargeAmount,
            taxAmount = pricingResult.taxAmount,
            totalAmount = pricingResult.grandTotal,
            createdBy = createdBy
        )
        orderDao.insertOrder(orderEntity)

        // Enqueue ORDER_CREATED event into sync_outbox
        val outboxEvent = SyncOutboxEntity(
            aggregateType = "ORDER",
            aggregateId = orderId,
            eventType = "ORDER_CREATED",
            payload = """{"orderId":"$orderId","customerId":${if (customerId != null) "\"$customerId\"" else "null"},"orderNumber":"$orderNumber","orderType":"$orderType","channel":"$channel","grossSatang":${pricingResult.grossAmount},"discountSatang":${pricingResult.totalDiscount},"taxSatang":${pricingResult.taxAmount},"totalSatang":${pricingResult.grandTotal},"itemCount":${items.size}}""",
            deviceId = deviceId,
            branchId = branchId
        )
        outboxDao.insertEvent(outboxEvent)

        return Pair(orderEntity, pricingResult)
    }

    /**
     * Create a BUFFET order locally with session tracking and Outbox events.
     */
    suspend fun createBuffetOrderLocal(
        branchId: String,
        tableId: String?,
        tableSessionId: String?,
        buffetTier: RoomBuffetTierEntity,
        adultCount: Int,
        childCount: Int,
        channel: String,
        createdBy: String?,
        items: List<LocalOrderItemRequest>,
        discountOptions: OrderPricingDiscountOptions = OrderPricingDiscountOptions(),
        deviceId: String,
        customerId: String? = null
    ): Triple<RoomOrderEntity, RoomBuffetSessionEntity, PricingCalculationResult> {
        val orderId = UUID.randomUUID().toString()
        val orderNumber = "BUF-OFF-${System.currentTimeMillis() % 10000}"
        val sessionId = UUID.randomUUID().toString()

        val now = System.currentTimeMillis()
        val expiresAt = now + (buffetTier.timeLimitMinutes.toLong() * 60_000L)

        val buffetSession = RoomBuffetSessionEntity(
            sessionId = sessionId,
            orderId = orderId,
            branchId = branchId,
            buffetTierId = buffetTier.tierId,
            adultCount = adultCount,
            childCount = childCount,
            adultPriceSnapshot = buffetTier.adultPrice,
            childPriceSnapshot = buffetTier.childPrice,
            timeLimitMinutes = buffetTier.timeLimitMinutes,
            startedAt = now,
            expiresAt = expiresAt,
            status = "ACTIVE",
            createdBy = createdBy
        )
        buffetDao?.insertSession(buffetSession)

        val buffetHeadCharge = buffetSession.totalChargeSatang()

        val pricingInputs = items.map { item ->
            PricingItemInput(
                menuItemId = item.menuItemId,
                name = item.nameSnapshot,
                unitPriceSatang = item.unitPriceSnapshot,
                quantity = item.quantity,
                modifierPricesSatang = item.modifiers.map { it.priceSnapshot },
                isBuffetIncluded = item.isBuffetIncluded
            )
        }

        val pricingResult = PricingEngine.calculatePricing(
            items = pricingInputs,
            buffetHeadChargeSatang = buffetHeadCharge,
            manualDiscountSatang = discountOptions.manualDiscountSatang,
            manualDiscountPercent = discountOptions.manualDiscountPercent,
            memberDiscountPercent = discountOptions.memberDiscountPercent,
            couponDiscountSatang = discountOptions.couponDiscountSatang,
            serviceChargeRatePercent = discountOptions.serviceChargeRatePercent,
            taxRatePercent = discountOptions.taxRatePercent,
            isVatInclusive = discountOptions.isVatInclusive
        )

        for (itemReq in items) {
            val modPrices = itemReq.modifiers.map { it.priceSnapshot }
            val unitBase = if (itemReq.isBuffetIncluded) 0L else itemReq.unitPriceSnapshot
            val itemSubtotal = (unitBase + modPrices.sum()) * itemReq.quantity

            val orderItemId = UUID.randomUUID().toString()
            val itemEntity = RoomOrderItemEntity(
                orderItemId = orderItemId,
                orderId = orderId,
                menuItemId = itemReq.menuItemId,
                nameSnapshot = itemReq.nameSnapshot,
                unitPriceSnapshot = itemReq.unitPriceSnapshot,
                quantity = itemReq.quantity,
                notes = itemReq.notes,
                subtotal = itemSubtotal
            )
            orderDao.insertOrderItem(itemEntity)

            val modEntities = itemReq.modifiers.map {
                RoomOrderItemModifierEntity(
                    orderItemId = orderItemId,
                    modifierId = it.modifierId,
                    nameSnapshot = it.nameSnapshot,
                    priceSnapshot = it.priceSnapshot
                )
            }
            orderDao.insertOrderItemModifiers(modEntities)
        }

        val orderEntity = RoomOrderEntity(
            orderId = orderId,
            branchId = branchId,
            customerId = customerId,
            tableId = tableId,
            tableSessionId = tableSessionId,
            orderNumber = orderNumber,
            orderType = "BUFFET",
            channel = channel,
            status = "OPEN",
            kitchenStatus = "NOT_SENT",
            buffetSessionId = sessionId,
            subtotalAmount = pricingResult.grossAmount,
            discountAmount = pricingResult.totalDiscount,
            serviceChargeAmount = pricingResult.serviceChargeAmount,
            taxAmount = pricingResult.taxAmount,
            totalAmount = pricingResult.grandTotal,
            createdBy = createdBy
        )
        orderDao.insertOrder(orderEntity)

        val outboxEvent = SyncOutboxEntity(
            aggregateType = "ORDER",
            aggregateId = orderId,
            eventType = "ORDER_CREATED",
            payload = """{"orderId":"$orderId","customerId":${if (customerId != null) "\"$customerId\"" else "null"},"orderNumber":"$orderNumber","orderType":"BUFFET","tierId":"${buffetTier.tierId}","adultCount":$adultCount,"childCount":$childCount,"headChargeSatang":$buffetHeadCharge,"totalSatang":${pricingResult.grandTotal}}""",
            deviceId = deviceId,
            branchId = branchId
        )
        outboxDao.insertEvent(outboxEvent)

        return Triple(orderEntity, buffetSession, pricingResult)
    }

    /**
     * Apply Manual Cashier Discount with authorization reason.
     */
    suspend fun applyManualDiscountLocal(
        orderId: String,
        manualDiscountSatang: Long,
        reason: String,
        authorizedBy: String,
        deviceId: String,
        branchId: String
    ): RoomOrderEntity {
        val order = orderDao.getOrderById(orderId) ?: throw IllegalArgumentException("Order not found")
        require(order.status == "OPEN") { "Can only discount OPEN orders" }

        val newDiscount = manualDiscountSatang.coerceAtMost(order.subtotalAmount)
        val afterDiscount = (order.subtotalAmount - newDiscount).coerceAtLeast(0L)
        val tax = ((afterDiscount * 7.0) / 107.0).toLong()

        val updatedOrder = order.copy(
            discountAmount = newDiscount,
            taxAmount = tax,
            totalAmount = afterDiscount
        )
        orderDao.insertOrder(updatedOrder)

        // Enqueue ORDER_UPDATED event
        outboxDao.insertEvent(
            SyncOutboxEntity(
                aggregateType = "ORDER",
                aggregateId = orderId,
                eventType = "ORDER_UPDATED",
                payload = """{"orderId":"$orderId","manualDiscountSatang":$newDiscount,"reason":"$reason","authorizedBy":"$authorizedBy","totalSatang":$afterDiscount}""",
                deviceId = deviceId,
                branchId = branchId
            )
        )

        return updatedOrder
    }

    /**
     * Update order item quantity when order is OPEN.
     */
    suspend fun updateItemQuantityLocal(
        orderId: String,
        orderItemId: String,
        newQuantity: Int,
        deviceId: String,
        branchId: String
    ): RoomOrderEntity {
        val order = orderDao.getOrderById(orderId) ?: throw IllegalArgumentException("Order not found")
        require(order.status == "OPEN") { "Cannot modify non-OPEN order" }

        val items = orderDao.getOrderItems(orderId)
        val target = items.find { it.orderItemId == orderItemId } ?: throw IllegalArgumentException("Item not found")

        if (newQuantity <= 0) {
            orderDao.deleteOrderItem(orderItemId)
        } else {
            val unitPrice = target.unitPriceSnapshot
            val updatedSubtotal = unitPrice * newQuantity
            orderDao.insertOrderItem(target.copy(quantity = newQuantity, subtotal = updatedSubtotal))
        }

        // Recalculate order
        val refreshedItems = orderDao.getOrderItems(orderId)
        val gross = refreshedItems.sumOf { it.subtotal }
        val afterDiscount = (gross - order.discountAmount).coerceAtLeast(0L)
        val tax = ((afterDiscount * 7.0) / 107.0).toLong()

        val updatedOrder = order.copy(
            subtotalAmount = gross,
            taxAmount = tax,
            totalAmount = afterDiscount
        )
        orderDao.insertOrder(updatedOrder)

        outboxDao.insertEvent(
            SyncOutboxEntity(
                aggregateType = "ORDER",
                aggregateId = orderId,
                eventType = "ORDER_UPDATED",
                payload = """{"orderId":"$orderId","updatedItemId":"$orderItemId","newQty":$newQuantity,"totalSatang":$afterDiscount}""",
                deviceId = deviceId,
                branchId = branchId
            )
        )

        return updatedOrder
    }

    /**
     * Void an item locally without hard delete.
     */
    suspend fun voidOrderItemLocal(
        orderId: String,
        orderItemId: String,
        reason: String,
        voidedBy: String,
        deviceId: String,
        branchId: String
    ) {
        val order = orderDao.getOrderById(orderId) ?: return
        orderDao.updateOrderItemKitchenStatus(orderItemId, "VOIDED")

        val outboxEvent = SyncOutboxEntity(
            aggregateType = "ORDER",
            aggregateId = orderId,
            eventType = "ORDER_ITEM_VOIDED",
            payload = """{"orderId":"$orderId","orderItemId":"$orderItemId","reason":"$reason","voidedBy":"$voidedBy"}""",
            deviceId = deviceId,
            branchId = branchId
        )
        outboxDao.insertEvent(outboxEvent)
    }

    /**
     * Complete an order.
     */
    suspend fun completeOrderLocal(orderId: String, deviceId: String, branchId: String) {
        orderDao.updateOrderStatus(orderId, "COMPLETED")
        outboxDao.insertEvent(
            SyncOutboxEntity(
                aggregateType = "ORDER",
                aggregateId = orderId,
                eventType = "ORDER_COMPLETED",
                payload = """{"orderId":"$orderId","status":"COMPLETED"}""",
                deviceId = deviceId,
                branchId = branchId
            )
        )
    }
}
