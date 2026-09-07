package com.sunpos.backend.domain.websocket

import com.sunpos.backend.domain.qrorder.QrOrder
import com.sunpos.backend.domain.qrorder.QrOrderItem
import com.sunpos.backend.domain.qrorder.QrOrderRepository
import com.sunpos.backend.domain.qrorder.QrOrderStatus
import org.slf4j.LoggerFactory
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant

data class BranchOrderItemPayload(
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitPrice: BigDecimal,
    val options: Any?,
    val note: String?
)

data class BranchOrderPushPayload(
    val orderId: String,
    val branchId: String,
    val tableNumber: String,
    val customerNote: String?,
    val totalAmount: BigDecimal,
    val items: List<BranchOrderItemPayload>,
    val createdAt: Instant
)

@Service
class BranchOrderPushService(
    private val messagingTemplate: SimpMessagingTemplate,
    private val sessionRegistry: BranchSessionRegistry,
    private val qrOrderRepository: QrOrderRepository
) {
    private val logger = LoggerFactory.getLogger(BranchOrderPushService::class.java)

    @Transactional
    fun pushOrderToBranch(order: QrOrder, items: List<QrOrderItem>): Boolean {
        val branchId = order.branchId

        if (!sessionRegistry.isBranchOnline(branchId)) {
            logger.warn("⚠️ Branch [{}] is OFFLINE. Order [{}] remains in 'pending' status.", branchId, order.id)
            return false
        }

        val itemPayloads = items.map { item ->
            BranchOrderItemPayload(
                productId = item.productId,
                productName = item.productName,
                quantity = item.quantity,
                unitPrice = item.unitPrice,
                options = item.options,
                note = item.note
            )
        }

        val payload = BranchOrderPushPayload(
            orderId = order.id,
            branchId = order.branchId,
            tableNumber = order.tableNumber,
            customerNote = order.customerNote,
            totalAmount = order.totalAmount,
            items = itemPayloads,
            createdAt = order.createdAt
        )

        val destination = "/topic/branch/$branchId/orders"
        messagingTemplate.convertAndSend(destination, payload)
        logger.info("📤 Order [{}] pushed to Branch [{}] at [{}]", order.id, branchId, destination)

        order.status = QrOrderStatus.sent_to_branch
        order.updatedAt = Instant.now()
        qrOrderRepository.save(order)

        return true
    }
}
