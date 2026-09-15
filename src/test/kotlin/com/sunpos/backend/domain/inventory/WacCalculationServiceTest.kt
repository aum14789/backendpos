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

    @Test
    fun `test replenishment of negative stock uses incoming receipt unit cost as new WAC`() {
        // Auto-provisioned negative stock: -2 kg @ estimated standard cost 50.0000
        val oldQty = BigDecimal("-2.0000")
        val oldCost = BigDecimal("50.0000")
        val recQty = BigDecimal("10.0000")
        val recCost = BigDecimal("60.0000")

        // Incoming goods replenish the negative balance (-2 + 10 = 8 kg remaining).
        // Remaining 8 kg cost basis is fully established by the receipt cost (60.0000).
        val newWac = wacService.calculateNewWac(oldQty, oldCost, recQty, recCost)
        assertEquals(BigDecimal("60.0000"), newWac)
    }

    @Test
    fun `test zero initial stock adopts receipt unit cost`() {
        val oldQty = BigDecimal("0.0000")
        val oldCost = BigDecimal("0.0000")
        val recQty = BigDecimal("5.0000")
        val recCost = BigDecimal("85.0000")

        val newWac = wacService.calculateNewWac(oldQty, oldCost, recQty, recCost)
        assertEquals(BigDecimal("85.0000"), newWac)
    }

    @Test
    fun `test receipt that does not fully offset negative stock retains receipt cost`() {
        // Was -10, received 3 @ 100.0000 -> net balance is still -7
        val oldQty = BigDecimal("-10.0000")
        val oldCost = BigDecimal("50.0000")
        val recQty = BigDecimal("3.0000")
        val recCost = BigDecimal("100.0000")

        val newWac = wacService.calculateNewWac(oldQty, oldCost, recQty, recCost)
        assertEquals(BigDecimal("100.0000"), newWac)
    }
}

