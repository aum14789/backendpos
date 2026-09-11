package com.sunpos.backend.domain.qrorder

import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Component
class QrOrderTimeoutWorker(
    private val qrOrderRepository: QrOrderRepository,
    private val qrOrderItemRepository: QrOrderItemRepository,
    private val quarantinedQrOrderRepository: QuarantinedQrOrderRepository,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(QrOrderTimeoutWorker::class.java)

    @Scheduled(fixedDelay = 60000)
    @Transactional
    fun processTimedOutOrders() {
        val cutoff = Instant.now().minus(Duration.ofMinutes(5))
        val timedOutOrders = qrOrderRepository.findTimedOutOrders(cutoff)

        if (timedOutOrders.isEmpty()) return

        logger.info("⏱️ Found {} QR orders that exceeded 5-minute grace period without POS delivery.", timedOutOrders.size)

        for (order in timedOutOrders) {
            try {
                val items = qrOrderItemRepository.findByOrderId(order.id)
                val itemsJson = try {
                    objectMapper.writeValueAsString(items)
                } catch (_: Exception) {
                    null
                }

                val quarantinedOrder = QuarantinedQrOrder(
                    id = UUID.randomUUID().toString(),
                    branchId = order.branchId,
                    tableNumber = order.tableNumber,
                    tableId = order.tableId,
                    sessionId = order.sessionId,
                    totalAmount = order.totalAmount,
                    customerNote = order.customerNote,
                    source = order.source,
                    idempotencyKey = order.idempotencyKey,
                    orderedAt = order.orderedAt,
                    cloudReceivedAt = order.cloudReceivedAt,
                    quarantinedAt = Instant.now(),
                    reason = "UNDELIVERED_TIMEOUT",
                    rawPayload = itemsJson,
                    isAcknowledged = false
                )

                quarantinedQrOrderRepository.save(quarantinedOrder)

                order.status = QrOrderStatus.undelivered_timeout
                order.updatedAt = Instant.now()
                qrOrderRepository.save(order)

                logger.warn("🛑 QR Order [{}] table [{}] quarantined after 5m timeout. Marked as undelivered_timeout.", order.id, order.tableNumber)
            } catch (ex: Exception) {
                logger.error("❌ Failed to quarantine timed out order [{}]: {}", order.id, ex.message, ex)
            }
        }
    }
}
