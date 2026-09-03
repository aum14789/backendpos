package sun.clientpos

import org.junit.Assert.*
import org.junit.Test
import sun.clientpos.printer.ReceiptBuilder
import sun.clientpos.printer.ReceiptItemLine
import sun.clientpos.printer.ReceiptPaymentLine
import sun.clientpos.printer.TaxInvoiceCustomer

class ReceiptFormattingTest {

    @Test
    fun `test abbreviated receipt thermal 80mm monospace text formatting`() {
        val items = listOf(
            ReceiptItemLine(name = "ข้าวผัดปู (Crab Fried Rice)", quantity = 2, unitPriceSatang = 12000L, subtotalSatang = 24000L),
            ReceiptItemLine(name = "ต้มยำกุ้ง (Tom Yum Goong)", quantity = 1, unitPriceSatang = 18000L, subtotalSatang = 18000L)
        )
        val payments = listOf(
            ReceiptPaymentLine(method = "PROMPTPAY", amountSatang = 38000L)
        )

        val receiptText = ReceiptBuilder.formatAbbreviatedReceiptText(
            companyName = "SunPOS Restaurant Group",
            branchName = "Sukhumvit Main Branch",
            branchCode = "BR-01",
            companyTaxId = "0105560000001",
            posDeviceId = "POS-01",
            cashierName = "Cashier John",
            orderNumber = "ORD-20260827-001",
            tableNumber = "T-05",
            orderType = "DINE_IN",
            items = items,
            grossSatang = 42000L,
            discountSatang = 4000L,
            taxSatang = 2486L,
            grandTotalSatang = 38000L,
            payments = payments,
            changeSatang = 0L
        )

        // Verifications
        assertTrue(receiptText.contains("SunPOS Restaurant Group"))
        assertTrue(receiptText.contains("ใบเสร็จรับเงิน / ใบกำกับภาษีอย่างย่อ"))
        assertTrue(receiptText.contains("0105560000001"))
        assertTrue(receiptText.contains("ORD-20260827-001"))
        assertTrue(receiptText.contains("ข้าวผัดปู"))
        assertTrue(receiptText.contains("PROMPTPAY"))
        assertTrue(receiptText.contains("380.00"))
    }

    @Test
    fun `test full tax invoice thermal 80mm with customer tax details`() {
        val customer = TaxInvoiceCustomer(
            taxpayerName = "บริษัท นวัตกรรมดิจิทัล จำกัด",
            taxId = "0105559012345",
            branchNumber = "00000",
            address = "888 อาคารออลซีซั่นส์ ถนนวิทยุ แขวงลุมพินี เขตปทุมวัน กทม. 10330",
            phone = "02-1234567"
        )
        val items = listOf(
            ReceiptItemLine(name = "Special Wagyu Set", quantity = 2, unitPriceSatang = 45000L, subtotalSatang = 90000L)
        )
        val payments = listOf(
            ReceiptPaymentLine(method = "CARD", amountSatang = 90000L)
        )

        val taxInvoiceText = ReceiptBuilder.formatFullTaxInvoiceText(
            invoiceNumber = "INV-20260827-0042",
            companyName = "SunPOS Restaurant Group",
            branchName = "Sukhumvit Main Branch",
            branchCode = "BR-01",
            companyTaxId = "0105560000001",
            companyAddress = "123 Sukhumvit Rd, Bangkok",
            posDeviceId = "POS-01",
            cashierName = "Manager Ann",
            orderNumber = "ORD-20260827-0099",
            customer = customer,
            items = items,
            buffetHeadChargeSatang = 0L,
            grossSatang = 90000L,
            discountSatang = 0L,
            taxSatang = 5888L,
            grandTotalSatang = 90000L,
            payments = payments
        )

        // Verifications
        assertTrue(taxInvoiceText.contains("INV-20260827-0042"))
        assertTrue(taxInvoiceText.contains("ใบเสร็จรับเงิน / ใบกำกับภาษี"))
        assertTrue(taxInvoiceText.contains("บริษัท นวัตกรรมดิจิทัล จำกัด"))
        assertTrue(taxInvoiceText.contains("0105559012345"))
        assertTrue(taxInvoiceText.contains("สำนักงานใหญ่ (Head Office)"))
        assertTrue(taxInvoiceText.contains("มูลค่าสินค้า/บริการ (Net Before Tax)"))
        assertTrue(taxInvoiceText.contains("ภาษีมูลค่าเพิ่ม 7% (VAT Amount)"))
    }
}
