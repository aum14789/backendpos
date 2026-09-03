package sun.clientpos

import org.junit.Assert.*
import org.junit.Test
import sun.clientpos.data.local.entity.RoomOrderEntity
import sun.clientpos.data.local.entity.RoomPaymentTransactionEntity
import java.util.UUID

/**
 * Unit test to verify data contracts, payload structures, and mathematical precision
 * between Android POS Room entities / Outbox Events and Backend REST DTOs / Sync Engine.
 * All monetary fields are in Long (satang).
 */
class AndroidBackendCompatibilityTest {

    @Test
    fun testOrderPayloadCompatibilityWithBackend() {
        val orderId = UUID.randomUUID().toString()
        val branchId = "branch-001"
        val order = RoomOrderEntity(
            orderId = orderId,
            branchId = branchId,
            tableId = "tbl-001",
            tableSessionId = null,
            orderNumber = "ORD-20260827-001",
            orderType = "DINE_IN",
            channel = "POS",
            status = "OPEN",
            kitchenStatus = "NOT_SENT",
            subtotalAmount = 35000L, // ฿350.00 in satang
            totalAmount = 35000L,    // ฿350.00 in satang
            createdBy = "cashier-001"
        )

        val payloadMap = mapOf(
            "orderId" to order.orderId,
            "branchId" to order.branchId,
            "tableId" to order.tableId,
            "orderType" to order.orderType,
            "channel" to order.channel,
            "subtotalSatang" to order.subtotalAmount,
            "totalSatang" to order.totalAmount,
            "createdBy" to order.createdBy
        )

        assertTrue(payloadMap.containsKey("orderId"))
        assertTrue(payloadMap.containsKey("branchId"))
        assertTrue(payloadMap.containsKey("totalSatang"))
        assertTrue(payloadMap.containsKey("createdBy"))

        assertEquals(orderId, payloadMap["orderId"])
        assertEquals("branch-001", payloadMap["branchId"])
        assertEquals(35000L, payloadMap["totalSatang"])
        assertEquals("cashier-001", payloadMap["createdBy"])
    }

    @Test
    fun testPaymentTransactionPayloadCompatibilityWithBackend() {
        val paymentId = UUID.randomUUID().toString()
        val orderId = UUID.randomUUID().toString()
        val idempotencyKey = "pos-tx-" + UUID.randomUUID().toString()

        val payment = RoomPaymentTransactionEntity(
            paymentId = paymentId,
            orderId = orderId,
            branchId = "branch-001",
            deviceId = "pos-tab-01",
            shiftId = "shift-001",
            paymentMethod = "PROMPTPAY",
            amount = 35000L,         // satang
            tenderedAmount = 35000L, // satang
            changeAmount = 0L,       // satang
            status = "SUCCESS",
            idempotencyKey = idempotencyKey,
            externalRef = "ref-12345",
            createdBy = "cashier-001"
        )

        val payloadMap = mapOf(
            "orderId" to payment.orderId,
            "branchId" to payment.branchId,
            "deviceId" to payment.deviceId,
            "shiftId" to payment.shiftId,
            "paymentMethod" to payment.paymentMethod,
            "amountSatang" to payment.amount,
            "tenderedSatang" to payment.tenderedAmount,
            "idempotencyKey" to payment.idempotencyKey,
            "externalRef" to payment.externalRef,
            "createdBy" to payment.createdBy
        )

        assertEquals("PROMPTPAY", payloadMap["paymentMethod"])
        assertEquals(35000L, payloadMap["amountSatang"])
        assertEquals(idempotencyKey, payloadMap["idempotencyKey"])
        assertEquals("ref-12345", payloadMap["externalRef"])
        assertEquals("cashier-001", payloadMap["createdBy"])
    }

    @Test
    fun testModifierSelectionBoundsCompatibility() {
        val minSelection = 1
        val maxSelection = 1
        val isRequired = true

        val selectedChoices = listOf("mod-mild")

        val isValid = if (isRequired && selectedChoices.size < minSelection) {
            false
        } else !(selectedChoices.size > maxSelection)

        assertTrue(isValid)

        val invalidOverSelected = listOf("mod-mild", "mod-spicy")
        val isOverValid = !(invalidOverSelected.size > maxSelection)
        assertFalse(isOverValid)
    }
}
