package com.sunpos.backend.common

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Immutable Monetary Value Object.
 * Enforces exact decimal calculations and prevents floating-point inaccuracies.
 */
data class Money(
    val amount: BigDecimal = BigDecimal.ZERO.setScale(DECIMAL_SCALE, ROUNDING_MODE)
) {
    init {
        require(amount.scale() == DECIMAL_SCALE) {
            "Money scale must be $DECIMAL_SCALE, but was ${amount.scale()}"
        }
    }

    operator fun plus(other: Money): Money = Money(amount.add(other.amount).setScale(DECIMAL_SCALE, ROUNDING_MODE))
    operator fun minus(other: Money): Money = Money(amount.subtract(other.amount).setScale(DECIMAL_SCALE, ROUNDING_MODE))
    operator fun times(factor: BigDecimal): Money = Money(amount.multiply(factor).setScale(DECIMAL_SCALE, ROUNDING_MODE))
    operator fun times(factor: Int): Money = this * BigDecimal(factor)

    fun isZero(): Boolean = amount.compareTo(BigDecimal.ZERO) == 0
    fun isPositive(): Boolean = amount > BigDecimal.ZERO
    fun isNegative(): Boolean = amount < BigDecimal.ZERO

    companion object {
        const val DECIMAL_SCALE = 4
        val ROUNDING_MODE = RoundingMode.HALF_UP

        fun of(valString: String): Money = Money(BigDecimal(valString).setScale(DECIMAL_SCALE, ROUNDING_MODE))
        fun of(valDouble: Double): Money = Money(BigDecimal.valueOf(valDouble).setScale(DECIMAL_SCALE, ROUNDING_MODE))
        fun zero(): Money = Money(BigDecimal.ZERO.setScale(DECIMAL_SCALE, ROUNDING_MODE))
    }
}
