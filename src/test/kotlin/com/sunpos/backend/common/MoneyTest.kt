package com.sunpos.backend.common

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class MoneyTest {

    @Test
    fun `test money addition and scale preservation`() {
        val m1 = Money.of("100.50")
        val m2 = Money.of("49.50")
        val result = m1 + m2

        assertEquals(BigDecimal("150.0000"), result.amount)
        assertEquals(4, result.amount.scale())
    }

    @Test
    fun `test money multiplication`() {
        val price = Money.of("200.00")
        val quantity = BigDecimal("3.5")
        val total = price * quantity

        assertEquals(BigDecimal("700.0000"), total.amount)
    }

    @Test
    fun `test zero and positive predicates`() {
        val zero = Money.zero()
        val pos = Money.of("10.00")
        val neg = Money.of("-5.00")

        assertTrue(zero.isZero())
        assertTrue(pos.isPositive())
        assertTrue(neg.isNegative())
    }
}
