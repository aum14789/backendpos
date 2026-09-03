package sun.clientpos

import org.junit.Assert.assertEquals
import org.junit.Test
import sun.clientpos.domain.pricing.CalculateOrderUseCase

class OrderCalculationUseCaseTest {

    private val calcUseCase = CalculateOrderUseCase()

    @Test
    fun testItemSubtotalWithModifiersAndQuantity() {
        val unitPrice = 12000L // ฿120.00 in satang
        val quantity = 2
        val modifierPrices = listOf(1500L, 1000L) // ฿15.00, ฿10.00 in satang

        // (12000 + 1500 + 1000) * 2 = 14500 * 2 = 29000 satang (฿290.00)
        val subtotal = calcUseCase.calculateItemSubtotal(unitPrice, quantity, modifierPrices)
        assertEquals(29000L, subtotal)
    }

    @Test
    fun testOrderTotalSum() {
        val subtotals = listOf(29000L, 6000L, 25000L) // satang
        val total = calcUseCase.calculateOrderTotal(subtotals)
        assertEquals(60000L, total) // ฿600.00
    }
}
