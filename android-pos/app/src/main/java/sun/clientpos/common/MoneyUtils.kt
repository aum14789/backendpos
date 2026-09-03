package sun.clientpos.common

import java.text.DecimalFormat

/**
 * Money utilities for Android POS.
 *
 * All monetary values are stored as Long in minor units (satang).
 *   1 Baht = 100 Satang
 *   e.g. ฿123.50 is stored as 12350L
 *
 * This eliminates floating-point rounding errors for financial calculations.
 */
object MoneyUtils {

    private const val SATANG_PER_BAHT = 100L
    private val displayFormat = DecimalFormat("#,##0.00")

    /**
     * Convert satang (Long) to display string "123.50"
     */
    fun Long.toDisplayBaht(): String {
        val baht = this.toDouble() / SATANG_PER_BAHT
        return displayFormat.format(baht)
    }

    /**
     * Convert satang (Long) to display string with symbol "฿123.50"
     */
    fun Long.toDisplayBahtWithSymbol(): String {
        return "฿${this.toDisplayBaht()}"
    }

    /**
     * Parse user input string (baht) to satang Long.
     * e.g. "123.50" -> 12350L, "200" -> 20000L
     * Returns 0L if input is invalid.
     */
    fun String.parseBahtToSatang(): Long {
        val cleaned = this.replace(",", "").trim()
        if (cleaned.isEmpty()) return 0L
        return try {
            val baht = cleaned.toDouble()
            Math.round(baht * SATANG_PER_BAHT)
        } catch (_: NumberFormatException) {
            0L
        }
    }

    /**
     * Create satang value from baht integer.
     * e.g. bahtToSatang(100) -> 10000L
     */
    fun bahtToSatang(baht: Int): Long = baht.toLong() * SATANG_PER_BAHT

    /**
     * Create satang value from baht and satang components.
     * e.g. toSatang(123, 50) -> 12350L
     */
    fun toSatang(baht: Long, satang: Int = 0): Long = (baht * SATANG_PER_BAHT) + satang

    /**
     * Multiply satang amount by an integer quantity.
     * e.g. 5000L.timesSatang(3) -> 15000L (฿50.00 × 3 = ฿150.00)
     */
    fun Long.timesSatang(quantity: Int): Long = this * quantity

    /**
     * Format satang Long for JSON payload (plain number, no formatting).
     * This is what gets sent to the backend in outbox payloads.
     */
    fun Long.toPayloadString(): String = this.toString()
}
