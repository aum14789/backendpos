package sun.clientpos

import org.junit.Assert.*
import org.junit.Test
import sun.clientpos.data.local.entity.SyncOutboxEntity
import sun.clientpos.data.local.entity.SyncStatus

class SyncOutboxEntityTest {

    @Test
    fun testSyncOutboxEntityDefaults() {
        val event = SyncOutboxEntity(
            aggregateType = "ORDER",
            aggregateId = "ord-12345",
            eventType = "ORDER_CREATED",
            payload = "{\"amount\": 500.00}",
            deviceId = "dev-01",
            branchId = "br-01"
        )

        assertNotNull(event.eventId)
        assertEquals(SyncStatus.PENDING, event.status)
        assertEquals(0, event.retryCount)
        assertNull(event.lastError)
    }
}
