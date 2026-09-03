package sun.clientpos

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import sun.clientpos.data.local.dao.OrderDao
import sun.clientpos.data.local.dao.PaymentDao
import sun.clientpos.data.local.dao.ShiftDao
import sun.clientpos.data.local.entity.*
import sun.clientpos.data.repository.*

class MockOrderDao : OrderDao {
    val orders = mutableMapOf<String, RoomOrderEntity>()
    val items = mutableMapOf<String, RoomOrderItemEntity>()
    val modifiers = mutableMapOf<String, RoomOrderItemModifierEntity>()

    override suspend fun insertOrder(order: RoomOrderEntity) { orders[order.orderId] = order }
    override suspend fun insertOrderItem(item: RoomOrderItemEntity) { items[item.orderItemId] = item }
    override suspend fun insertOrderItemModifiers(modifiersList: List<RoomOrderItemModifierEntity>) {
        modifiersList.forEach { modifiers[it.orderItemModifierId] = it }
    }
    override suspend fun getOrderById(orderId: String): RoomOrderEntity? = orders[orderId]
    override fun observeActiveOrders(branchId: String): Flow<List<RoomOrderEntity>> = flowOf(orders.values.toList())
    override suspend fun getOrderItems(orderId: String): List<RoomOrderItemEntity> = items.values.filter { it.orderId == orderId }
    override suspend fun getItemModifiers(orderItemId: String): List<RoomOrderItemModifierEntity> = modifiers.values.filter { it.orderItemId == orderItemId }
    override suspend fun updateOrderStatus(orderId: String, status: String) {
        val o = orders[orderId]
        if (o != null) orders[orderId] = o.copy(status = status)
    }
    override suspend fun updateOrderCustomer(orderId: String, customerId: String?) {
        val o = orders[orderId]
        if (o != null) orders[orderId] = o.copy(customerId = customerId)
    }
    override suspend fun updateKitchenStatus(orderId: String, kitchenStatus: String) {
        val o = orders[orderId]
        if (o != null) orders[orderId] = o.copy(kitchenStatus = kitchenStatus)
    }
    override suspend fun updateOrderTable(orderId: String, tableId: String?, tableSessionId: String?) {
        val o = orders[orderId]
        if (o != null) orders[orderId] = o.copy(tableId = tableId, tableSessionId = tableSessionId)
    }
    override suspend fun updateOrderAmounts(orderId: String, subtotal: Long, discount: Long, serviceCharge: Long, tax: Long, total: Long) {
        val o = orders[orderId]
        if (o != null) orders[orderId] = o.copy(subtotalAmount = subtotal, discountAmount = discount, serviceChargeAmount = serviceCharge, taxAmount = tax, totalAmount = total)
    }
    override suspend fun updateOrderItemQuantity(orderItemId: String, quantity: Int, subtotal: Long) {
        val it = items[orderItemId]
        if (it != null) items[orderItemId] = it.copy(quantity = quantity, subtotal = subtotal)
    }
    override suspend fun updateOrderItemStatus(orderItemId: String, kitchenStatus: String, notes: String?) {
        val it = items[orderItemId]
        if (it != null) items[orderItemId] = it.copy(kitchenStatus = kitchenStatus, notes = notes)
    }
    override suspend fun updateOrderItemKitchenStatus(orderItemId: String, kitchenStatus: String) {
        val it = items[orderItemId]
        if (it != null) items[orderItemId] = it.copy(kitchenStatus = kitchenStatus)
    }
    override suspend fun deleteOrderItem(orderItemId: String) { items.remove(orderItemId) }
    override suspend fun reassignOrderItems(orderItemIds: List<String>, newOrderId: String) {
        for (id in orderItemIds) {
            val it = items[id]
            if (it != null) items[id] = it.copy(orderId = newOrderId)
        }
    }
}

class MockPaymentDao : PaymentDao {
    val payments = mutableMapOf<String, RoomPaymentTransactionEntity>()
    val refunds = mutableMapOf<String, RoomRefundTransactionEntity>()

    override suspend fun insertPayment(payment: RoomPaymentTransactionEntity) {
        payments[payment.paymentId] = payment
    }
    override suspend fun insertRefund(refund: RoomRefundTransactionEntity) {
        refunds[refund.refundId] = refund
    }
    override suspend fun getPaymentsByOrder(orderId: String): List<RoomPaymentTransactionEntity> =
        payments.values.filter { it.orderId == orderId }
    override fun observePayments(branchId: String): Flow<List<RoomPaymentTransactionEntity>> =
        flowOf(payments.values.toList())
    override suspend fun updatePaymentStatus(paymentId: String, status: String) {
        val p = payments[paymentId]
        if (p != null) payments[paymentId] = p.copy(status = status)
    }
}

class MockShiftDao : ShiftDao {
    val shifts = mutableMapOf<String, RoomCashierShiftEntity>()
    val movements = mutableMapOf<String, RoomCashMovementEntity>()

    override suspend fun insertShift(shift: RoomCashierShiftEntity) { shifts[shift.shiftId] = shift }
    override suspend fun insertCashMovement(movement: RoomCashMovementEntity) {
        movements[movement.movementId] = movement
    }
    override suspend fun getActiveShift(branchId: String, deviceId: String): RoomCashierShiftEntity? =
        shifts.values.find { it.branchId == branchId && it.deviceId == deviceId && it.status == "OPEN" }
    override suspend fun getShiftById(shiftId: String): RoomCashierShiftEntity? = shifts[shiftId]
    override suspend fun getCashMovements(shiftId: String): List<RoomCashMovementEntity> =
        movements.values.filter { it.shiftId == shiftId }
}

class OrderLifecycleAndPaymentOutboxTest {

    private lateinit var orderDao: MockOrderDao
    private lateinit var paymentDao: MockPaymentDao
    private lateinit var shiftDao: MockShiftDao
    private lateinit var outboxDao: FakeSyncOutboxDao
    private lateinit var orderRepo: OrderRepository
    private lateinit var paymentRepo: PaymentRepository
    private lateinit var shiftRepo: ShiftRepository

    @Before
    fun setUp() {
        orderDao = MockOrderDao()
        paymentDao = MockPaymentDao()
        shiftDao = MockShiftDao()
        outboxDao = FakeSyncOutboxDao()
        orderRepo = OrderRepository(orderDao, outboxDao)
        paymentRepo = PaymentRepository(paymentDao, orderDao, outboxDao)
        shiftRepo = ShiftRepository(shiftDao, outboxDao)
    }

    @Test
    fun testCompleteOrderCreationToPaymentSettlementFlow() = runBlocking {
        val branchId = "branch-001"
        val deviceId = "pos-dev-01"

        // 1. Create Order with items (2x ฿120.00 = 24000 satang)
        val items = listOf(
            LocalOrderItemRequest(
                menuItemId = "item-rice",
                nameSnapshot = "Crab Fried Rice",
                unitPriceSnapshot = 12000L,
                quantity = 2
            )
        )
        val (order, pricing) = orderRepo.createOrderLocal(
            branchId = branchId,
            tableId = "tbl-01",
            tableSessionId = "sess-01",
            orderType = "DINE_IN",
            channel = "POS",
            createdBy = "cashier_john",
            items = items,
            deviceId = deviceId
        )

        assertEquals("OPEN", order.status)
        assertEquals(24000L, order.totalAmount)
        assertEquals(24000L, pricing.grandTotal)

        // Verify ORDER_CREATED outbox event was generated
        val pendingEvents1 = outboxDao.getPendingEvents(10)
        assertTrue(pendingEvents1.any { it.eventType == "ORDER_CREATED" && it.aggregateId == order.orderId })

        // 2. Apply Manual Discount of ฿40.00 (4000 satang)
        val discountedOrder = orderRepo.applyManualDiscountLocal(
            orderId = order.orderId,
            manualDiscountSatang = 4000L,
            reason = "Manager special discount",
            authorizedBy = "manager_ann",
            deviceId = deviceId,
            branchId = branchId
        )
        assertEquals(20000L, discountedOrder.totalAmount) // 240 - 40 = ฿200.00 (20000 satang)

        // Verify ORDER_UPDATED outbox event
        val pendingEvents2 = outboxDao.getPendingEvents(10)
        assertTrue(pendingEvents2.any { it.eventType == "ORDER_UPDATED" && it.aggregateId == order.orderId })

        // 3. Process Full Payment (฿200.00 via PromptPay QR)
        val payment = paymentRepo.processPaymentLocal(
            orderId = order.orderId,
            branchId = branchId,
            deviceId = deviceId,
            shiftId = "shift-01",
            paymentMethod = "PROMPTPAY",
            amount = 20000L,
            tenderedAmount = 20000L,
            orderTotalAmount = discountedOrder.totalAmount,
            createdBy = "cashier_john"
        )
        assertEquals("SUCCESS", payment.status)
        assertEquals(20000L, payment.amount)

        // Verify Order status transitioned to COMPLETED
        val completedOrder = orderDao.getOrderById(order.orderId)
        assertNotNull(completedOrder)
        assertEquals("COMPLETED", completedOrder?.status)

        // Verify PAYMENT_COMPLETED and ORDER_COMPLETED outbox events were generated
        val pendingEvents3 = outboxDao.getPendingEvents(10)
        assertTrue(pendingEvents3.any { it.eventType == "PAYMENT_COMPLETED" && it.aggregateId == payment.paymentId })
        assertTrue(pendingEvents3.any { it.eventType == "ORDER_COMPLETED" && it.aggregateId == order.orderId })
    }

    @Test
    fun testCashierShiftOpenCloseAndVarianceFlow() = runBlocking {
        val branchId = "branch-001"
        val deviceId = "pos-dev-01"

        // 1. Open Shift with ฿2,000.00 opening float (200000 satang)
        val openedShift = shiftRepo.openShiftLocal(
            branchId = branchId,
            deviceId = deviceId,
            userId = "usr-cashier",
            openingCash = 200000L
        )
        assertEquals("OPEN", openedShift.status)
        assertEquals(200000L, openedShift.expectedCash)

        // Verify SHIFT_OPENED outbox event
        val pendingEvents1 = outboxDao.getPendingEvents(10)
        assertTrue(pendingEvents1.any { it.eventType == "SHIFT_OPENED" && it.aggregateId == openedShift.shiftId })

        // 2. Close Shift with actual counted cash ฿2,050.00 (205000 satang -> ฿50.00 OVER)
        val closedShift = shiftRepo.closeShiftLocal(
            shiftId = openedShift.shiftId,
            actualCash = 205000L,
            closingNotes = "EndOfDay count verified",
            deviceId = deviceId,
            branchId = branchId
        )
        assertEquals("CLOSED", closedShift.status)
        assertEquals(5000L, closedShift.variance) // +฿50.00 OVER
        assertEquals("OVER", closedShift.varianceType)

        // Verify SHIFT_CLOSED outbox event
        val pendingEvents2 = outboxDao.getPendingEvents(10)
        assertTrue(pendingEvents2.any { it.eventType == "SHIFT_CLOSED" && it.aggregateId == openedShift.shiftId })
    }
}
