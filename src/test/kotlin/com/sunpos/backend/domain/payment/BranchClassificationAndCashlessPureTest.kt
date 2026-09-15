package com.sunpos.backend.domain.payment

import com.sunpos.backend.domain.crm.CrmService
import com.sunpos.backend.domain.order.*
import com.sunpos.backend.domain.organization.*
import com.sunpos.backend.domain.shift.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.util.*

class BranchClassificationAndCashlessPureTest {

    class FakePaymentTransactionRepository : PaymentTransactionRepository(mock(JdbcTemplate::class.java)) {
        val payments = mutableMapOf<String, PaymentTransaction>()
        override fun save(entity: PaymentTransaction): PaymentTransaction {
            payments[entity.id] = entity
            return entity
        }
        override fun findByOrderId(orderId: String): List<PaymentTransaction> =
            payments.values.filter { it.orderId == orderId }
        override fun findByIdempotencyKey(key: String): Optional<PaymentTransaction> =
            Optional.ofNullable(payments.values.firstOrNull { it.idempotencyKey == key })
    }

    class FakeRefundTransactionRepository : RefundTransactionRepository(mock(JdbcTemplate::class.java))

    class FakeOrderRepository : OrderRepository(mock(JdbcTemplate::class.java)) {
        val orders = mutableMapOf<String, Order>()
        override fun save(entity: Order): Order {
            orders[entity.id] = entity
            return entity
        }
        override fun findById(id: Any): Optional<Order> = Optional.ofNullable(orders[id.toString()])
    }

    class FakeBranchRepository : BranchRepository(mock(JdbcTemplate::class.java)) {
        val branches = mutableMapOf<String, Branch>()
        override fun save(entity: Branch): Branch {
            branches[entity.id] = entity
            return entity
        }
        override fun findById(id: Any): Optional<Branch> = Optional.ofNullable(branches[id.toString()])
    }

    class FakeCashierShiftRepository : CashierShiftRepository(mock(JdbcTemplate::class.java)) {
        val shifts = mutableMapOf<String, CashierShift>()
        override fun save(entity: CashierShift): CashierShift {
            shifts[entity.id] = entity
            return entity
        }
        override fun findByBranchIdAndDeviceIdAndStatus(branchId: String, deviceId: String, status: ShiftStatus): Optional<CashierShift> =
            Optional.ofNullable(shifts.values.firstOrNull { it.branchId == branchId && it.deviceId == deviceId && it.status == status })
        override fun findById(id: Any): Optional<CashierShift> = Optional.ofNullable(shifts[id.toString()])
    }

    class FakeCashMovementRepository : CashMovementRepository(mock(JdbcTemplate::class.java))

    private lateinit var paymentRepository: FakePaymentTransactionRepository
    private lateinit var refundRepository: FakeRefundTransactionRepository
    private lateinit var orderRepository: FakeOrderRepository
    private lateinit var branchRepository: FakeBranchRepository
    private lateinit var shiftRepository: FakeCashierShiftRepository
    private lateinit var movementRepository: FakeCashMovementRepository
    private lateinit var crmService: CrmService
    private lateinit var paymentService: PaymentService
    private lateinit var shiftService: ShiftService

    @BeforeEach
    fun setUp() {
        paymentRepository = FakePaymentTransactionRepository()
        refundRepository = FakeRefundTransactionRepository()
        orderRepository = FakeOrderRepository()
        branchRepository = FakeBranchRepository()
        shiftRepository = FakeCashierShiftRepository()
        movementRepository = FakeCashMovementRepository()
        crmService = mock(CrmService::class.java)

        paymentService = PaymentService(
            paymentRepository = paymentRepository,
            refundRepository = refundRepository,
            orderRepository = orderRepository,
            crmService = crmService,
            branchRepository = branchRepository
        )

        shiftService = ShiftService(
            shiftRepository = shiftRepository,
            movementRepository = movementRepository,
            branchRepository = branchRepository
        )
    }

    @Test
    fun `test branch and brand default lifecycle and cashless configurations`() {
        val branch = Branch(name = "Central World", code = "BKK01")
        assertEquals(false, branch.isTestBranch)
        assertEquals("PRE_OPENING", branch.status)
        assertEquals(true, branch.allowCashPayment)
        assertEquals(0L, branch.invoiceSequenceNumber)

        val brand = Brand(name = "Sun Yakiniku", code = "SYK")
        assertEquals(true, brand.allowCashPayment)
    }

    @Test
    fun `test marketing payment tender successfully settles order without change`() {
        val branch = Branch(id = "branch-01", name = "Test Branch", status = "PRE_OPENING")
        branchRepository.save(branch)

        val order = Order(
            id = "order-001",
            branchId = branch.id,
            totalAmount = BigDecimal("500.0000"),
            status = OrderStatus.OPEN,
            financialStatus = FinancialStatus.UNPAID
        )
        orderRepository.save(order)

        val request = PaymentRequestDto(
            orderId = order.id,
            branchId = branch.id,
            paymentMethod = PaymentMethod.MARKETING,
            amount = BigDecimal("500.00"),
            tenderedAmount = BigDecimal("500.00"),
            externalRef = "Staff Training Pre-Opening"
        )

        val response = paymentService.processPayment(request)

        assertEquals(PaymentStatus.SUCCESS, response.status)
        assertEquals(PaymentMethod.MARKETING, response.paymentMethod)
        assertEquals(BigDecimal("500.0000"), response.amount)
        assertEquals(BigDecimal("0.0000"), response.changeAmount)

        val updatedOrder = orderRepository.findById(order.id).get()
        assertEquals(FinancialStatus.PAID, updatedOrder.financialStatus)
        assertEquals(OrderStatus.COMPLETED, updatedOrder.status)
    }

    @Test
    fun `test cashless branch rejects cash payment with illegal argument exception`() {
        val branch = Branch(id = "branch-cashless", name = "Cashless Store", allowCashPayment = false)
        branchRepository.save(branch)

        val order = Order(
            id = "order-002",
            branchId = branch.id,
            totalAmount = BigDecimal("300.0000")
        )
        orderRepository.save(order)

        val request = PaymentRequestDto(
            orderId = order.id,
            branchId = branch.id,
            paymentMethod = PaymentMethod.CASH,
            amount = BigDecimal("300.00")
        )

        val exception = assertThrows(IllegalArgumentException::class.java) {
            paymentService.processPayment(request)
        }
        assertEquals("Cash payments are disabled for this branch", exception.message)
    }

    @Test
    fun `test cashless branch resets opening cash float to zero when opening shift`() {
        val branch = Branch(id = "branch-cashless", name = "Cashless Store", allowCashPayment = false)
        branchRepository.save(branch)

        val openShiftDto = OpenShiftDto(
            branchId = branch.id,
            deviceId = "dev-01",
            userId = "user-01",
            openingCash = BigDecimal("2000.00") // Cashier tried to enter 2,000 float
        )

        val shift = shiftService.openShift(openShiftDto)

        assertEquals(BigDecimal("0.0000"), shift.openingCash)
        assertEquals(BigDecimal("0.0000"), shift.expectedCash)
        assertEquals(ShiftStatus.OPEN, shift.status)
    }
}
