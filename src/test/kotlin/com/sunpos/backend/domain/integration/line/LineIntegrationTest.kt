package com.sunpos.backend.domain.integration.line

import com.sunpos.backend.domain.crm.CreateCustomerDto
import com.sunpos.backend.domain.crm.CrmService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LineIntegrationTest {

    @Autowired
    private lateinit var crmService: CrmService

    @Autowired
    private lateinit var lineIntegrationService: LineIntegrationService

    @Autowired
    private lateinit var lineMessagingAdapter: LineMessagingAdapter

    @Test
    fun `test LINE identity linking, lookup, duplicate rejection, and unlinking`() {
        // 1. Setup Customer 1 & Customer 2
        val cust1 = crmService.createCustomer(
            CreateCustomerDto(displayName = "Somchai Sukjai", phone = "0860001111")
        )
        val cust2 = crmService.createCustomer(
            CreateCustomerDto(displayName = "Somsri Jaidee", phone = "0860002222")
        )

        // 2. Link LINE Identity to Customer 1
        val lineUserId = "U_LINE_USER_1001"
        val linkedIdentity = lineIntegrationService.linkLineIdentity(cust1.customer.id, lineUserId)
        assertNotNull(linkedIdentity.id)
        assertEquals(lineUserId, linkedIdentity.identityValue)

        // Idempotent check: Linking same LINE_USER_ID to same customer returns existing identity
        val relinked = lineIntegrationService.linkLineIdentity(cust1.customer.id, lineUserId)
        assertEquals(linkedIdentity.id, relinked.id)

        // 3. Lookup customer by LINE User ID
        val lookupFound = lineIntegrationService.findCustomerByLineUserId(lineUserId)
        assertNotNull(lookupFound)
        assertEquals(cust1.customer.id, lookupFound?.customer?.id)
        assertEquals("Somchai Sukjai", lookupFound?.customer?.displayName)

        // Lookup with unknown LINE ID returns null
        val lookupNotFound = lineIntegrationService.findCustomerByLineUserId("U_UNKNOWN_999")
        assertNull(lookupNotFound)

        // 4. Test Duplicate Identity Rejection: Linking same LINE_USER_ID to Customer 2 must throw IllegalArgumentException
        val ex = assertThrows(IllegalArgumentException::class.java) {
            lineIntegrationService.linkLineIdentity(cust2.customer.id, lineUserId)
        }
        assertTrue(ex.message?.contains("already linked to another customer") == true)

        // 5. Unlink LINE Identity
        val unlinked = lineIntegrationService.unlinkLineIdentity(cust1.customer.id, lineUserId)
        assertTrue(unlinked)

        // After unlinking, lookup should return null
        val lookupAfterUnlink = lineIntegrationService.findCustomerByLineUserId(lineUserId)
        assertNull(lookupAfterUnlink)

        // Unlink again returns false (no identity to unlink)
        val unlinkedAgain = lineIntegrationService.unlinkLineIdentity(cust1.customer.id, lineUserId)
        assertFalse(unlinkedAgain)
    }

    @Test
    fun `test Webhook event processing and customer resolution`() {
        // 1. Create and Link Customer
        val cust = crmService.createCustomer(
            CreateCustomerDto(displayName = "Wichai LineUser", phone = "0860003333")
        )
        val lineUserId = "U_WEBHOOK_USER_777"
        lineIntegrationService.linkLineIdentity(cust.customer.id, lineUserId)

        // 2. Webhook payload with known user
        val webhookPayloadKnown = """
            {
              "destination": "U_BOT_DEST",
              "events": [
                {
                  "type": "message",
                  "message": { "type": "text", "id": "msg_001", "text": "เช็คแต้มสะสม" },
                  "timestamp": 1724800000000,
                  "source": { "type": "user", "userId": "$lineUserId" },
                  "replyToken": "reply_token_abc"
                }
              ]
            }
        """.trimIndent()

        val responseKnown = lineIntegrationService.processWebhook(webhookPayloadKnown, null)
        assertTrue(responseKnown.success)
        assertEquals(1, responseKnown.eventCount)
        assertEquals(1, responseKnown.matchedCustomers.size)
        assertEquals(cust.customer.id, responseKnown.matchedCustomers[0].customerId)
        assertEquals("Wichai LineUser", responseKnown.matchedCustomers[0].displayName)

        // 3. Webhook payload with unknown user
        val webhookPayloadUnknown = """
            {
              "events": [
                {
                  "type": "follow",
                  "source": { "type": "user", "userId": "U_STRANGER_999" }
                }
              ]
            }
        """.trimIndent()

        val responseUnknown = lineIntegrationService.processWebhook(webhookPayloadUnknown, null)
        assertTrue(responseUnknown.success)
        assertEquals(1, responseUnknown.eventCount)
        assertTrue(responseUnknown.matchedCustomers.isEmpty())
    }

    @Test
    fun `test HMAC-SHA256 signature validation`() {
        val payload = "{\"events\":[{\"type\":\"message\"}]}"
        val secret = "my_channel_secret_test_key"

        // Calculate valid signature
        val keySpec = SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(keySpec)
        val validSig = Base64.getEncoder().encodeToString(mac.doFinal(payload.toByteArray(Charsets.UTF_8)))

        // Test valid signature
        assertTrue(lineIntegrationService.validateLineSignature(payload, validSig, secret))

        // Test invalid signature
        assertFalse(lineIntegrationService.validateLineSignature(payload, "invalid_sig_string", secret))
        assertFalse(lineIntegrationService.validateLineSignature("tampered_payload", validSig, secret))
    }

    @Test
    fun `test notification dispatch, failure resilience, and retry`() {
        val cust = crmService.createCustomer(
            CreateCustomerDto(displayName = "Notify Test Customer", phone = "0860004444")
        )
        lineIntegrationService.linkLineIdentity(cust.customer.id, "U_NOTIFY_USER_123")

        // 1. Successful Notification Dispatch
        lineMessagingAdapter.simulateApiFailure = false
        val successLog = lineIntegrationService.notifyOrderStatus(cust.customer.id, "ORD-20260828-101", "CONFIRMED")
        assertNotNull(successLog)
        assertEquals(NotificationStatus.DELIVERED, successLog?.status)

        // 2. Failure Resilience: simulate connection timeout
        lineMessagingAdapter.simulateApiFailure = true
        val failedLog = lineIntegrationService.notifyOrderStatus(cust.customer.id, "ORD-20260828-101", "COMPLETED")
        assertNotNull(failedLog)
        assertEquals(NotificationStatus.FAILED, failedLog?.status)
        assertTrue(failedLog?.errorMessage?.contains("Timeout") == true)

        // 3. Retry
        lineMessagingAdapter.simulateApiFailure = false
        val retriedCount = lineMessagingAdapter.retryFailedNotifications()
        assertTrue(retriedCount >= 1)
    }
}
