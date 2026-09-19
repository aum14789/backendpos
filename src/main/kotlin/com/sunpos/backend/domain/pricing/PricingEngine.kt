package com.sunpos.backend.domain.pricing

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Pure Pricing & Discount Allocation Engine (Candidate 4 Architecture).
 *
 * Characteristics:
 * - ZERO Spring framework annotations (@Component, @Service, @Transactional)
 * - ZERO JDBC / Repository / DB dependencies
 * - 100% deterministic, side-effect-free pure functions
 * - Guaranteed satang-accurate proportional discount allocation without rounding leakage
 */
object PricingEngine {

    const val SCALE = 4
    const val CURRENCY_SCALE = 2
    val ROUNDING = RoundingMode.HALF_UP
    val DEFAULT_VAT_RATE = BigDecimal("0.07") // 7% VAT
    val DEFAULT_POINT_REDEEM_RATE = BigDecimal("0.1000") // 100 Points = 10 THB
    val DEFAULT_SPEND_PER_POINT = BigDecimal("25.0000") // 25 THB spent = 1 Point

    data class LineItem(
        val id: String,
        val unitPrice: BigDecimal,
        val quantity: BigDecimal,
        val modifierTotal: BigDecimal = BigDecimal.ZERO,
        val surcharge: BigDecimal = BigDecimal.ZERO
    ) {
        val subtotal: BigDecimal
            get() = unitPrice.add(modifierTotal).add(surcharge)
                .multiply(quantity)
                .setScale(SCALE, ROUNDING)
    }

    data class DiscountBreakdown(
        val promotionDiscount: BigDecimal = BigDecimal.ZERO,
        val memberDiscount: BigDecimal = BigDecimal.ZERO,
        val manualDiscount: BigDecimal = BigDecimal.ZERO,
        val couponDiscount: BigDecimal = BigDecimal.ZERO,
        val pointDiscount: BigDecimal = BigDecimal.ZERO
    ) {
        val totalDiscount: BigDecimal
            get() = promotionDiscount
                .add(memberDiscount)
                .add(manualDiscount)
                .add(couponDiscount)
                .add(pointDiscount)
                .setScale(SCALE, ROUNDING)
    }

    data class LineAllocation(
        val lineId: String,
        val grossAmount: BigDecimal,
        val allocatedDiscount: BigDecimal,
        val netAmount: BigDecimal
    )

    data class PricingResult(
        val grossTotal: BigDecimal,
        val discounts: DiscountBreakdown,
        val totalDiscount: BigDecimal,
        val subtotalAfterDiscount: BigDecimal,
        val serviceCharge: BigDecimal,
        val netBeforeTax: BigDecimal,
        val taxAmount: BigDecimal,
        val grandTotal: BigDecimal,
        val lineAllocations: List<LineAllocation> = emptyList()
    )

    /**
     * Compute line item subtotal: (basePrice + sum(modifiers) + surcharge) * quantity
     */
    fun calculateLineSubtotal(
        unitPrice: BigDecimal,
        quantity: BigDecimal,
        modifierPrices: List<BigDecimal> = emptyList(),
        surcharge: BigDecimal = BigDecimal.ZERO
    ): BigDecimal {
        val base = unitPrice.setScale(SCALE, ROUNDING)
        val modSum = modifierPrices.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.setScale(SCALE, ROUNDING)) }
        val effectiveUnit = base.add(modSum).add(surcharge.setScale(SCALE, ROUNDING))
        return effectiveUnit.multiply(quantity.setScale(SCALE, ROUNDING)).setScale(SCALE, ROUNDING)
    }

    /**
     * Extract Inclusive VAT:
     * net = gross / (1 + vatRate)
     * tax = gross - net
     */
    fun extractInclusiveVat(gross: BigDecimal, vatRate: BigDecimal = DEFAULT_VAT_RATE): Pair<BigDecimal, BigDecimal> {
        val divisor = BigDecimal.ONE.add(vatRate)
        val net = gross.divide(divisor, SCALE, ROUNDING)
        val tax = gross.subtract(net).setScale(SCALE, ROUNDING)
        return Pair(net, tax)
    }

    /**
     * Proportional discount allocation across line items:
     * Guarantees that sum(line.allocatedDiscount) == totalDiscount exactly down to 4 decimal places.
     */
    fun allocateDiscountProportionately(
        lines: List<LineItem>,
        totalDiscountToAllocate: BigDecimal
    ): List<LineAllocation> {
        if (lines.isEmpty()) return emptyList()
        val gross = lines.fold(BigDecimal.ZERO) { acc, l -> acc.add(l.subtotal) }
        val targetDiscount = totalDiscountToAllocate.min(gross).setScale(SCALE, ROUNDING)

        if (targetDiscount.compareTo(BigDecimal.ZERO) <= 0 || gross.compareTo(BigDecimal.ZERO) <= 0) {
            return lines.map {
                LineAllocation(
                    lineId = it.id,
                    grossAmount = it.subtotal,
                    allocatedDiscount = BigDecimal.ZERO.setScale(SCALE, ROUNDING),
                    netAmount = it.subtotal
                )
            }
        }

        var allocatedSum = BigDecimal.ZERO
        val allocations = mutableListOf<LineAllocation>()

        for (i in 0 until lines.size - 1) {
            val line = lines[i]
            val ratio = line.subtotal.divide(gross, 8, RoundingMode.HALF_UP)
            val lineDisc = targetDiscount.multiply(ratio).setScale(SCALE, ROUNDING)
            allocatedSum = allocatedSum.add(lineDisc)
            val net = line.subtotal.subtract(lineDisc).setScale(SCALE, ROUNDING)
            allocations.add(LineAllocation(line.id, line.subtotal, lineDisc, net))
        }

        // Remainder adjustment for the last line to guarantee zero leakage
        val lastLine = lines.last()
        val lastLineDisc = targetDiscount.subtract(allocatedSum).setScale(SCALE, ROUNDING)
        val lastNet = lastLine.subtotal.subtract(lastLineDisc).setScale(SCALE, ROUNDING)
        allocations.add(LineAllocation(lastLine.id, lastLine.subtotal, lastLineDisc, lastNet))

        return allocations
    }

    /**
     * Full deterministic pipeline computation
     */
    fun calculateOrder(
        lines: List<LineItem>,
        promotionDiscount: BigDecimal = BigDecimal.ZERO,
        memberDiscountPercentage: BigDecimal = BigDecimal.ZERO,
        memberDiscount: BigDecimal = BigDecimal.ZERO,
        manualDiscount: BigDecimal = BigDecimal.ZERO,
        couponDiscount: BigDecimal = BigDecimal.ZERO,
        pointDiscount: BigDecimal = BigDecimal.ZERO,
        serviceChargeRate: BigDecimal = BigDecimal.ZERO,
        isVatInclusive: Boolean = true,
        vatRate: BigDecimal = DEFAULT_VAT_RATE
    ): PricingResult {
        val gross = lines.fold(BigDecimal.ZERO) { acc, l -> acc.add(l.subtotal) }.setScale(SCALE, ROUNDING)

        // 1. Promotions
        val promoDisc = promotionDiscount.setScale(SCALE, ROUNDING).coerceAtMost(gross)
        val remAfterPromo = gross.subtract(promoDisc).setScale(SCALE, ROUNDING)

        // 2. Member discount
        val calcMemberDisc = if (memberDiscountPercentage > BigDecimal.ZERO) {
            remAfterPromo.multiply(memberDiscountPercentage.divide(BigDecimal("100"), SCALE, ROUNDING)).setScale(SCALE, ROUNDING)
        } else {
            BigDecimal.ZERO.setScale(SCALE, ROUNDING)
        }
        val effMemberDisc = memberDiscount.setScale(SCALE, ROUNDING).max(calcMemberDisc).coerceAtMost(remAfterPromo)
        val remAfterMember = remAfterPromo.subtract(effMemberDisc).setScale(SCALE, ROUNDING)

        // 3. Manual discount
        val effManualDisc = manualDiscount.setScale(SCALE, ROUNDING).coerceAtMost(remAfterMember)
        val remAfterManual = remAfterMember.subtract(effManualDisc).setScale(SCALE, ROUNDING)

        // 4. Coupon discount
        val effCouponDisc = couponDiscount.setScale(SCALE, ROUNDING).coerceAtMost(remAfterManual)
        val remAfterCoupon = remAfterManual.subtract(effCouponDisc).setScale(SCALE, ROUNDING)

        // 5. Point redemption
        val effPointDisc = pointDiscount.setScale(SCALE, ROUNDING).coerceAtMost(remAfterCoupon)

        val breakdown = DiscountBreakdown(
            promotionDiscount = promoDisc,
            memberDiscount = effMemberDisc,
            manualDiscount = effManualDisc,
            couponDiscount = effCouponDisc,
            pointDiscount = effPointDisc
        )
        val totalDisc = breakdown.totalDiscount.coerceAtMost(gross)
        val subtotalAfterDisc = gross.subtract(totalDisc).setScale(SCALE, ROUNDING)

        // 6. Service Charge
        val serviceCharge = if (serviceChargeRate > BigDecimal.ZERO) {
            subtotalAfterDisc.multiply(serviceChargeRate.divide(BigDecimal("100"), SCALE, ROUNDING)).setScale(SCALE, ROUNDING)
        } else {
            BigDecimal.ZERO.setScale(SCALE, ROUNDING)
        }

        val baseForTax = subtotalAfterDisc.add(serviceCharge).setScale(SCALE, ROUNDING)

        // 7. VAT
        val (tax, grandTotal, netBeforeTax) = if (isVatInclusive) {
            val (net, taxAmt) = extractInclusiveVat(baseForTax, vatRate)
            Triple(taxAmt, baseForTax, net)
        } else {
            val taxAmt = baseForTax.multiply(vatRate).setScale(SCALE, ROUNDING)
            val total = baseForTax.add(taxAmt).setScale(SCALE, ROUNDING)
            Triple(taxAmt, total, baseForTax)
        }

        val allocations = allocateDiscountProportionately(lines, totalDisc)

        return PricingResult(
            grossTotal = gross,
            discounts = breakdown,
            totalDiscount = totalDisc,
            subtotalAfterDiscount = subtotalAfterDisc,
            serviceCharge = serviceCharge,
            netBeforeTax = netBeforeTax,
            taxAmount = tax,
            grandTotal = grandTotal,
            lineAllocations = allocations
        )
    }
}
