package com.sunpos.backend.domain.inventory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class WacCalculationServiceTest {

    private val wacService = WacCalculationService()

    @Test
    fun `test weighted average cost calculation`() {
        val oldQty = BigDecimal("10.0000")
        val oldCost = BigDecimal("150.0000")
        val recQty = BigDecimal("10.0000")
        val recCost = BigDecimal("170.0000")

        // New WAC = (10*150 + 10*170) / (10+10) = 3200 / 20 = 160.0000
        val newWac = wacService.calculateNewWac(oldQty, oldCost, recQty, recCost)
        assertEquals(BigDecimal("160.0000"), newWac)
    }
}
