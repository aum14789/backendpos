package com.sunpos.backend.domain.shift

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ShiftServiceTest {

    @Autowired
    private lateinit var shiftService: ShiftService

    @Test
    fun `test shift opening, cash in out, expected cash calculation, and zero variance close`() {
        val shift = shiftService.openShift(
            OpenShiftDto(branchId = "branch-001", deviceId = "dev-test-01", userId = "user-001", openingCash = BigDecimal("1000.00"))
        )
        assertNotNull(shift.id)
        assertEquals(ShiftStatus.OPEN, shift.status)
        assertEquals(BigDecimal("1000.0000"), shift.expectedCash)

        // Record Cash In 200.00
        shiftService.recordCashMovement(
            CashMovementDto(shiftId = shift.id, movementType = CashMovementType.CASH_IN, amount = BigDecimal("200.00"), reason = "Petty cash deposit")
        )

        // Record Cash Out 100.00
        shiftService.recordCashMovement(
            CashMovementDto(shiftId = shift.id, movementType = CashMovementType.CASH_OUT, amount = BigDecimal("100.00"), reason = "Ice purchase")
        )

        // Expected Cash = 1,000 + 200 - 100 = 1,100.00
        val closedShift = shiftService.closeShift(
            CloseShiftDto(shiftId = shift.id, actualCash = BigDecimal("1100.00"), closingNotes = "Clean shift")
        )
        assertEquals(ShiftStatus.CLOSED, closedShift.status)
        assertEquals(BigDecimal("1100.0000"), closedShift.expectedCash)
        assertEquals(BigDecimal("0.0000"), closedShift.variance)
        assertEquals(VarianceType.ZERO, closedShift.varianceType)
    }

    @Test
    fun `test shift close cash short variance`() {
        val shift = shiftService.openShift(
            OpenShiftDto(branchId = "branch-001", deviceId = "dev-test-02", userId = "user-002", openingCash = BigDecimal("1000.00"))
        )

        // Expected 1,000, Actual 950 -> Short by 50
        val closedShift = shiftService.closeShift(
            CloseShiftDto(shiftId = shift.id, actualCash = BigDecimal("950.00"))
        )
        assertEquals(VarianceType.SHORT, closedShift.varianceType)
        assertEquals(BigDecimal("-50.0000"), closedShift.variance)
    }
}
