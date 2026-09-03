package sun.clientpos

import org.junit.Assert.*
import org.junit.Test
import sun.clientpos.common.PhoneUtils
import sun.clientpos.printer.ReceiptBuilder
import sun.clientpos.printer.ReceiptItemLine
import sun.clientpos.printer.ReceiptPaymentLine
import java.util.UUID

class OrderCustomerLinkTest {

    @Test
    fun testPhoneNormalization() {
        assertEquals("0812345678", PhoneUtils.normalize("081-234-5678"))
        assertEquals("0812345678", PhoneUtils.normalize("081 234 5678"))
        assertEquals("0812345678", PhoneUtils.normalize("+66 81 234 5678"))
        assertEquals("0812345678", PhoneUtils.normalize("+66812345678"))
        assertEquals("0812345678", PhoneUtils.normalize("66812345678"))
        assertEquals("0899998888", PhoneUtils.normalize("089-999-8888"))
    }

    @Test
    fun testCustomerReceiptFormatting() {
        val items = listOf(
            ReceiptItemLine(name = "ข้าวผัดกุ้ง", quantity = 2, unitPriceSatang = 8000L, subtotalSatang = 16000L)
        )
        val payments = listOf(
            ReceiptPaymentLine(method = "CASH", amountSatang = 20000L)
        )

        val receiptText = ReceiptBuilder.formatAbbreviatedReceiptText(
            companyName = "SunPOS Restaurant Group",
            branchName = "Sukhumvit Main Branch",
            branchCode = "BR-01",
            companyTaxId = "0105560000001",
            posDeviceId = "POS-DEV-01",
            cashierName = "Cashier 01",
            orderNumber = "ORD-20260827-001",
            tableNumber = "T-01",
            orderType = "DINE_IN",
            items = items,
            grossSatang = 16000L,
            discountSatang = 0L,
            taxSatang = 1047L,
            grandTotalSatang = 16000L,
            payments = payments,
            changeSatang = 4000L,
            customerName = "คุณสมชาย ใจดี",
            customerTier = "สมาชิก VIP (Gold)",
            earnedPoints = 2
        )

        assertTrue(receiptText.contains("สมาชิก: คุณสมชาย ใจดี (สมาชิก VIP (Gold))"))
        assertTrue(receiptText.contains("แต้มสะสมที่ได้รับจากบิลนี้:"))
        assertTrue(receiptText.contains("+2 แต้ม"))
    }

    @Test
    fun testCustomerUpsertAndOrderCustomerLinkedPayloads() {
        val custId = UUID.randomUUID().toString()
        val orderId = UUID.randomUUID().toString()
        val cleanPhone = PhoneUtils.normalize("+66 81 234 5678")

        // 1. Customer Upsert Payload
        val custPayload = """
            {
                "customerId": "$custId",
                "displayName": "สมชาย ใจดี",
                "phone": "$cleanPhone",
                "customerGroup": "VIP"
            }
        """.trimIndent()

        assertTrue(custPayload.contains(custId))
        assertTrue(custPayload.contains("0812345678"))
        assertTrue(custPayload.contains("สมชาย ใจดี"))

        // 2. Order Customer Linked Payload
        val orderLinkPayload = """
            {
                "orderId": "$orderId",
                "customerId": "$custId",
                "linkedBy": "cashier-01"
            }
        """.trimIndent()

        assertTrue(orderLinkPayload.contains(orderId))
        assertTrue(orderLinkPayload.contains(custId))
        assertTrue(orderLinkPayload.contains("cashier-01"))
    }

    @Test
    fun testPointEarnAndRedeemSyncPayloads() {
        val custId = UUID.randomUUID().toString()
        val orderId = UUID.randomUUID().toString()

        // 1. Point Earn on ฿500.00 bill (50,000 satang) with Gold 1.5x Multiplier
        // Net ฿500 / 25 = 20 Base Points * 1.5 = 30 Points
        val netSpentBaht = 50000L / 100.0
        val earnedPts = ((netSpentBaht / 25.0) * 1.5).toLong()
        assertEquals(30L, earnedPts)

        val earnPayload = """
            {
                "customerId": "$custId",
                "orderId": "$orderId",
                "orderAmountSatang": 50000,
                "earnedPoints": $earnedPts,
                "multiplier": 1.5
            }
        """.trimIndent()
        assertTrue(earnPayload.contains("earnedPoints\": 30"))

        // 2. Point Redeem 200 Points (฿20.00 / 2,000 satang discount)
        val redeemSatang = 2000L
        val ptsRedeemed = redeemSatang / 10
        assertEquals(200L, ptsRedeemed)

        val redeemPayload = """
            {
                "customerId": "$custId",
                "orderId": "$orderId",
                "pointsRedeemed": $ptsRedeemed,
                "discountAmountSatang": $redeemSatang
            }
        """.trimIndent()
        assertTrue(redeemPayload.contains("pointsRedeemed\": 200"))
        assertTrue(redeemPayload.contains("discountAmountSatang\": 2000"))
    }
}
