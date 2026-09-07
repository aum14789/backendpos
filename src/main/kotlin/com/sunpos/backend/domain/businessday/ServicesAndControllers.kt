package com.sunpos.backend.domain.businessday

import com.sunpos.backend.common.ApiResponse
import com.sunpos.backend.common.JdbcRepository
import org.springframework.jdbc.core.JdbcTemplate
import com.sunpos.backend.domain.order.OrderRepository
import com.sunpos.backend.domain.order.OrderStatus
import com.sunpos.backend.domain.payment.PaymentMethod
import com.sunpos.backend.domain.payment.PaymentStatus
import com.sunpos.backend.domain.payment.PaymentTransactionRepository
import com.sunpos.backend.domain.payment.RefundTransactionRepository
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Repository
class BusinessDayRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<BusinessDay>(jdbcTemplate, "business_days", BusinessDay::class.java) {
    fun findByBranchIdAndStatus(branchId: String, status: BusinessDayStatus): List<BusinessDay> =
        findByFields(mapOf("branchId" to branchId, "status" to status.name))
    fun findByBranchIdOrderByBusinessDateDesc(branchId: String): List<BusinessDay> =
        findByField("branchId", branchId).sortedByDescending { it.businessDate }
}

@Service
class BusinessDayService(
    private val businessDayRepository: BusinessDayRepository,
    private val orderRepository: OrderRepository,
    private val paymentRepository: PaymentTransactionRepository,
    private val refundRepository: RefundTransactionRepository,
    private val businessDayResolver: BusinessDayResolver,
    private val clock: BusinessDayClock,
    private val inventoryEodConsumptionService: com.sunpos.backend.domain.recipe.InventoryEodConsumptionService,
    private val warehouseRepository: com.sunpos.backend.domain.inventory.WarehouseRepository
) {
    companion object {
        const val SCALE = 4
        val ROUNDING = RoundingMode.HALF_UP
    }

    @Transactional
    fun getOrCreateOpenBusinessDay(branchId: String): BusinessDay {
        val activeList = businessDayRepository.findByBranchIdAndStatus(branchId, BusinessDayStatus.OPEN)
        if (activeList.isNotEmpty()) {
            return activeList.first()
        }

        // Default timezone for branches is Asia/Bangkok
        val zoneId = ZoneId.of("Asia/Bangkok")
        val businessDate = businessDayResolver.resolveBusinessDate(clock.instant(), zoneId, "02:00")

        val bday = BusinessDay(
            branchId = branchId,
            businessDate = businessDate.toString(),
            closingTimeSetting = "02:00",
            status = BusinessDayStatus.OPEN
        )
        return businessDayRepository.save(bday)
    }

    @Transactional
    fun closeBusinessDayEod(branchId: String, closedBy: String?): BusinessDay {
        val openDays = businessDayRepository.findByBranchIdAndStatus(branchId, BusinessDayStatus.OPEN)
        val day = if (openDays.isNotEmpty()) openDays.first() else getOrCreateOpenBusinessDay(branchId)

        // Idempotent & Resumable EOD calculation
        day.status = BusinessDayStatus.PROCESSING
        businessDayRepository.save(day)

        // Trigger stock consumption for all warehouses in this branch
        val warehouses = warehouseRepository.findByBranchId(branchId)
        for (wh in warehouses) {
            inventoryEodConsumptionService.consumeBusinessDaySales(day.id, branchId, wh.id, closedBy)
        }

        // Aggregate completed orders for this business day
        val completedOrders = orderRepository.findByBranchId(branchId)
            .filter { it.businessDayId == day.id && (it.status == OrderStatus.COMPLETED || it.status == OrderStatus.SERVED || it.status == OrderStatus.READY) }
        val totalSales = completedOrders.map { it.totalAmount }.fold(BigDecimal.ZERO) { acc, a -> acc.add(a) }.setScale(SCALE, ROUNDING)

        // Aggregate payments for this business day
        val orderIds = completedOrders.map { it.id }.toSet()
        val payments = paymentRepository.findByBranchId(branchId)
            .filter { it.orderId in orderIds && (it.status == PaymentStatus.SUCCESS || it.status == PaymentStatus.PARTIALLY_REFUNDED) }
        val cashPay = payments.filter { it.paymentMethod == PaymentMethod.CASH }.map { it.amount }.fold(BigDecimal.ZERO) { acc, a -> acc.add(a) }.setScale(SCALE, ROUNDING)
        val nonCashPay = payments.filter { it.paymentMethod != PaymentMethod.CASH }.map { it.amount }.fold(BigDecimal.ZERO) { acc, a -> acc.add(a) }.setScale(SCALE, ROUNDING)

        // Aggregate refunds for this business day
        val totalRefunds = payments.filter { it.status == PaymentStatus.REFUNDED || it.status == PaymentStatus.PARTIALLY_REFUNDED }
            .map { it.amount }.fold(BigDecimal.ZERO) { acc, a -> acc.add(a) }.setScale(SCALE, ROUNDING)

        day.totalSales = totalSales
        day.totalCashPayments = cashPay
        day.totalNonCashPayments = nonCashPay
        day.totalRefunds = totalRefunds
        day.status = BusinessDayStatus.CLOSED
        day.closedAt = Instant.now()
        day.closedBy = closedBy

        return businessDayRepository.save(day)
    }

    fun listBusinessDays(branchId: String): List<BusinessDay> {
        return businessDayRepository.findByBranchIdOrderByBusinessDateDesc(branchId)
    }
}

@RestController
@RequestMapping("/api/v1/business-day")
class BusinessDayController(
    private val businessDayService: BusinessDayService
) {
    @GetMapping("/current")
    fun getCurrentBusinessDay(@RequestParam branchId: String): ApiResponse<BusinessDay> {
        return ApiResponse.success(businessDayService.getOrCreateOpenBusinessDay(branchId))
    }

    @PostMapping("/close-eod")
    @PreAuthorize("hasAuthority('BUSINESS_DAY_CLOSE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun closeEod(@RequestBody dto: CloseEodDto): ApiResponse<BusinessDay> {
        return ApiResponse.success(businessDayService.closeBusinessDayEod(dto.branchId, dto.closedBy), "Business Day EOD closed successfully")
    }

    @GetMapping
    fun listBusinessDays(@RequestParam branchId: String): ApiResponse<List<BusinessDay>> {
        return ApiResponse.success(businessDayService.listBusinessDays(branchId))
    }
}
