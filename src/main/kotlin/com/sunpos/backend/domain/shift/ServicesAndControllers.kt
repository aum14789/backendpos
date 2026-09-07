package com.sunpos.backend.domain.shift

import com.sunpos.backend.common.ApiResponse
import com.sunpos.backend.common.JdbcRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.Optional

@Repository
class CashierShiftRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<CashierShift>(jdbcTemplate, "cashier_shifts", CashierShift::class.java) {
    fun findByBranchIdAndDeviceIdAndStatus(branchId: String, deviceId: String, status: ShiftStatus): Optional<CashierShift> {
        val list = findByFields(mapOf("branchId" to branchId, "deviceId" to deviceId, "status" to status.name))
        return Optional.ofNullable(list.firstOrNull())
    }
    fun findByBranchId(branchId: String): List<CashierShift> = findByField("branchId", branchId)
}

@Repository
class CashMovementRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<CashMovement>(jdbcTemplate, "cash_movements", CashMovement::class.java) {
    fun findByShiftId(shiftId: String): List<CashMovement> = findByField("shiftId", shiftId)
}

@Service
class ShiftService(
    private val shiftRepository: CashierShiftRepository,
    private val movementRepository: CashMovementRepository
) {
    companion object {
        const val SCALE = 4
        val ROUNDING = RoundingMode.HALF_UP
    }

    @Transactional
    fun openShift(dto: OpenShiftDto): CashierShift {
        val existingOpt = shiftRepository.findByBranchIdAndDeviceIdAndStatus(dto.branchId, dto.deviceId, ShiftStatus.OPEN)
        if (existingOpt.isPresent) {
            return existingOpt.get()
        }

        val shift = CashierShift(
            branchId = dto.branchId,
            deviceId = dto.deviceId,
            userId = dto.userId,
            openingCash = dto.openingCash.setScale(SCALE, ROUNDING),
            expectedCash = dto.openingCash.setScale(SCALE, ROUNDING)
        )
        return shiftRepository.save(shift)
    }

    @Transactional
    fun recordCashMovement(dto: CashMovementDto): CashMovement {
        val shift = shiftRepository.findById(dto.shiftId).orElseThrow { IllegalArgumentException("Shift not found") }
        if (shift.status == ShiftStatus.CLOSED) {
            throw IllegalArgumentException("Cannot add cash movement to closed shift")
        }

        val amount = dto.amount.setScale(SCALE, ROUNDING)
        val movement = CashMovement(
            shiftId = dto.shiftId,
            movementType = dto.movementType,
            amount = amount,
            reason = dto.reason,
            createdBy = dto.createdBy
        )
        val savedMovement = movementRepository.save(movement)

        if (dto.movementType == CashMovementType.CASH_IN) {
            shift.cashIn = shift.cashIn.add(amount).setScale(SCALE, ROUNDING)
        } else {
            shift.cashOut = shift.cashOut.add(amount).setScale(SCALE, ROUNDING)
        }
        recalculateExpectedCash(shift)
        shiftRepository.save(shift)

        return savedMovement
    }

    @Transactional
    fun closeShift(dto: CloseShiftDto): CashierShift {
        val shift = shiftRepository.findById(dto.shiftId).orElseThrow { IllegalArgumentException("Shift not found") }
        if (shift.status == ShiftStatus.CLOSED) {
            return shift
        }

        recalculateExpectedCash(shift)

        val actual = dto.actualCash.setScale(SCALE, ROUNDING)
        val diff = actual.subtract(shift.expectedCash).setScale(SCALE, ROUNDING)

        shift.actualCash = actual
        shift.variance = diff
        shift.varianceType = when {
            diff.compareTo(BigDecimal.ZERO) > 0 -> VarianceType.OVER
            diff.compareTo(BigDecimal.ZERO) < 0 -> VarianceType.SHORT
            else -> VarianceType.ZERO
        }
        shift.closingNotes = dto.closingNotes
        shift.status = ShiftStatus.CLOSED
        shift.closedAt = Instant.now()

        return shiftRepository.save(shift)
    }

    fun getActiveShift(branchId: String, deviceId: String): CashierShift? {
        return shiftRepository.findByBranchIdAndDeviceIdAndStatus(branchId, deviceId, ShiftStatus.OPEN).orElse(null)
    }

    fun listShifts(branchId: String): List<CashierShift> {
        return shiftRepository.findByBranchId(branchId)
    }

    private fun recalculateExpectedCash(shift: CashierShift) {
        // expected_cash = opening_cash + cash_sales + cash_in - cash_out - refund_cash
        val expected = shift.openingCash
            .add(shift.cashSales)
            .add(shift.cashIn)
            .subtract(shift.cashOut)
            .subtract(shift.refundCash)
            .setScale(SCALE, ROUNDING)
        shift.expectedCash = expected
    }
}

@RestController
@RequestMapping("/api/v1/shifts")
class ShiftController(
    private val shiftService: ShiftService
) {
    @PostMapping("/open")
    @PreAuthorize("hasAuthority('SHIFT_OPEN') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_CASHIER')")
    fun openShift(@RequestBody dto: OpenShiftDto): ApiResponse<CashierShift> {
        return ApiResponse.success(shiftService.openShift(dto), "Shift opened successfully")
    }

    @PostMapping("/{id}/cash-movement")
    @PreAuthorize("hasAuthority('SHIFT_OPEN') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_CASHIER')")
    fun recordCashMovement(@PathVariable id: String, @RequestBody dto: CashMovementDto): ApiResponse<CashMovement> {
        return ApiResponse.success(shiftService.recordCashMovement(dto.copy(shiftId = id)), "Cash movement recorded successfully")
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('SHIFT_CLOSE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_CASHIER')")
    fun closeShift(@PathVariable id: String, @RequestBody dto: CloseShiftDto): ApiResponse<CashierShift> {
        return ApiResponse.success(shiftService.closeShift(dto.copy(shiftId = id)), "Shift closed successfully")
    }

    @GetMapping("/active")
    fun getActiveShift(@RequestParam branchId: String, @RequestParam deviceId: String): ApiResponse<CashierShift?> {
        return ApiResponse.success(shiftService.getActiveShift(branchId, deviceId))
    }

    @GetMapping
    fun listShifts(@RequestParam branchId: String): ApiResponse<List<CashierShift>> {
        return ApiResponse.success(shiftService.listShifts(branchId))
    }
}
