package com.sunpos.backend.domain.order

import com.sunpos.backend.domain.pricing.PricingEngine
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
        const val SCALE = PricingEngine.SCALE
        val ROUNDING = PricingEngine.ROUNDING
        val VAT_RATE = PricingEngine.DEFAULT_VAT_RATE
        val POINT_REDEEM_RATE = PricingEngine.DEFAULT_POINT_REDEEM_RATE
        val SPEND_PER_POINT = PricingEngine.DEFAULT_SPEND_PER_POINT
    }

    /**
     * Calculate Item Subtotal from Base Price + Choice/Modifier additions * quantity
     */
    fun calculateItemSubtotal(
        unitPriceSnapshot: BigDecimal,
        quantity: BigDecimal,
        modifierPrices: List<BigDecimal> = emptyList(),
        surcharge: BigDecimal = BigDecimal.ZERO
    ): BigDecimal = PricingEngine.calculateLineSubtotal(unitPriceSnapshot, quantity, modifierPrices, surcharge)

    fun calculateOrderTotal(itemSubtotals: List<BigDecimal>): BigDecimal =
        itemSubtotals.fold(BigDecimal.ZERO) { acc, subtotal ->
            acc.add(subtotal.setScale(SCALE, ROUNDING))
        }.setScale(SCALE, ROUNDING)

    /**
     * Calculate cash discount value from redeemed loyalty points.
     * Rate: 100 points = 10 THB (1 point = 0.10 THB).
     */
    fun calculatePointDiscount(pointsToRedeem: BigDecimal): BigDecimal {
        if (pointsToRedeem <= BigDecimal.ZERO) return BigDecimal.ZERO.setScale(SCALE, ROUNDING)
        return pointsToRedeem.multiply(POINT_REDEEM_RATE).setScale(SCALE, ROUNDING)
    }

    /**
     * Deterministic Order Pricing Pipeline (delegates to pure PricingEngine):
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
        val lines = itemSubtotals.mapIndexed { idx, subtotal ->
            PricingEngine.LineItem(
                id = idx.toString(),
                unitPrice = subtotal,
                quantity = BigDecimal.ONE
            )
        }

        val result = PricingEngine.calculateOrder(
            lines = lines,
            promotionDiscount = promotionDiscount,
            memberDiscountPercentage = memberDiscountPercentage,
            memberDiscount = memberDiscount,
            manualDiscount = manualDiscount,
            couponDiscount = couponDiscount,
            pointDiscount = pointDiscount,
            serviceChargeRate = serviceChargeRate,
            isVatInclusive = isVatInclusive,
            vatRate = VAT_RATE
        )

        return OrderPricingCalculationResult(
            grossItemTotal = result.grossTotal,
            promotionDiscount = result.discounts.promotionDiscount,
            memberDiscount = result.discounts.memberDiscount,
            manualDiscount = result.discounts.manualDiscount,
            couponDiscount = result.discounts.couponDiscount,
            pointDiscount = result.discounts.pointDiscount,
            totalDiscount = result.totalDiscount,
            subtotalAfterDiscount = result.subtotalAfterDiscount,
            taxAmount = result.taxAmount,
            serviceChargeAmount = result.serviceCharge,
            grandTotal = result.grandTotal
        )
    }
}
