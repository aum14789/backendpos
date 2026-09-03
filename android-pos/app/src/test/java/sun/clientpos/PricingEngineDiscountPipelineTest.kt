package sun.clientpos

import org.junit.Assert.*
import org.junit.Test
import sun.clientpos.data.local.entity.RoomPromotionEntity
import sun.clientpos.domain.pricing.PricingEngine
import sun.clientpos.domain.pricing.PricingItemInput

class PricingEngineDiscountPipelineTest {

    @Test
    fun `test combined automatic promotions and manual discount in deterministic pipeline`() {
        // Items in cart:
        // Item 1: 2x ฿150.00 = 30000 satang
        // Item 2: 1x ฿200.00 = 20000 satang
        val items = listOf(
            PricingItemInput(menuItemId = "item-1", name = "Steak", unitPriceSatang = 15000L, quantity = 2),
            PricingItemInput(menuItemId = "item-2", name = "Salad", unitPriceSatang = 20000L, quantity = 1)
        )
        // Gross = 300.00 + 200.00 = ฿500.00 (50000 satang)

        // 1. Automatic Promotion: 10% off bill (Priority 1)
        val promo1 = RoomPromotionEntity(
            promotionId = "promo-10pct",
            code = "PROMO10",
            name = "10% Off",
            promoType = "PERCENTAGE",
            discountRate = 10.0,
            priority = 1,
            isActive = true
        )

        // 2. Manual Discount: ฿50.00 (5000 satang) entered by Cashier with Manager Authorization
        val manualDiscountSatang = 5000L

        val result = PricingEngine.calculatePricing(
            items = items,
            buffetHeadChargeSatang = 0L,
            activePromotions = listOf(promo1),
            manualDiscountSatang = manualDiscountSatang,
            isVatInclusive = true
        )

        assertEquals(50000L, result.grossAmount) // ฿500.00 gross
        assertEquals(5000L, result.automaticPromotionDiscount) // 10% of 50000 = 5000 satang (฿50.00)
        assertEquals(5000L, result.manualDiscount) // ฿50.00 manual discount
        assertEquals(10000L, result.totalDiscount) // ฿100.00 total discount
        assertEquals(40000L, result.subtotalAfterDiscount) // ฿400.00
        assertEquals(40000L, result.grandTotal) // ฿400.00

        // VAT 7% inclusive on ฿400.00: 40000 * 7 / 107 = 2617 satang (฿26.17)
        assertEquals(2617L, result.taxAmount)
        assertEquals(1, result.appliedPromotions.size)
        assertEquals("PROMO10", result.appliedPromotions[0].code)
    }

    @Test
    fun `test buffet per-head pricing base with additional manual discount`() {
        // Buffet Order: 2 Adults @ ฿399.00 = 79800 satang
        val buffetHeadCharge = 79800L

        // Buffet-included items (฿0.00 in cart subtotal) + 1 Special beverage @ ฿80.00
        val items = listOf(
            PricingItemInput(menuItemId = "buffet-beef", name = "Wagyu Slice", unitPriceSatang = 35000L, quantity = 5, isBuffetIncluded = true),
            PricingItemInput(menuItemId = "drink-soda", name = "Special Craft Soda", unitPriceSatang = 8000L, quantity = 1, isBuffetIncluded = false)
        )
        // Gross = 798.00 (headcount) + 80.00 (drink) = ฿878.00 (87800 satang)

        // Manual discount of 5% applied
        val result = PricingEngine.calculatePricing(
            items = items,
            buffetHeadChargeSatang = buffetHeadCharge,
            manualDiscountPercent = 5.0,
            isVatInclusive = true
        )

        assertEquals(87800L, result.grossAmount) // ฿878.00
        // 5% of 87800 = 4390 satang (฿43.90)
        assertEquals(4390L, result.manualDiscount)
        assertEquals(83410L, result.subtotalAfterDiscount) // 878.00 - 43.90 = ฿834.10
        assertEquals(83410L, result.grandTotal)

        // VAT 7% on 83410 satang: 83410 * 7 / 107 = 5457 satang (฿54.57)
        assertEquals(5457L, result.taxAmount)
    }
}
