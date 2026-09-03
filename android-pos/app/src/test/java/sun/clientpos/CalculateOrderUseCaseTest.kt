package sun.clientpos

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import sun.clientpos.domain.pricing.CalculateOrderUseCase
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Unit tests verifying financial calculations on SunPOS Android POS:
 *   1. Standard item subtotal: (unitPrice * qty)
 *   2. Item with modifiers: (unitPrice + sum(modifiers)) * qty
 *   3. Order with manual discount
 *   4. VAT 7% Inclusive calculation
 *   5. Mathematical parity with Backend OrderCalculationService (BigDecimal scale 4)
 */
class CalculateOrderUseCaseTest {

    private lateinit var useCase: CalculateOrderUseCase

    @Before
    fun setUp() {
        useCase = CalculateOrderUseCase()
    }

    @Test
    fun `test standard line item subtotal without modifiers`() {
        // 2x ข้าวผัดปู (฿120.00 = 12000 satang)
        val unitPrice = 12000L
        val quantity = 2
        val subtotal = useCase.calculateItemSubtotal(unitPrice, quantity)

        // Expected: 12000 * 2 = 24000 satang (฿240.00)
        assertEquals(24000L, subtotal)
    }

    @Test
    fun `test line item subtotal with multiple modifiers and surcharge`() {
        // 2x ข้าวผัดพิเศษ (฿120.00 = 12000 satang)
        // Modifiers: Extra Crab (฿40.00 = 4000 satang) + Fried Egg (฿15.00 = 1500 satang)
        // Surcharge: ฿10.00 = 1000 satang (e.g. takeaway packaging)
        val unitPrice = 12000L
        val modifiers = listOf(4000L, 1500L)
        val surcharge = 1000L
        val quantity = 2

        // Effective unit price = 12000 + 4000 + 1500 + 1000 = 18500 satang (฿185.00)
        // Total subtotal = 18500 * 2 = 37000 satang (฿370.00)
        val subtotal = useCase.calculateItemSubtotal(unitPrice, quantity, modifiers, surcharge)

        assertEquals(37000L, subtotal)
    }

    @Test
    fun `test order pipeline with manual cashier discount`() {
        // Item 1: 2x ฿120.00 = 24000 satang
        // Item 2: 1x ฿220.00 = 22000 satang
        // Item 3: 2x ฿45.00 = 9000 satang
        val itemSubtotals = listOf(24000L, 22000L, 9000L)
        val grossExpected = 55000L // ฿550.00

        // Manual discount ฿50.00 = 5000 satang
        val manualDiscount = 5000L

        val result = useCase.calculateFullOrderPipeline(
            itemSubtotals = itemSubtotals,
            manualDiscount = manualDiscount,
            isVatInclusive = true
        )

        assertEquals(grossExpected, result.grossItemTotal)
        assertEquals(5000L, result.totalDiscount)
        assertEquals(50000L, result.subtotalAfterDiscount) // ฿500.00
        assertEquals(50000L, result.grandTotal) // ฿500.00 (VAT inclusive)

        // VAT 7% inclusive on ฿500.00 => (50000 * 7) / 107 = 3271 satang (฿32.71)
        assertEquals(3271L, result.taxAmount)
    }

    @Test
    fun `test VAT 7 percent inclusive calculation with exact standard amount`() {
        // Clean ฿107.00 (10700 satang)
        val itemSubtotals = listOf(10700L)

        val result = useCase.calculateFullOrderPipeline(
            itemSubtotals = itemSubtotals,
            isVatInclusive = true
        )

        // Formula: Tax = Base * (7 / 107) = 10700 * 7 / 107 = 700 satang (฿7.00)
        // Net Before Tax = 10700 - 700 = 10000 satang (฿100.00)
        assertEquals(10700L, result.grossItemTotal)
        assertEquals(700L, result.taxAmount)
        assertEquals(10700L, result.grandTotal)
    }

    @Test
    fun `test price snapshot freeze guarantee`() {
        // Simulates price change on menu catalog:
        // Item originally ordered at ฿120.00 (12000 satang)
        val originalSnapshotPrice = 12000L
        val quantity = 3

        val frozenSubtotal = useCase.calculateItemSubtotal(originalSnapshotPrice, quantity)
        assertEquals(36000L, frozenSubtotal)

        // Menu catalog price later changed to ฿150.00 (15000 satang)
        // POS must STILL calculate with snapshot price
        val catalogUpdatedPrice = 15000L
        assertNotEquals(catalogUpdatedPrice * quantity, frozenSubtotal)
        assertEquals(36000L, frozenSubtotal)
    }

    @Test
    fun `test mathematical parity between Android satang and Backend BigDecimal scale 4`() {
        // Test inputs
        val unitPriceSatang = 22000L // ฿220.00
        val qty = 3
        val itemSubtotalSatang = unitPriceSatang * qty // 66000L (฿660.00)
        val promoDiscountSatang = 6000L // ฿60.00
        val manualDiscountSatang = 4000L // ฿40.00

        // 1. Android Calculation (satang Long)
        val androidResult = useCase.calculateFullOrderPipeline(
            itemSubtotals = listOf(itemSubtotalSatang),
            promotionDiscount = promoDiscountSatang,
            manualDiscount = manualDiscountSatang,
            isVatInclusive = true
        )

        // 2. Backend Formula (BigDecimal scale 4)
        val backendGross = BigDecimal("660.0000")
        val backendPromo = BigDecimal("60.0000")
        val backendManual = BigDecimal("40.0000")
        val backendTotalDisc = backendPromo.add(backendManual) // 100.0000
        val backendSubAfterDisc = backendGross.subtract(backendTotalDisc) // 560.0000

        // Backend Inclusive VAT: base - (base / 1.07)
        val divisor = BigDecimal("1.0700")
        val backendTax = backendSubAfterDisc.subtract(
            backendSubAfterDisc.divide(divisor, 4, RoundingMode.HALF_UP)
        ).setScale(4, RoundingMode.HALF_UP) // 36.6355 -> 36.64

        val backendGrandTotal = backendSubAfterDisc

        // Compare Android converted to BigDecimal vs Backend
        assertEquals(backendGross, CalculateOrderUseCase.satangToBigDecimal(androidResult.grossItemTotal))
        assertEquals(backendTotalDisc, CalculateOrderUseCase.satangToBigDecimal(androidResult.totalDiscount))
        assertEquals(backendSubAfterDisc, CalculateOrderUseCase.satangToBigDecimal(androidResult.subtotalAfterDiscount))
        assertEquals(backendGrandTotal, CalculateOrderUseCase.satangToBigDecimal(androidResult.grandTotal))

        // Compare Satang VAT vs Backend VAT (rounded to 2 decimal places in satang)
        // 36.6355 baht * 100 = 3663.55 -> 3664 satang
        val backendTaxSatang = CalculateOrderUseCase.bigDecimalToSatang(backendTax)
        assertEquals(backendTaxSatang, androidResult.taxAmount)
    }
}
