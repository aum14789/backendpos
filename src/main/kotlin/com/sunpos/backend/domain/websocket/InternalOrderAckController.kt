package com.sunpos.backend.domain.websocket

import com.sunpos.backend.domain.qrorder.QrOrderRepository
import com.sunpos.backend.domain.qrorder.QrOrderStatus
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class OrderAckResponse(
    val orderId: String,
    val status: String,
    val message: String
)

data class BranchMonitoringStatus(
    val branchId: String,
    val isOnline: Boolean,
    val connectedAt: Instant?,
    val pendingOrdersCount: Int
)

@RestController
@RequestMapping("/api/internal")
class InternalOrderAckController(
    private val qrOrderRepository: QrOrderRepository,
    private val branchSessionRegistry: BranchSessionRegistry
) {

    private val logger = LoggerFactory.getLogger(InternalOrderAckController::class.java)

    @PostMapping("/orders/{orderId}/ack")
    fun acknowledgeOrder(@PathVariable orderId: String): ResponseEntity<OrderAckResponse> {
        val order = qrOrderRepository.findById(orderId)
            .orElseThrow { NoSuchElementException("Order '$orderId' not found") }

        order.status = QrOrderStatus.received
        order.updatedAt = Instant.now()
        qrOrderRepository.save(order)

        logger.info("✅ Order [{}] ACK received from branch [{}]. Status updated to 'received'.", orderId, order.branchId)

        return ResponseEntity.ok(
            OrderAckResponse(
                orderId = order.id,
                status = order.status.name,
                message = "Order acknowledged successfully"
            )
        )
    }

    /**
     * Real-time monitoring endpoint for a specific branch
     */
    @GetMapping("/branches/{branchId}/status")
    fun getBranchStatus(@PathVariable branchId: String): ResponseEntity<BranchMonitoringStatus> {
        val isOnline = branchSessionRegistry.isBranchOnline(branchId)
        val meta = branchSessionRegistry.getConnectionMeta(branchId)
        val pendingCount = qrOrderRepository.findByBranchIdAndStatus(branchId, QrOrderStatus.pending).size

        return ResponseEntity.ok(
            BranchMonitoringStatus(
                branchId = branchId,
                isOnline = isOnline,
                connectedAt = meta?.connectedAt,
                pendingOrdersCount = pendingCount
            )
        )
    }

    /**
     * Real-time monitoring endpoint listing all online branches
     */
    @GetMapping("/branches/status")
    fun getAllBranchStatuses(): ResponseEntity<List<BranchMonitoringStatus>> {
        val onlineBranches = branchSessionRegistry.getOnlineBranches()
        val statuses = onlineBranches.map { branchId ->
            val meta = branchSessionRegistry.getConnectionMeta(branchId)
            val pendingCount = qrOrderRepository.findByBranchIdAndStatus(branchId, QrOrderStatus.pending).size
            BranchMonitoringStatus(
                branchId = branchId,
                isOnline = true,
                connectedAt = meta?.connectedAt,
                pendingOrdersCount = pendingCount
            )
        }
        return ResponseEntity.ok(statuses)
    }
}
