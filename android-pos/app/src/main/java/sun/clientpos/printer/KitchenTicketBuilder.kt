package sun.clientpos.printer

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*

data class KitchenTicketItem(
    val name: String,
    val quantity: Int,
    val notes: String? = null,
    val modifiers: List<String> = emptyList()
)

/**
 * Kitchen Ticket ESC/POS Builder for 80mm thermal station printers.
 * Formats order items clearly with checkboxes, large table numbers, and cooking notes.
 */
object KitchenTicketBuilder {

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
    private val ESC_INIT = byteArrayOf(0x1B, 0x40)
    private val ESC_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    private val ESC_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    private val ESC_BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
    private val ESC_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
    private val ESC_DOUBLE_SIZE_ON = byteArrayOf(0x1D, 0x21, 0x11) // Double Width + Height
    private val ESC_DOUBLE_SIZE_OFF = byteArrayOf(0x1D, 0x21, 0x00)
    private val ESC_FEED_AND_CUT = byteArrayOf(0x1D, 0x56, 0x42, 0x00)

    /**
     * Build formatted Kitchen Ticket.
     */
    fun buildKitchenTicket(
        stationName: String = "ครัวหลัก (MAIN KITCHEN)",
        tableNumber: String?,
        orderNumber: String,
        orderType: String,
        serverName: String?,
        items: List<KitchenTicketItem>
    ): ByteArray {
        val stream = ByteArrayOutputStream()
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US)
        val now = dateFormat.format(Date())

        stream.write(ESC_INIT)

        // 1. Station Header
        stream.write(ESC_ALIGN_CENTER)
        stream.write(ESC_BOLD_ON)
        stream.writeLine("=== $stationName ===")
        stream.write(ESC_BOLD_OFF)

        // 2. Big Table / Order Type Banner
        stream.write(ESC_DOUBLE_SIZE_ON)
        val tableLabel = if (!tableNumber.isNullOrBlank()) "โต๊ะ: $tableNumber" else "ออเดอร์: $orderType"
        stream.writeLine(tableLabel)
        stream.write(ESC_DOUBLE_SIZE_OFF)

        // 3. Order Info
        stream.write(ESC_ALIGN_LEFT)
        stream.writeLine("------------------------------------------------")
        stream.writeLine("เลขที่: $orderNumber    เวลา: $now")
        stream.writeLine("ประเภท: $orderType    พนักงาน: ${serverName ?: "Cashier"}")
        stream.writeLine("================================================")

        // 4. Kitchen Item Lines
        stream.write(ESC_BOLD_ON)
        for (item in items) {
            stream.writeLine("[ ] ${item.quantity}x  ${item.name}")
            for (mod in item.modifiers) {
                stream.writeLine("      + $mod")
            }
            if (!item.notes.isNullOrBlank()) {
                stream.writeLine("      ** หมายเหตุ: ${item.notes} **")
            }
            stream.writeLine("")
        }
        stream.write(ESC_BOLD_OFF)

        // 5. Footer & Cut
        stream.writeLine("================================================")
        stream.write(ESC_ALIGN_CENTER)
        stream.writeLine("--- จบรายการอาหาร ---")
        stream.writeLine("\n\n")

        stream.write(ESC_FEED_AND_CUT)

        return stream.toByteArray()
    }

    private fun ByteArrayOutputStream.writeLine(text: String) {
        val bytes = "$text\n".toByteArray(THAI_CHARSET)
        this.write(bytes)
    }
}
