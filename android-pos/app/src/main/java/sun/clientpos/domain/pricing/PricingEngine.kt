package sun.clientpos.domain.pricing

import sun.clientpos.data.local.entity.RoomPromotionEntity
import kotlin.math.roundToLong

/**
 * Result breakdown of the deterministic Pricing Engine pipeline.
 * All monetary amounts are in satang (minor units).
 */
data class PricingCalculationResult(
    val grossAmount: Long,
    val automaticPromotionDiscount: Long,
    val manualDiscount: Long,
    val memberDiscount: Long,
    val couponDiscount: Long,
    val totalDiscount: Long,
    val subtotalAfterDiscount: Long,
    val serviceChargeAmount: Long,
    val taxAmount: Long,
    val grandTotal: Long,
    val appliedPromotions: List<AppliedPromotionInfo> = emptyList()
)

data class AppliedPromotionInfo(
    val promotionId: String,
    val code: String,
    val name: String,
    val discountSatang: Long
)

data class PricingItemInput(
    val menuItemId: String,
    val name: String,
    val unitPriceSatang: Long,
    val quantity: Int,
    val modifierPricesSatang: List<Long> = emptyList(),
    val isBuffetIncluded: Boolean = false
) {
    val subtotalSatang: Long
        get() {
            val base = if (isBuffetIncluded) 0L else unitPriceSatang
            return (base + modifierPricesSatang.sum()) * quantity
        }
}

/**
 * Deterministic POS Pricing Pipeline:
 *
 * Items -> Gross Subtotal -> Automatic Promotions -> Manual Discount -> Member Discount -> Coupon
 *       -> Subtotal After Discount -> Service Charge -> VAT (Inclusive/Exclusive) -> Grand Total
 *
 * All arithmetic is performed in pure integer satang (Long) or fixed point rounding.
 */
object PricingEngine {

    const val DEFAULT_VAT_RATE_PERCENT = 7.0
    const val DEFAULT_SERVICE_CHARGE_PERCENT = 0.0

    /**
     * Compute full order financial breakdown.
     */
    fun calculatePricing(
        items: List<PricingItemInput>,
        buffetHeadChargeSatang: Long = 0L,
        activePromotions: List<RoomPromotionEntity> = emptyList(),
        eligibleProductIdsByPromo: Map<String, Set<String>> = emptyMap(),
        manualDiscountSatang: Long = 0L,
        manualDiscountPercent: Double = 0.0,
        memberDiscountPercent: Double = 0.0,
        couponDiscountSatang: Long = 0L,
        serviceChargeRatePercent: Double = DEFAULT_SERVICE_CHARGE_PERCENT,
        taxRatePercent: Double = DEFAULT_VAT_RATE_PERCENT,
        isVatInclusive: Boolean = true
    ): PricingCalculationResult {
        val itemsGross = items.sumOf { it.subtotalSatang }
        val gross = itemsGross + buffetHeadChargeSatang

        // 1. Automatic Promotion Application
        val appliedPromos = mutableListOf<AppliedPromotionInfo>()
        var promoDiscount = 0L

        for (promo in activePromotions.filter { it.isActive }.sortedBy { it.priority }) {
            val eligibleItemIds = eligibleProductIdsByPromo[promo.promotionId] ?: emptySet()
            val eligibleItems = if (eligibleItemIds.isEmpty()) items else items.filter { eligibleItemIds.contains(it.menuItemId) }
            val eligibleAmount = eligibleItems.sumOf { it.subtotalSatang }

            if (eligibleAmount <= 0L) continue

            var discountForPromo = 0L
            when (promo.promoType) {
                "PERCENTAGE" -> {
                    if (promo.discountRate > 0.0) {
                        discountForPromo = (eligibleAmount * (promo.discountRate / 100.0)).roundToLong()
                    }
                }
                "FIXED_AMOUNT" -> {
                    if (promo.discountAmount > 0L) {
                        discountForPromo = promo.discountAmount.coerceAtMost(eligibleAmount)
                    }
                }
                "BUY_1_GET_1" -> {
                    // For each pair of eligible items, discount the cheapest one
                    for (item in eligibleItems) {
                        val freeUnits = item.quantity / 2
                        if (freeUnits > 0) {
                            discountForPromo += item.unitPriceSatang * freeUnits
                        }
                    }
                }
            }

            if (discountForPromo > 0L) {
                discountForPromo = discountForPromo.coerceAtMost(eligibleAmount)
                promoDiscount += discountForPromo
                appliedPromos.add(
                    AppliedPromotionInfo(
                        promotionId = promo.promotionId,
                        code = promo.code,
                        name = promo.name,
                        discountSatang = discountForPromo
                    )
                )
                if (promo.stackingPolicy == "NON_STACKABLE") {
                    break // Do not apply further promotions
                }
            }
        }

        // 2. Manual Discount (Fixed amount or Percentage)
        var manualDisc = manualDiscountSatang
        if (manualDiscountPercent > 0.0) {
            val percentDisc = (gross * (manualDiscountPercent / 100.0)).roundToLong()
            manualDisc = maxOf(manualDisc, percentDisc)
        }

        // 3. Member Tier Discount
        val memberDisc = if (memberDiscountPercent > 0.0) {
            (gross * (memberDiscountPercent / 100.0)).roundToLong()
        } else 0L

        // 4. Coupon Discount
        val couponDisc = couponDiscountSatang

        // Total Discount Capped at Gross
        val totalDisc = (promoDiscount + manualDisc + memberDisc + couponDisc).coerceAtMost(gross)
        val subtotalAfterDiscount = (gross - totalDisc).coerceAtLeast(0L)

        // 5. Service Charge
        val serviceCharge = if (serviceChargeRatePercent > 0.0) {
            (subtotalAfterDiscount * (serviceChargeRatePercent / 100.0)).roundToLong()
        } else 0L

        val baseForTax = subtotalAfterDiscount + serviceCharge

        // 6. Tax Calculation (Inclusive vs Exclusive)
        val (taxAmount, grandTotal) = if (isVatInclusive) {
            // Inclusive VAT: Tax = Base * (Rate / (100 + Rate))
            // Example: 107.00 baht -> Tax = 107 * (7 / 107) = 7.00 baht
            val rateFactor = taxRatePercent / (100.0 + taxRatePercent)
            val tax = (baseForTax * rateFactor).roundToLong()
            Pair(tax, baseForTax)
        } else {
            // Exclusive VAT: Tax = Base * (Rate / 100), GrandTotal = Base + Tax
            val tax = (baseForTax * (taxRatePercent / 100.0)).roundToLong()
            Pair(tax, baseForTax + tax)
        }

        return PricingCalculationResult(
            grossAmount = gross,
            automaticPromotionDiscount = promoDiscount,
            manualDiscount = manualDisc,
            memberDiscount = memberDisc,
            couponDiscount = couponDisc,
            totalDiscount = totalDisc,
            subtotalAfterDiscount = subtotalAfterDiscount,
            serviceChargeAmount = serviceCharge,
            taxAmount = taxAmount,
            grandTotal = grandTotal,
            appliedPromotions = appliedPromos
        )
    }
}
