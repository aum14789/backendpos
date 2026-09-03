package sun.clientpos.printer

import sun.clientpos.common.MoneyUtils.toDisplayBaht
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*

/**
 * Receipt item line data.
 */
data class ReceiptItemLine(
    val name: String,
    val quantity: Int,
    val unitPriceSatang: Long,
    val subtotalSatang: Long,
    val modifiers: List<String> = emptyList(),
    val isBuffetIncluded: Boolean = false
)

/**
 * Payment tender record for receipt printing.
 */
data class ReceiptPaymentLine(
    val method: String,
    val amountSatang: Long
)

/**
 * Full Tax Invoice Customer details for 80mm printing.
 */
data class TaxInvoiceCustomer(
    val taxpayerName: String,
    val taxId: String,
    val branchNumber: String = "00000", // "00000" for Head Office (สำนักงานใหญ่)
    val address: String,
    val phone: String? = null
)

/**
 * ESC/POS Thermal 80mm Receipt Builder.
 * Supports standard 80mm printer width (48 chars/line in Font A).
 * Character set: TIS-620 / CP874 for Thai Language support.
 */
object ReceiptBuilder {

    const val LINE_WIDTH = 48
    private val THAI_CHARSET: Charset = try {
        Charset.forName("TIS-620")
    } catch (_: Exception) {
        try {
            Charset.forName("windows-874")
        } catch (_: Exception) {
            Charsets.UTF_8
        }
    }

    // ESC/POS Commands
    private val ESC_INIT = byteArrayOf(0x1B, 0x40) // Initialize printer
    private val ESC_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    private val ESC_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    private val ESC_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)
    private val ESC_BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
    private val ESC_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
    private val ESC_DOUBLE_HEIGHT_ON = byteArrayOf(0x1D, 0x21, 0x01)
    private val ESC_DOUBLE_HEIGHT_OFF = byteArrayOf(0x1D, 0x21, 0x00)
    private val ESC_FEED_AND_CUT = byteArrayOf(0x1D, 0x56, 0x42, 0x00) // GS V B 0 (Feed and Cut)

    /**
     * Format Human-Readable Monospace Plain Text for Abbreviated Receipt (ใบเสร็จรับเงินอย่างย่อ 80mm).
     */
    fun formatAbbreviatedReceiptText(
        companyName: String,
        branchName: String,
        branchCode: String,
        companyTaxId: String,
        posDeviceId: String,
        cashierName: String,
        orderNumber: String,
        tableNumber: String?,
        orderType: String,
        buffetTierName: String? = null,
        buffetAdults: Int = 0,
        buffetChildren: Int = 0,
        buffetHeadChargeSatang: Long = 0L,
        items: List<ReceiptItemLine>,
        grossSatang: Long,
        discountSatang: Long,
        taxSatang: Long,
        grandTotalSatang: Long,
        payments: List<ReceiptPaymentLine>,
        changeSatang: Long,
        customerName: String? = null,
        customerTier: String? = null,
        earnedPoints: Long = 0L
    ): String {
        val sb = StringBuilder()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US)
        val now = dateFormat.format(Date())

        sb.append(centerText(companyName)).append("\n")
        sb.append(centerText("สาขา: $branchName ($branchCode)")).append("\n")
        sb.append(centerText("เลขประจำตัวผู้เสียภาษี: $companyTaxId")).append("\n")
        sb.append(centerText("ใบเสร็จรับเงิน / ใบกำกับภาษีอย่างย่อ")).append("\n")
        sb.append("-".repeat(LINE_WIDTH)).append("\n")

        sb.append("วันที่: $now\n")
        sb.append("เลขที่: $orderNumber    เครื่อง: $posDeviceId\n")
        val tableInfo = if (tableNumber != null) "โต๊ะ: $tableNumber  " else ""
        sb.append("${tableInfo}ประเภท: $orderType    พนักงาน: $cashierName\n")

        if (!customerName.isNullOrBlank()) {
            sb.append("สมาชิก: $customerName (${customerTier ?: "MEMBER"})\n")
        }
        sb.append("-".repeat(LINE_WIDTH)).append("\n")

        if (buffetHeadChargeSatang > 0L && buffetTierName != null) {
            sb.append(twoColumns("🥩 $buffetTierName", "")).append("\n")
            val headDesc = "   ($buffetAdults ผู้ใหญ่, $buffetChildren เด็ก)"
            sb.append(twoColumns(headDesc, buffetHeadChargeSatang.toDisplayBaht())).append("\n")
        }

        for (item in items) {
            val itemLine = "${item.quantity}x ${item.name}"
            val priceStr = if (item.isBuffetIncluded) "Buffet" else item.subtotalSatang.toDisplayBaht()
            sb.append(twoColumns(itemLine, priceStr)).append("\n")
            for (mod in item.modifiers) {
                sb.append("   + $mod\n")
            }
        }
        sb.append("-".repeat(LINE_WIDTH)).append("\n")

        sb.append(twoColumns("ยอดรวม (Gross Subtotal):", grossSatang.toDisplayBaht())).append("\n")
        if (discountSatang > 0L) {
            sb.append(twoColumns("ส่วนลดรวม (Discount):", "-${discountSatang.toDisplayBaht()}")).append("\n")
        }
        sb.append(twoColumns("ภาษีมูลค่าเพิ่ม 7% (VAT Included):", taxSatang.toDisplayBaht())).append("\n")
        sb.append(twoColumns("ยอดสุทธิ (Total Amount):", "฿${grandTotalSatang.toDisplayBaht()}")).append("\n")
        sb.append("-".repeat(LINE_WIDTH)).append("\n")

        for (p in payments) {
            sb.append(twoColumns("ชำระโดย (${p.method}):", p.amountSatang.toDisplayBaht())).append("\n")
        }
        if (changeSatang > 0L) {
            sb.append(twoColumns("เงินทอน (Change):", changeSatang.toDisplayBaht())).append("\n")
        }

        if (earnedPoints > 0L) {
            sb.append("-".repeat(LINE_WIDTH)).append("\n")
            sb.append(twoColumns("แต้มสะสมที่ได้รับจากบิลนี้:", "+$earnedPoints แต้ม")).append("\n")
        }

        sb.append("-".repeat(LINE_WIDTH)).append("\n")
        sb.append(centerText("ขอบคุณที่ใช้บริการ / Thank You!")).append("\n")
        sb.append(centerText("ราคารวมภาษีมูลค่าเพิ่มแล้ว (VAT Inclusive)")).append("\n")

        return sb.toString()
    }

    /**
     * Format Human-Readable Monospace Plain Text for Full Tax Invoice (ใบกำกับภาษีเต็มรูป 80mm).
     */
    fun formatFullTaxInvoiceText(
        invoiceNumber: String,
        companyName: String,
        branchName: String,
        branchCode: String,
        companyTaxId: String,
        companyAddress: String,
        posDeviceId: String,
        cashierName: String,
        orderNumber: String,
        customer: TaxInvoiceCustomer,
        items: List<ReceiptItemLine>,
        buffetHeadChargeSatang: Long,
        grossSatang: Long,
        discountSatang: Long,
        taxSatang: Long,
        grandTotalSatang: Long,
        payments: List<ReceiptPaymentLine>
    ): String {
        val sb = StringBuilder()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US)
        val now = dateFormat.format(Date())
        val netBeforeTaxSatang = grandTotalSatang - taxSatang

        sb.append(centerText(companyName)).append("\n")
        sb.append(centerText("สาขา: $branchName ($branchCode)")).append("\n")
        sb.append(centerText(companyAddress)).append("\n")
        sb.append(centerText("เลขประจำตัวผู้เสียภาษี: $companyTaxId")).append("\n")
        sb.append(centerText("ใบเสร็จรับเงิน / ใบกำกับภาษี")).append("\n")
        sb.append(centerText("(TAX INVOICE / RECEIPT)")).append("\n")
        sb.append("=".repeat(LINE_WIDTH)).append("\n")

        sb.append("เลขที่ใบกำกับ: $invoiceNumber\n")
        sb.append("วันที่: $now\n")
        sb.append("อ้างอิงบิล: $orderNumber    เครื่อง: $posDeviceId    พนักงาน: $cashierName\n")
        sb.append("-".repeat(LINE_WIDTH)).append("\n")

        sb.append("ข้อมูลผู้ซื้อ (Customer Info):\n")
        sb.append("ชื่อ: ${customer.taxpayerName}\n")
        sb.append("เลขประจำตัวผู้เสียภาษี: ${customer.taxId}\n")
        val branchLabel = if (customer.branchNumber == "00000") "สำนักงานใหญ่ (Head Office)" else "สาขาที่ ${customer.branchNumber}"
        sb.append("สาขา: $branchLabel\n")
        sb.append("ที่อยู่: ${customer.address}\n")
        if (!customer.phone.isNullOrBlank()) {
            sb.append("โทร: ${customer.phone}\n")
        }
        sb.append("-".repeat(LINE_WIDTH)).append("\n")

        for (item in items) {
            val itemLine = "${item.quantity}x ${item.name}"
            val priceStr = if (item.isBuffetIncluded) "Buffet" else item.subtotalSatang.toDisplayBaht()
            sb.append(twoColumns(itemLine, priceStr)).append("\n")
        }
        sb.append("-".repeat(LINE_WIDTH)).append("\n")

        sb.append(twoColumns("มูลค่าสินค้า/บริการ (Net Before Tax):", netBeforeTaxSatang.toDisplayBaht())).append("\n")
        if (discountSatang > 0L) {
            sb.append(twoColumns("ส่วนลด (Discount):", "-${discountSatang.toDisplayBaht()}")).append("\n")
        }
        sb.append(twoColumns("ภาษีมูลค่าเพิ่ม 7% (VAT Amount):", taxSatang.toDisplayBaht())).append("\n")
        sb.append("=".repeat(LINE_WIDTH)).append("\n")
        sb.append(twoColumns("จำนวนเงินรวมทั้งสิ้น (Total):", "฿${grandTotalSatang.toDisplayBaht()}")).append("\n")
        sb.append("=".repeat(LINE_WIDTH)).append("\n")

        for (p in payments) {
            sb.append(twoColumns("ชำระ (${p.method}):", p.amountSatang.toDisplayBaht())).append("\n")
        }
        sb.append("\n")
        sb.append(centerText("เอกสารออกโดยระบบ SunPOS อิเล็กทรอนิกส์")).append("\n")

        return sb.toString()
    }

    /**
     * Build ESC/POS Raw Binary Byte Array for Abbreviated Receipt.
     */
    fun buildAbbreviatedReceipt(
        companyName: String,
        branchName: String,
        branchCode: String,
        companyTaxId: String,
        posDeviceId: String,
        cashierName: String,
        orderNumber: String,
        tableNumber: String?,
        orderType: String,
        buffetTierName: String? = null,
        buffetAdults: Int = 0,
        buffetChildren: Int = 0,
        buffetHeadChargeSatang: Long = 0L,
        items: List<ReceiptItemLine>,
        grossSatang: Long,
        discountSatang: Long,
        taxSatang: Long,
        grandTotalSatang: Long,
        payments: List<ReceiptPaymentLine>,
        changeSatang: Long,
        customerName: String? = null,
        customerTier: String? = null,
        earnedPoints: Long = 0L
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(ESC_INIT)

        val text = formatAbbreviatedReceiptText(
            companyName = companyName,
            branchName = branchName,
            branchCode = branchCode,
            companyTaxId = companyTaxId,
            posDeviceId = posDeviceId,
            cashierName = cashierName,
            orderNumber = orderNumber,
            tableNumber = tableNumber,
            orderType = orderType,
            buffetTierName = buffetTierName,
            buffetAdults = buffetAdults,
            buffetChildren = buffetChildren,
            buffetHeadChargeSatang = buffetHeadChargeSatang,
            items = items,
            grossSatang = grossSatang,
            discountSatang = discountSatang,
            taxSatang = taxSatang,
            grandTotalSatang = grandTotalSatang,
            payments = payments,
            changeSatang = changeSatang,
            customerName = customerName,
            customerTier = customerTier,
            earnedPoints = earnedPoints
        )

        out.write(text.toByteArray(THAI_CHARSET))
        out.write(byteArrayOf(0x0A, 0x0A, 0x0A)) // 3 Feed lines
        out.write(ESC_FEED_AND_CUT)

        return out.toByteArray()
    }

    /**
     * Build ESC/POS Raw Binary Byte Array for Full Tax Invoice.
     */
    fun buildFullTaxInvoiceReceipt(
        invoiceNumber: String,
        companyName: String,
        branchName: String,
        branchCode: String,
        companyTaxId: String,
        companyAddress: String,
        posDeviceId: String,
        cashierName: String,
        orderNumber: String,
        customer: TaxInvoiceCustomer,
        items: List<ReceiptItemLine>,
        buffetHeadChargeSatang: Long = 0L,
        grossSatang: Long,
        discountSatang: Long,
        taxSatang: Long,
        grandTotalSatang: Long,
        payments: List<ReceiptPaymentLine>
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(ESC_INIT)

        val text = formatFullTaxInvoiceText(
            invoiceNumber = invoiceNumber,
            companyName = companyName,
            branchName = branchName,
            branchCode = branchCode,
            companyTaxId = companyTaxId,
            companyAddress = companyAddress,
            posDeviceId = posDeviceId,
            cashierName = cashierName,
            orderNumber = orderNumber,
            customer = customer,
            items = items,
            buffetHeadChargeSatang = buffetHeadChargeSatang,
            grossSatang = grossSatang,
            discountSatang = discountSatang,
            taxSatang = taxSatang,
            grandTotalSatang = grandTotalSatang,
            payments = payments
        )

        out.write(text.toByteArray(THAI_CHARSET))
        out.write(byteArrayOf(0x0A, 0x0A, 0x0A)) // 3 Feed lines
        out.write(ESC_FEED_AND_CUT)

        return out.toByteArray()
    }

    private fun centerText(text: String, width: Int = LINE_WIDTH): String {
        if (text.length >= width) return text
        val pad = (width - text.length) / 2
        return " ".repeat(pad) + text
    }

    private fun twoColumns(left: String, right: String, width: Int = LINE_WIDTH): String {
        val spaceNeeded = width - left.length - right.length
        return if (spaceNeeded > 0) {
            left + " ".repeat(spaceNeeded) + right
        } else {
            "$left $right"
        }
    }
}
