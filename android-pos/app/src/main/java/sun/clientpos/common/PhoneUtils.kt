package sun.clientpos.common

object PhoneUtils {

    /**
     * Normalize Thai phone numbers to clean standard digits (e.g. 0812345678).
     * Handles:
     * - "081-234-5678"    -> "0812345678"
     * - "081 234 5678"    -> "0812345678"
     * - "+66812345678"    -> "0812345678"
     * - "+66 81-234-5678" -> "0812345678"
     * - "66812345678"     -> "0812345678"
     * - "(081) 234-5678"  -> "0812345678"
     */
    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val trimmed = raw.trim()

        if (trimmed.startsWith("+66")) {
            val withoutPlus66 = trimmed.substring(3).replace(Regex("[^0-9]"), "")
            return "0$withoutPlus66"
        }

        val digitsOnly = trimmed.replace(Regex("[^0-9]"), "")
        if (digitsOnly.startsWith("66") && (digitsOnly.length == 11 || digitsOnly.length == 12)) {
            return "0" + digitsOnly.substring(2)
        }

        return digitsOnly
    }
}
