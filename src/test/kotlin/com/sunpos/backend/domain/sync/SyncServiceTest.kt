package com.sunpos.backend.domain.sync

import com.sunpos.backend.domain.crm.CrmService
import com.sunpos.backend.domain.order.FinancialStatus
import com.sunpos.backend.domain.order.OrderRepository
import com.sunpos.backend.domain.order.OrderStatus
import com.sunpos.backend.domain.payment.PaymentStatus
import com.sunpos.backend.domain.payment.PaymentTransactionRepository
import com.sunpos.backend.domain.promotion.CouponService
import com.sunpos.backend.domain.promotion.CreateCouponRequestDto
import com.sunpos.backend.domain.shift.CashierShiftRepository
import com.sunpos.backend.domain.shift.ShiftStatus
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SyncServiceTest {

    @Autowired
    private lateinit var syncService: SyncService

    @Autowired
    private lateinit var orderRepository: OrderRepository

    @Autowired
    private lateinit var paymentRepository: PaymentTransactionRepository

    @Autowired
    private lateinit var shiftRepository: CashierShiftRepository

    @Autowired
    private lateinit var crmService: CrmService

    @Autowired
    private lateinit var couponService: CouponService

    @Test
    fun `test offline PUSH batch idempotency, domain entity application, and duplicate filtering`() {
        val branchId = "branch-001"
        val deviceId = "pos-device-test-01"

        val event1Id = UUID.randomUUID().toString()
        val event2Id = UUID.randomUUID().toString()
        val event3Id = UUID.randomUUID().toString()
        val event4Id = UUID.randomUUID().toString()

        val orderId = "ord-sync-100"
        val paymentId = "pay-sync-100"
        val shiftId = "shift-sync-100"

        // 1. Shift Opened Event (Opening float ฿2,000.00 = 200000 satang)
        val shiftOpenedEvent = SyncEventDto(
            eventId = event1Id,
            aggregateType = "SHIFT",
            aggregateId = shiftId,
            eventType = "SHIFT_OPENED",
            deviceId = deviceId,
            branchId = branchId,
            payload = """{"shiftId":"$shiftId","userId":"cashier_1","openingCashSatang":200000}""",
            createdAt = Instant.now()
        )

        // 2. Order Created Event (฿350.00 = 35000 satang)
        val orderCreatedEvent = SyncEventDto(
            eventId = event2Id,
            aggregateType = "ORDER",
            aggregateId = orderId,
            eventType = "ORDER_CREATED",
            deviceId = deviceId,
            branchId = branchId,
            payload = """{"orderId":"$orderId","orderNumber":"ORD-OFF-101","orderType":"DINE_IN","totalSatang":35000,"grossSatang":35000}""",
            createdAt = Instant.now()
        )

        // 3. Payment Completed Event (฿350.00 PromptPay)
        val paymentCompletedEvent = SyncEventDto(
            eventId = event3Id,
            aggregateType = "PAYMENT",
            aggregateId = paymentId,
            eventType = "PAYMENT_COMPLETED",
            deviceId = deviceId,
            branchId = branchId,
            payload = """{"paymentId":"$paymentId","orderId":"$orderId","method":"PROMPTPAY","amountSatang":35000,"tenderedSatang":35000,"changeSatang":0}""",
            createdAt = Instant.now()
        )

        // Push Initial Batch 1
        val result1 = syncService.processPush(SyncPushRequest(listOf(shiftOpenedEvent, orderCreatedEvent, paymentCompletedEvent)))
        assertEquals(3, result1.processedEventIds.size)
        assertEquals(0, result1.duplicateEventIds.size)

        // Verify Domain Entities were created in DB
        val createdOrder = orderRepository.findById(orderId).orElse(null)
        assertNotNull(createdOrder)
        assertEquals(OrderStatus.COMPLETED, createdOrder?.status)
        assertEquals(FinancialStatus.PAID, createdOrder?.financialStatus)
        assertEquals(BigDecimal("350.0000"), createdOrder?.totalAmount)

        val createdPayment = paymentRepository.findById(paymentId).orElse(null)
        assertNotNull(createdPayment)
        assertEquals(PaymentStatus.SUCCESS, createdPayment?.status)
        assertEquals(BigDecimal("350.0000"), createdPayment?.amount)

        val createdShift = shiftRepository.findById(shiftId).orElse(null)
        assertNotNull(createdShift)
        assertEquals(ShiftStatus.OPEN, createdShift?.status)
        assertEquals(BigDecimal("2000.0000"), createdShift?.openingCash)

        // 4. Shift Closed Event
        val shiftClosedEvent = SyncEventDto(
            eventId = event4Id,
            aggregateType = "SHIFT",
            aggregateId = shiftId,
            eventType = "SHIFT_CLOSED",
            deviceId = deviceId,
            branchId = branchId,
            payload = """{"shiftId":"$shiftId","actualCashSatang":205000,"varianceSatang":5000,"varianceType":"OVER"}""",
            createdAt = Instant.now()
        )

        // 5. Test Duplicate Replay: Resend event2 (orderCreated) + new event4 (shiftClosed)
        val result2 = syncService.processPush(SyncPushRequest(listOf(orderCreatedEvent, shiftClosedEvent)))
        assertEquals(1, result2.processedEventIds.size)
        assertEquals(1, result2.duplicateEventIds.size)
        assertTrue(result2.duplicateEventIds.contains(event2Id))
        assertTrue(result2.processedEventIds.contains(event4Id))

        // Verify Shift is now CLOSED with variance
        val closedShift = shiftRepository.findById(shiftId).orElse(null)
        assertEquals(ShiftStatus.CLOSED, closedShift?.status)
        assertEquals(BigDecimal("2050.0000"), closedShift?.actualCash)
        assertEquals(BigDecimal("50.0000"), closedShift?.variance)

        // 6. Test Device Sync State Monitoring
        val deviceStates = syncService.listDeviceStates()
        val posDevice = deviceStates.firstOrNull { it.deviceId == deviceId }
        assertNotNull(posDevice)
        assertEquals("SYNCED", posDevice?.syncStatus)
        assertEquals(0, posDevice?.pendingOutboxCount)
    }

    @Test
    fun `test comprehensive CRM sync events - customer upsert, link, point earn redeem reverse, and coupon redeem with idempotency`() {
        val branchId = "branch-001"
        val deviceId = "pos-device-crm-01"

        // 1. Create Coupon in DB for testing sync redemption
        val couponCode = "SYNC_PROMO_50"
        try {
            couponService.createCoupon(
                "comp-001",
                CreateCouponRequestDto(
                    code = couponCode,
                    type = com.sunpos.backend.domain.promotion.CouponType.FIXED,
                    value = BigDecimal("50.00"),
                    minSpend = BigDecimal("100.00"),
                    name = "Sync Test Coupon"
                )
            )
        } catch (_: Exception) {}

        // Setup Customer and Order Sync Events
        val custPhone = "0877771122"
        val orderSyncId = "ord-crm-sync-888"

        val eventCustUpsertId = UUID.randomUUID().toString()
        val eventCustLinkId = UUID.randomUUID().toString()
        val eventPointEarnId = UUID.randomUUID().toString()
        val eventPointRedeemId = UUID.randomUUID().toString()
        val eventCouponRedeemId = UUID.randomUUID().toString()
        val eventPointReverseId = UUID.randomUUID().toString()

        // 1. CUSTOMER_UPSERTED Event
        val custUpsertEvent = SyncEventDto(
            eventId = eventCustUpsertId,
            aggregateType = "CUSTOMER",
            aggregateId = custPhone,
            eventType = "CUSTOMER_UPSERTED",
            deviceId = deviceId,
            branchId = branchId,
            payload = """{"displayName":"ลูกค้า CRM Sync","phone":"$custPhone","customerGroup":"VIP"}""",
            createdAt = Instant.now()
        )

        // 2. ORDER_CREATED Event (฿1,000.00)
        val orderEvent = SyncEventDto(
            eventId = UUID.randomUUID().toString(),
            aggregateType = "ORDER",
            aggregateId = orderSyncId,
            eventType = "ORDER_CREATED",
            deviceId = deviceId,
            branchId = branchId,
            payload = """{"orderId":"$orderSyncId","orderNumber":"ORD-CRM-888","orderType":"DINE_IN","totalSatang":100000,"grossSatang":100000}""",
            createdAt = Instant.now()
        )

        // 3. ORDER_CUSTOMER_LINKED Event
        val custFound = crmService.searchCustomerByIdentity(custPhone)
        val resolvedCustId = custFound.map { it.customer.id }.orElse("cust-crm-fallback")
        val orderLinkEvent = SyncEventDto(
            eventId = eventCustLinkId,
            aggregateType = "ORDER",
            aggregateId = orderSyncId,
            eventType = "ORDER_CUSTOMER_LINKED",
            deviceId = deviceId,
            branchId = branchId,
            payload = """{"orderId":"$orderSyncId","customerId":"$resolvedCustId"}""",
            createdAt = Instant.now()
        )

        // 4. POINT_EARNED Event (+40 Points for ฿1,000 Order)
        val pointEarnEvent = SyncEventDto(
            eventId = eventPointEarnId,
            aggregateType = "LOYALTY_POINT",
            aggregateId = resolvedCustId,
            eventType = "POINT_EARNED",
            deviceId = deviceId,
            branchId = branchId,
            payload = """{"customerId":"$resolvedCustId","orderId":"$orderSyncId","orderAmountSatang":100000,"earnedPoints":40}""",
            createdAt = Instant.now()
        )

        // 5. COUPON_REDEEMED Event
        val couponRedeemEvent = SyncEventDto(
            eventId = eventCouponRedeemId,
            aggregateType = "COUPON",
            aggregateId = couponCode,
            eventType = "COUPON_REDEEMED",
            deviceId = deviceId,
            branchId = branchId,
            payload = """{"code":"$couponCode","orderId":"$orderSyncId","orderAmountSatang":100000,"discountAmountSatang":5000,"customerId":"$resolvedCustId"}""",
            createdAt = Instant.now()
        )

        // Push Initial CRM Sync Batch
        val crmBatch = listOf(custUpsertEvent, orderEvent, orderLinkEvent, pointEarnEvent, couponRedeemEvent)
        val pushResult1 = syncService.processPush(SyncPushRequest(crmBatch))
        assertEquals(5, pushResult1.processedEventIds.size)
        assertEquals(0, pushResult1.duplicateEventIds.size)

        // Verify Order is linked to customer
        val orderInDb = orderRepository.findById(orderSyncId).orElse(null)
        assertNotNull(orderInDb)
        assertEquals(resolvedCustId, orderInDb?.customerId)

        // 6. Test POINT_REDEEMED Event (Redeem 20 Points on next order)
        val nextOrderId = "ord-crm-sync-889"
        val pointRedeemEvent = SyncEventDto(
            eventId = eventPointRedeemId,
            aggregateType = "LOYALTY_POINT",
            aggregateId = resolvedCustId,
            eventType = "POINT_REDEEMED",
            deviceId = deviceId,
            branchId = branchId,
            payload = """{"customerId":"$resolvedCustId","orderId":"$nextOrderId","pointsRedeemed":20,"notes":"Redeem 20 pts"}""",
            createdAt = Instant.now()
        )

        // 7. Test POINT_REVERSED Event (Void orderSyncId)
        val pointReverseEvent = SyncEventDto(
            eventId = eventPointReverseId,
            aggregateType = "LOYALTY_POINT",
            aggregateId = resolvedCustId,
            eventType = "POINT_REVERSED",
            deviceId = deviceId,
            branchId = branchId,
            payload = """{"customerId":"$resolvedCustId","orderId":"$orderSyncId"}""",
            createdAt = Instant.now()
        )

        val pushResult2 = syncService.processPush(SyncPushRequest(listOf(pointRedeemEvent, pointReverseEvent)))
        assertEquals(2, pushResult2.processedEventIds.size)
        assertEquals(0, pushResult2.duplicateEventIds.size)

        // 8. Strict Idempotency Check: Replay ALL 7 events -> ALL must be returned as duplicates with 0 processed
        val allEvents = crmBatch + listOf(pointRedeemEvent, pointReverseEvent)
        val replayResult = syncService.processPush(SyncPushRequest(allEvents))
        assertEquals(0, replayResult.processedEventIds.size, "No events should be processed on duplicate replay")
        assertEquals(7, replayResult.duplicateEventIds.size, "All 7 replayed events must be reported as duplicates")
    }
}
