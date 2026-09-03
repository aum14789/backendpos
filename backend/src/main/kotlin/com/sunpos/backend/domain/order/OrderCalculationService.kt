package com.sunpos.backend.domain.order

import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

data class OrderPricingCalculationResult(
    val grossItemTotal: BigDecimal,
    val promotionDiscount: BigDecimal,
    val memberDiscount: BigDecimal,
    val manualDiscount: BigDecimal = BigDecimal.ZERO,
    val couponDiscount: BigDecimal = BigDecimal.ZERO,
    val pointDiscount: BigDecimal = BigDecimal.ZERO,
    val totalDiscount: BigDecimal,
    val subtotalAfterDiscount: BigDecimal,
    val taxAmount: BigDecimal,
    val serviceChargeAmount: BigDecimal,
    val grandTotal: BigDecimal
)

@Service
class OrderCalculationService {

    companion object {
        const val SCALE = 4
        val ROUNDING = RoundingMode.HALF_UP
        val VAT_RATE = BigDecimal("0.07") // 7% VAT
        val POINT_REDEEM_RATE = BigDecimal("0.1000") // 100 Points = 10 THB -> 1 Point = 0.10 THB
        val SPEND_PER_POINT = BigDecimal("25.0000") // 25 THB spent = 1 Point
    }

    /**
     * Calculate Item Subtotal from Base Price + Choice/Modifier additions * quantity
     */
    fun calculateItemSubtotal(
        unitPriceSnapshot: BigDecimal,
        quantity: BigDecimal,
        modifierPrices: List<BigDecimal> = emptyList(),
        surcharge: BigDecimal = BigDecimal.ZERO
    ): BigDecimal {
        val baseUnitPrice = unitPriceSnapshot.setScale(SCALE, ROUNDING)
        val modifierSum = modifierPrices.fold(BigDecimal.ZERO) { acc, p -> acc.add(p.setScale(SCALE, ROUNDING)) }
        val effectiveUnitPrice = baseUnitPrice.add(modifierSum).add(surcharge.setScale(SCALE, ROUNDING))

        return effectiveUnitPrice.multiply(quantity.setScale(SCALE, ROUNDING)).setScale(SCALE, ROUNDING)
    }

    fun calculateOrderTotal(itemSubtotals: List<BigDecimal>): BigDecimal {
        return itemSubtotals.fold(BigDecimal.ZERO) { acc, subtotal ->
            acc.add(subtotal.setScale(SCALE, ROUNDING))
        }.setScale(SCALE, ROUNDING)
    }

    /**
     * Calculate cash discount value from redeemed loyalty points.
     * Rate: 100 points = 10 THB (1 point = 0.10 THB).
     */
    fun calculatePointDiscount(pointsToRedeem: BigDecimal): BigDecimal {
        if (pointsToRedeem <= BigDecimal.ZERO) return BigDecimal.ZERO.setScale(SCALE, ROUNDING)
        return pointsToRedeem.multiply(POINT_REDEEM_RATE).setScale(SCALE, ROUNDING)
    }

    /**
     * Deterministic Order Pricing Pipeline:
     * 1. Gross Item Total (base price + choices/modifiers)
     * 2. Automatic Promotions (product/bill level promotions from PromotionEngine)
     * 3. Member Tier Discount (% from customer membership tier applied on remaining balance)
     * 4. Manual Cashier/Manager Discount (fixed or percentage with authorization)
     * 5. Coupon Discount
     * 6. Loyalty Point Redemption Discount (100 pts = 10 THB)
     * 7. Service Charge (if applicable, e.g. 10% on subtotal after discounts)
     * 8. VAT (Inclusive extraction: Total * (Rate / (1 + Rate)))
     * 9. Grand Total
     */
    fun calculateFullOrderPipeline(
        itemSubtotals: List<BigDecimal>,
        promotionDiscount: BigDecimal = BigDecimal.ZERO,
        memberDiscountPercentage: BigDecimal = BigDecimal.ZERO,
        memberDiscount: BigDecimal = BigDecimal.ZERO,
        manualDiscount: BigDecimal = BigDecimal.ZERO,
        couponDiscount: BigDecimal = BigDecimal.ZERO,
        pointDiscount: BigDecimal = BigDecimal.ZERO,
        serviceChargeRate: BigDecimal = BigDecimal.ZERO,
        isVatInclusive: Boolean = true
    ): OrderPricingCalculationResult {
        val gross = calculateOrderTotal(itemSubtotals)

        // 1. Automatic Product/Bill Promotions
        val promoDisc = promotionDiscount.setScale(SCALE, ROUNDING).coerceAtMost(gross)
        val remainingAfterPromo = gross.subtract(promoDisc).setScale(SCALE, ROUNDING)

        // 2. Member Tier Discount (% from customer's membership tier)
        val calculatedMemberDiscount = if (memberDiscountPercentage > BigDecimal.ZERO) {
            remainingAfterPromo.multiply(memberDiscountPercentage.divide(BigDecimal("100"), SCALE, ROUNDING)).setScale(SCALE, ROUNDING)
        } else {
            BigDecimal.ZERO.setScale(SCALE, ROUNDING)
        }
        val effectiveMemberDisc = memberDiscount.setScale(SCALE, ROUNDING).max(calculatedMemberDiscount).coerceAtMost(remainingAfterPromo)
        val remainingAfterMember = remainingAfterPromo.subtract(effectiveMemberDisc).setScale(SCALE, ROUNDING)

        // 3. Manual Discount
        val effectiveManualDisc = manualDiscount.setScale(SCALE, ROUNDING).coerceAtMost(remainingAfterMember)
        val remainingAfterManual = remainingAfterMember.subtract(effectiveManualDisc).setScale(SCALE, ROUNDING)

        // 4. Coupon Discount
        val effectiveCouponDisc = couponDiscount.setScale(SCALE, ROUNDING).coerceAtMost(remainingAfterManual)
        val remainingAfterCoupon = remainingAfterManual.subtract(effectiveCouponDisc).setScale(SCALE, ROUNDING)

        // 5. Point Redemption Discount
        val effectivePointDisc = pointDiscount.setScale(SCALE, ROUNDING).coerceAtMost(remainingAfterCoupon)

        // Total Aggregate Discount
        val totalDisc = promoDisc
            .add(effectiveMemberDisc)
            .add(effectiveManualDisc)
            .add(effectiveCouponDisc)
            .add(effectivePointDisc)
            .setScale(SCALE, ROUNDING)
            .coerceAtMost(gross)
        val subtotalAfterDisc = gross.subtract(totalDisc).setScale(SCALE, ROUNDING)

        // 6. Service Charge
        val serviceCharge = if (serviceChargeRate > BigDecimal.ZERO) {
            subtotalAfterDisc.multiply(serviceChargeRate.divide(BigDecimal("100"), SCALE, ROUNDING)).setScale(SCALE, ROUNDING)
        } else {
            BigDecimal.ZERO.setScale(SCALE, ROUNDING)
        }

        val baseForTax = subtotalAfterDisc.add(serviceCharge).setScale(SCALE, ROUNDING)

        // 7. VAT Calculation
        val (tax, grandTotal) = if (isVatInclusive) {
            val divisor = BigDecimal.ONE.add(VAT_RATE)
            val taxAmount = baseForTax.subtract(baseForTax.divide(divisor, SCALE, ROUNDING)).setScale(SCALE, ROUNDING)
            Pair(taxAmount, baseForTax)
        } else {
            val taxAmount = baseForTax.multiply(VAT_RATE).setScale(SCALE, ROUNDING)
            val total = baseForTax.add(taxAmount).setScale(SCALE, ROUNDING)
            Pair(taxAmount, total)
        }

        return OrderPricingCalculationResult(
            grossItemTotal = gross,
            promotionDiscount = promoDisc,
            memberDiscount = effectiveMemberDisc,
            manualDiscount = effectiveManualDisc,
            couponDiscount = effectiveCouponDisc,
            pointDiscount = effectivePointDisc,
            totalDiscount = totalDisc,
            subtotalAfterDiscount = subtotalAfterDisc,
            taxAmount = tax,
            serviceChargeAmount = serviceCharge,
            grandTotal = grandTotal
        )
    }
}
