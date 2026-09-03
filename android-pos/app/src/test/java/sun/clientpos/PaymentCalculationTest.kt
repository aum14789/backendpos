package sun.clientpos

import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentCalculationTest {

    @Test
    fun testMultiPaymentRemainingAndChangeInSatang() {
        val orderTotal = 100000L // ฿1000.00
        val p1Cash = 20000L     // ฿200.00
        val p2QR = 30000L       // ฿300.00
        val p3Card = 50000L     // ฿500.00

        val totalPaid = p1Cash + p2QR + p3Card
        val remaining = (orderTotal - totalPaid).coerceAtLeast(0L)

        assertEquals(0L, remaining)

        val tendered = 50000L   // ฿500.00
        val due = 20000L        // ฿200.00
        val change = tendered - due
        assertEquals(30000L, change) // ฿300.00
    }
}
