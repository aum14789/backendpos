package com.sunpos.backend.domain.pricing

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PricingEnginePureTest {

    @Test
    fun `test exact line subtotal calculation with modifiers and surcharge`() {
        val subtotal = PricingEngine.calculateLineSubtotal(
            unitPrice = BigDecimal("120.00"),
            quantity = BigDecimal("2"),
            modifierPrices = listOf(BigDecimal("15.00"), BigDecimal("10.00")),
            surcharge = BigDecimal("5.00")
        )
        // (120 + 15 + 10 + 5) * 2 = 150 * 2 = 300.0000
        assertEquals(BigDecimal("300.0000"), subtotal)
    }

    @Test
    fun `test vat inclusive extraction accuracy`() {
        val totalGross = BigDecimal("107.0000")
        val (net, tax) = PricingEngine.extractInclusiveVat(totalGross, BigDecimal("0.07"))

        // 107 / 1.07 = 100.0000
        assertEquals(BigDecimal("100.0000"), net)
        assertEquals(BigDecimal("7.0000"), tax)
        assertEquals(totalGross, net.add(tax))
    }

    @Test
    fun `test proportional discount allocation guarantees zero leakage`() {
        val lines = listOf(
            PricingEngine.LineItem("line-1", BigDecimal("100.00"), BigDecimal("1")),
            PricingEngine.LineItem("line-2", BigDecimal("200.00"), BigDecimal("1")),
            PricingEngine.LineItem("line-3", BigDecimal("300.00"), BigDecimal("1"))
        )
        // Total gross = 600.0000
        // Target discount = 50.0000
        val targetDiscount = BigDecimal("50.0000")
        val allocations = PricingEngine.allocateDiscountProportionately(lines, targetDiscount)

        assertEquals(3, allocations.size)
        val sumAllocatedDiscount = allocations.fold(BigDecimal.ZERO) { acc, a -> acc.add(a.allocatedDiscount) }
        assertEquals(targetDiscount, sumAllocatedDiscount, "Sum of line discounts must exactly equal the total discount")

        val sumNetAmount = allocations.fold(BigDecimal.ZERO) { acc, a -> acc.add(a.netAmount) }
        assertEquals(BigDecimal("550.0000"), sumNetAmount, "Sum of net amounts must equal 600 - 50 = 550")
    }

    @Test
    fun `test full pipeline with member discount, service charge and vat`() {
        val lines = listOf(
            PricingEngine.LineItem("l1", BigDecimal("500.00"), BigDecimal("1")),
            PricingEngine.LineItem("l2", BigDecimal("500.00"), BigDecimal("1"))
        )
        // Gross = 1000.0000
        // Member discount 10% = 100.0000 -> subtotal after disc = 900.0000
        // Service charge 10% on 900 = 90.0000 -> base for tax = 990.0000
        // VAT 7% inclusive on 990 = 990 - (990 / 1.07) = 990 - 925.2336 = 64.7664
        val result = PricingEngine.calculateOrder(
            lines = lines,
            memberDiscountPercentage = BigDecimal("10"),
            serviceChargeRate = BigDecimal("10"),
            isVatInclusive = true
        )

        assertEquals(BigDecimal("1000.0000"), result.grossTotal)
        assertEquals(BigDecimal("100.0000"), result.totalDiscount)
        assertEquals(BigDecimal("900.0000"), result.subtotalAfterDiscount)
        assertEquals(BigDecimal("90.0000"), result.serviceCharge)
        assertEquals(BigDecimal("990.0000"), result.grandTotal)
        assertEquals(BigDecimal("925.2336"), result.netBeforeTax)
        assertEquals(BigDecimal("64.7664"), result.taxAmount)
    }
}
