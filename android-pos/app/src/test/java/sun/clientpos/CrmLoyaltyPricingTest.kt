package sun.clientpos

import org.junit.Assert.*
import org.junit.Test
import sun.clientpos.domain.pricing.PricingEngine
import sun.clientpos.domain.pricing.PricingItemInput

class CrmLoyaltyPricingTest {

    @Test
    fun `test member tier discount calculation in pricing pipeline`() {
        // Items: 2 x Wagyu Beef (฿350.00 / 35,000 satang) = ฿700.00 (70,000 satang)
        val items = listOf(
            PricingItemInput(
                menuItemId = "item-01",
                name = "Wagyu Beef",
                unitPriceSatang = 35000L,
                quantity = 2
            )
        )

        // 1. Regular Customer (Silver / 0% discount)
        val resultSilver = PricingEngine.calculatePricing(
            items = items,
            memberDiscountPercent = 0.0,
            isVatInclusive = true
        )
        assertEquals(70000L, resultSilver.grossAmount)
        assertEquals(0L, resultSilver.memberDiscount)
        assertEquals(70000L, resultSilver.grandTotal)

        // 2. VIP Customer (Gold / 5% discount)
        // 5% of ฿700 = ฿35.00 (3,500 satang) -> Grand Total = ฿665.00 (66,500 satang)
        val resultGold = PricingEngine.calculatePricing(
            items = items,
            memberDiscountPercent = 5.0,
            isVatInclusive = true
        )
        assertEquals(70000L, resultGold.grossAmount)
        assertEquals(3500L, resultGold.memberDiscount)
        assertEquals(66500L, resultGold.grandTotal)

        // 3. VVIP Customer (Platinum / 10% discount)
        // 10% of ฿700 = ฿70.00 (7,000 satang) -> Grand Total = ฿630.00 (63,000 satang)
        val resultPlatinum = PricingEngine.calculatePricing(
            items = items,
            memberDiscountPercent = 10.0,
            isVatInclusive = true
        )
        assertEquals(70000L, resultPlatinum.grossAmount)
        assertEquals(7000L, resultPlatinum.memberDiscount)
        assertEquals(63000L, resultPlatinum.grandTotal)
    }

    @Test
    fun `test coupon and points redemption deduction`() {
        // Items: 3 x Pork (฿180.00 / 18,000 satang) = ฿540.00 (54,000 satang)
        val items = listOf(
            PricingItemInput(
                menuItemId = "item-02",
                name = "Kurobuta Pork",
                unitPriceSatang = 18000L,
                quantity = 3
            )
        )

        // Redeem 200 Points (฿20.00 / 2,000 satang) + Coupon WELCOME50 (฿50.00 / 5,000 satang) = ฿70.00 discount
        val result = PricingEngine.calculatePricing(
            items = items,
            memberDiscountPercent = 5.0, // Gold Member 5% of ฿540 = ฿27.00 (2,700 satang)
            couponDiscountSatang = 7000L, // ฿70.00 (Coupon ฿50 + Points ฿20)
            isVatInclusive = true
        )

        assertEquals(54000L, result.grossAmount)
        assertEquals(2700L, result.memberDiscount)
        assertEquals(7000L, result.couponDiscount)
        assertEquals(9700L, result.totalDiscount) // 2700 + 7000 = 9700 satang (฿97.00)
        assertEquals(44300L, result.grandTotal)   // 54000 - 9700 = 44300 satang (฿443.00)
    }
}
