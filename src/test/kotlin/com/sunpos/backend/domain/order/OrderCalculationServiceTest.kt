package com.sunpos.backend.domain.order

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class OrderCalculationServiceTest {

    private val calcService = OrderCalculationService()

    @Test
    fun `test item subtotal with modifiers and quantity`() {
        val unitPrice = BigDecimal("120.00")
        val modifier1 = BigDecimal("15.00")
        val modifier2 = BigDecimal("10.00")
        val quantity = BigDecimal("2.5")

        val subtotal = calcService.calculateItemSubtotal(
            unitPriceSnapshot = unitPrice,
            quantity = quantity,
            modifierPrices = listOf(modifier1, modifier2)
        )

        // (120 + 15 + 10) * 2.5 = 145 * 2.5 = 362.5000
        assertEquals(BigDecimal("362.5000"), subtotal)
        assertEquals(4, subtotal.scale())
    }

    @Test
    fun `test order total sum of subtotals`() {
        val subtotals = listOf(
            BigDecimal("362.5000"),
            BigDecimal("60.0000"),
            BigDecimal("250.0000")
        )

        val total = calcService.calculateOrderTotal(subtotals)
        assertEquals(BigDecimal("672.5000"), total)
    }

    @Test
    fun `test deterministic discount pipeline with automatic promotions, member tier discount, manual discount, service charge, and inclusive VAT`() {
        // Gross: 1,000 THB
        val items = listOf(BigDecimal("500.0000"), BigDecimal("500.0000"))

        // Pipeline parameters:
        // 1. Gross = 1,000.0000
        // 2. Automatic Promo = 100.0000 (Remaining = 900.0000)
        // 3. Member Tier Discount (Gold 5%) = 5% of 900 = 45.0000 (Remaining = 855.0000)
        // 4. Manual Cashier Discount = 55.0000 (Remaining = 800.0000)
        // 5. Total Discount = 100 + 45 + 55 = 200.0000
        // 6. Subtotal After Discount = 800.0000
        // 7. Service Charge (10%) = 80.0000 => Base For Tax = 880.0000
        // 8. VAT Inclusive (7%) = 880 - (880 / 1.07) = 57.5701
        // 9. Grand Total = 880.0000
        val result = calcService.calculateFullOrderPipeline(
            itemSubtotals = items,
            promotionDiscount = BigDecimal("100.0000"),
            memberDiscountPercentage = BigDecimal("5.00"),
            manualDiscount = BigDecimal("55.0000"),
            serviceChargeRate = BigDecimal("10.00"),
            isVatInclusive = true
        )

        assertEquals(BigDecimal("1000.0000"), result.grossItemTotal)
        assertEquals(BigDecimal("100.0000"), result.promotionDiscount)
        assertEquals(BigDecimal("45.0000"), result.memberDiscount)
        assertEquals(BigDecimal("55.0000"), result.manualDiscount)
        assertEquals(BigDecimal("200.0000"), result.totalDiscount)
        assertEquals(BigDecimal("800.0000"), result.subtotalAfterDiscount)
        assertEquals(BigDecimal("80.0000"), result.serviceChargeAmount)
        assertEquals(BigDecimal("880.0000"), result.grandTotal)
        assertEquals(BigDecimal("57.5701"), result.taxAmount)
    }

    @Test
    fun `test pricing pipeline with point redemption discount and coupon discount`() {
        val items = listOf(BigDecimal("1000.0000"))

        // Gross = 1000
        // Auto Promo = 0
        // Member Discount (5%) = 50 (Remaining = 950)
        // Coupon = 50 (Remaining = 900)
        // Point Redeem (200 pts * 0.10) = 20 (Remaining = 880)
        // Total Discount = 50 + 50 + 20 = 120
        // Subtotal = 880
        val pointDiscount = calcService.calculatePointDiscount(BigDecimal("200.0000"))
        assertEquals(BigDecimal("20.0000"), pointDiscount)

        val result = calcService.calculateFullOrderPipeline(
            itemSubtotals = items,
            memberDiscountPercentage = BigDecimal("5.00"),
            couponDiscount = BigDecimal("50.0000"),
            pointDiscount = pointDiscount,
            isVatInclusive = true
        )

        assertEquals(BigDecimal("1000.0000"), result.grossItemTotal)
        assertEquals(BigDecimal("50.0000"), result.memberDiscount)
        assertEquals(BigDecimal("50.0000"), result.couponDiscount)
        assertEquals(BigDecimal("20.0000"), result.pointDiscount)
        assertEquals(BigDecimal("120.0000"), result.totalDiscount)
        assertEquals(BigDecimal("880.0000"), result.subtotalAfterDiscount)
        assertEquals(BigDecimal("880.0000"), result.grandTotal)
    }
}
