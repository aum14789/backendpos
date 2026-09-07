package com.sunpos.backend.domain.websocket

import com.sunpos.backend.domain.qrorder.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.socket.messaging.SessionDisconnectEvent
import org.springframework.web.socket.CloseStatus
import java.math.BigDecimal

@SpringBootTest
@ActiveProfiles("test")
class WebSocketBranchFlowTest {

    @Autowired
    private lateinit var sessionRegistry: BranchSessionRegistry

    @Autowired
    private lateinit var branchOrderPushService: BranchOrderPushService

    @Autowired
    private lateinit var qrOrderService: QrOrderService

    @Autowired
    private lateinit var internalOrderAckController: InternalOrderAckController

    @Autowired
    private lateinit var qrOrderRepository: QrOrderRepository

    @Test
    fun `test branch session registry online offline lifecycle`() {
        val branchId = "BR_TEST_01"
        val sessionId = "sess-12345"

        // 1. Initial status: Offline
        assertFalse(sessionRegistry.isBranchOnline(branchId))

        // 2. Branch connects -> Online
        sessionRegistry.register(branchId, sessionId)
        assertTrue(sessionRegistry.isBranchOnline(branchId))
        assertEquals(sessionId, sessionRegistry.getSessionId(branchId))
        assertTrue(sessionRegistry.getOnlineBranches().contains(branchId))

        // 3. Branch disconnects -> Offline
        val disconnectEvent = SessionDisconnectEvent(this, org.springframework.messaging.support.GenericMessage(ByteArray(0)), sessionId, CloseStatus.NORMAL)
        sessionRegistry.handleSessionDisconnect(disconnectEvent)
        assertFalse(sessionRegistry.isBranchOnline(branchId))
    }

    @Test
    fun `test order push and ACK update status to received`() {
        val branchId = "BR_ACK_TEST"
        val sessionId = "sess-ack-01"
        sessionRegistry.register(branchId, sessionId)

        // 1. Create order
        val req = CreatePublicOrderRequest(
            branchId = branchId,
            tableNumber = "T10",
            customerNote = "ขอน้ำแข็งเพิ่ม",
            items = listOf(
                PublicOrderItemRequest(
                    productId = "P99",
                    productName = "ต้มยำกุ้งแม่น้ำ",
                    quantity = 1,
                    unitPrice = BigDecimal("250.00")
                )
            )
        )

        val created = qrOrderService.createPublicOrder(req, "idemp-ack-01")
        val order = qrOrderRepository.findById(created.orderId).get()

        // Because branch was online, status should have transitioned to sent_to_branch
        assertEquals(QrOrderStatus.sent_to_branch, order.status)

        // 2. Branch sends ACK back to Cloud
        val ackResp = internalOrderAckController.acknowledgeOrder(order.id).body
        assertNotNull(ackResp)
        assertEquals("received", ackResp!!.status)

        // 3. Verify status in database is now received
        val updatedOrder = qrOrderRepository.findById(order.id).get()
        assertEquals(QrOrderStatus.received, updatedOrder.status)

        // 4. Test Monitoring Status Endpoints
        val statusResp = internalOrderAckController.getBranchStatus(branchId).body
        assertNotNull(statusResp)
        assertTrue(statusResp!!.isOnline)
        assertEquals(branchId, statusResp.branchId)
        assertNotNull(statusResp.connectedAt)

        val allStatuses = internalOrderAckController.getAllBranchStatuses().body
        assertNotNull(allStatuses)
        assertTrue(allStatuses!!.any { it.branchId == branchId })
    }
}
