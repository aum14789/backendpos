package com.sunpos.backend.domain.promotion

import com.sunpos.backend.domain.order.Order
import com.sunpos.backend.domain.order.OrderRepository
import com.sunpos.backend.domain.order.OrderStatus
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CouponServiceTest {

    @Autowired
    private lateinit var couponService: CouponService

    @Autowired
    private lateinit var couponRepository: CouponRepository

    @Autowired
    private lateinit var couponRedemptionRepository: CouponRedemptionRepository

    @Autowired
    private lateinit var orderRepository: OrderRepository

    private val companyId = "comp-001"
    private val branchId = "branch-001"

    @BeforeEach
    fun setUp() {
        couponRedemptionRepository.deleteAll()
        couponRepository.deleteAll()
    }

    @Test
    fun `test FIXED coupon validation and calculation`() {
        val coupon = couponService.createCoupon(
            companyId = companyId,
            dto = CreateCouponRequestDto(
                code = "SAVE50",
                name = "ลดทันที 50 บาท",
                type = CouponType.FIXED,
                value = BigDecimal("50.0000"),
                minSpend = BigDecimal("200.0000"),
                status = CouponStatus.ACTIVE
            )
        )

        val validation = couponService.validateCoupon(
            ValidateCouponRequestDto(
                code = "save50", // test case-insensitivity
                orderAmount = BigDecimal("350.0000"),
                branchId = branchId
            )
        )

        assertTrue(validation.isValid)
        assertEquals("SAVE50", validation.couponCode)
        assertEquals(0, BigDecimal("50.0000").compareTo(validation.calculatedDiscountAmount))
        assertTrue(validation.message.contains("สำเร็จ"))
    }

    @Test
    fun `test PERCENT coupon validation with max discount cap`() {
        couponService.createCoupon(
            companyId = companyId,
            dto = CreateCouponRequestDto(
                code = "DISC10",
                name = "ลด 10% สูงสุด 100 บาท",
                type = CouponType.PERCENT,
                value = BigDecimal("10.0000"),
                minSpend = BigDecimal("300.0000"),
                maxDiscount = BigDecimal("100.0000"),
                status = CouponStatus.ACTIVE
            )
        )

        // Case 1: 10% of 500 = 50 (< maxDiscount 100) -> discount = 50
        val val1 = couponService.validateCoupon(
            ValidateCouponRequestDto(
                code = "DISC10",
                orderAmount = BigDecimal("500.0000"),
                branchId = branchId
            )
        )
        assertTrue(val1.isValid)
        assertEquals(0, BigDecimal("50.0000").compareTo(val1.calculatedDiscountAmount))

        // Case 2: 10% of 1500 = 150 (> maxDiscount 100) -> discount capped at 100
        val val2 = couponService.validateCoupon(
            ValidateCouponRequestDto(
                code = "DISC10",
                orderAmount = BigDecimal("1500.0000"),
                branchId = branchId
            )
        )
        assertTrue(val2.isValid)
        assertEquals(0, BigDecimal("100.0000").compareTo(val2.calculatedDiscountAmount))
    }

    @Test
    fun `test Expired coupon code fails validation`() {
        couponService.createCoupon(
            companyId = companyId,
            dto = CreateCouponRequestDto(
                code = "EXPIRED20",
                type = CouponType.FIXED,
                value = BigDecimal("20.0000"),
                validFrom = Instant.now().minus(30, ChronoUnit.DAYS),
                validTo = Instant.now().minus(1, ChronoUnit.DAYS),
                status = CouponStatus.ACTIVE
            )
        )

        val validation = couponService.validateCoupon(
            ValidateCouponRequestDto(
                code = "EXPIRED20",
                orderAmount = BigDecimal("300.0000"),
                branchId = branchId
            )
        )

        assertFalse(validation.isValid)
        assertEquals("คูปองหมดอายุแล้ว", validation.message)
    }

    @Test
    fun `test Future coupon code fails validation`() {
        couponService.createCoupon(
            companyId = companyId,
            dto = CreateCouponRequestDto(
                code = "FUTURE50",
                type = CouponType.FIXED,
                value = BigDecimal("50.0000"),
                validFrom = Instant.now().plus(7, ChronoUnit.DAYS),
                validTo = Instant.now().plus(30, ChronoUnit.DAYS),
                status = CouponStatus.ACTIVE
            )
        )

        val validation = couponService.validateCoupon(
            ValidateCouponRequestDto(
                code = "FUTURE50",
                orderAmount = BigDecimal("300.0000")
            )
        )

        assertFalse(validation.isValid)
        assertEquals("คูปองนี้ยังไม่เริ่มเปิดใช้งาน", validation.message)
    }

    @Test
    fun `test Order amount below min spend fails validation`() {
        couponService.createCoupon(
            companyId = companyId,
            dto = CreateCouponRequestDto(
                code = "MIN500",
                type = CouponType.FIXED,
                value = BigDecimal("50.0000"),
                minSpend = BigDecimal("500.0000"),
                status = CouponStatus.ACTIVE
            )
        )

        val validation = couponService.validateCoupon(
            ValidateCouponRequestDto(
                code = "MIN500",
                orderAmount = BigDecimal("450.0000")
            )
        )

        assertFalse(validation.isValid)
        assertTrue(validation.message.contains("ยอดสั่งซื้อไม่ถึงขั้นต่ำ ฿500.00"))
    }

    @Test
    fun `test Total usage limit reached fails validation`() {
        val couponDto = couponService.createCoupon(
            companyId = companyId,
            dto = CreateCouponRequestDto(
                code = "LIMITED2",
                type = CouponType.FIXED,
                value = BigDecimal("30.0000"),
                usageLimitTotal = 2,
                usageLimitPerCustomer = 5,
                status = CouponStatus.ACTIVE
            )
        )

        // 1st redemption
        couponService.redeemCoupon(
            RedeemCouponRequestDto(
                code = "LIMITED2",
                orderId = "ord-001",
                orderAmount = BigDecimal("300.0000"),
                customerId = "cust-001"
            )
        )

        // 2nd redemption
        couponService.redeemCoupon(
            RedeemCouponRequestDto(
                code = "LIMITED2",
                orderId = "ord-002",
                orderAmount = BigDecimal("300.0000"),
                customerId = "cust-002"
            )
        )

        // 3rd validation should fail
        val validation = couponService.validateCoupon(
            ValidateCouponRequestDto(
                code = "LIMITED2",
                orderAmount = BigDecimal("300.0000"),
                customerId = "cust-003"
            )
        )

        assertFalse(validation.isValid)
        assertEquals("คูปองนี้ถูกใช้งานเต็มจำนวนสิทธิ์แล้ว", validation.message)
    }

    @Test
    fun `test Per-customer usage limit reached fails validation`() {
        couponService.createCoupon(
            companyId = companyId,
            dto = CreateCouponRequestDto(
                code = "ONEPERCUST",
                type = CouponType.FIXED,
                value = BigDecimal("40.0000"),
                usageLimitTotal = 100,
                usageLimitPerCustomer = 1,
                status = CouponStatus.ACTIVE
            )
        )

        // Customer 1 redeems
        couponService.redeemCoupon(
            RedeemCouponRequestDto(
                code = "ONEPERCUST",
                orderId = "ord-c1-01",
                orderAmount = BigDecimal("200.0000"),
                customerId = "cust-alpha"
            )
        )

        // Customer 1 tries again -> should fail
        val valCust1 = couponService.validateCoupon(
            ValidateCouponRequestDto(
                code = "ONEPERCUST",
                orderAmount = BigDecimal("200.0000"),
                customerId = "cust-alpha"
            )
        )
        assertFalse(valCust1.isValid)
        assertEquals("คุณใช้สิทธิ์คูปองนี้ครบตามจำนวนที่กำหนดแล้ว (1 ครั้ง)", valCust1.message)

        // Customer 2 tries -> should succeed
        val valCust2 = couponService.validateCoupon(
            ValidateCouponRequestDto(
                code = "ONEPERCUST",
                orderAmount = BigDecimal("200.0000"),
                customerId = "cust-beta"
            )
        )
        assertTrue(valCust2.isValid)
    }

    @Test
    fun `test Branch isolation fails validation`() {
        couponService.createCoupon(
            companyId = companyId,
            dto = CreateCouponRequestDto(
                code = "BRANCHONLY",
                type = CouponType.FIXED,
                value = BigDecimal("50.0000"),
                branchId = "branch-specific-01",
                status = CouponStatus.ACTIVE
            )
        )

        val valWrongBranch = couponService.validateCoupon(
            ValidateCouponRequestDto(
                code = "BRANCHONLY",
                orderAmount = BigDecimal("200.0000"),
                branchId = "branch-other-02"
            )
        )
        assertFalse(valWrongBranch.isValid)
        assertEquals("คูปองนี้ไม่สามารถใช้กับสาขานี้ได้", valWrongBranch.message)

        val valCorrectBranch = couponService.validateCoupon(
            ValidateCouponRequestDto(
                code = "BRANCHONLY",
                orderAmount = BigDecimal("200.0000"),
                branchId = "branch-specific-01"
            )
        )
        assertTrue(valCorrectBranch.isValid)
    }

    @Test
    fun `test Redeem coupon successfully and idempotently`() {
        couponService.createCoupon(
            companyId = companyId,
            dto = CreateCouponRequestDto(
                code = "IDEMPOTENT50",
                type = CouponType.FIXED,
                value = BigDecimal("50.0000"),
                status = CouponStatus.ACTIVE
            )
        )

        val order = orderRepository.save(
            Order(
                id = "order-idem-01",
                orderNumber = "ORD-IDEM-01",
                businessDayId = "bday-001",
                branchId = branchId,
                totalAmount = BigDecimal("300.0000")
            )
        )

        // 1st redemption
        val res1 = couponService.redeemCoupon(
            RedeemCouponRequestDto(
                code = "IDEMPOTENT50",
                orderId = order.id,
                orderAmount = BigDecimal("300.0000"),
                customerId = "cust-idem"
            )
        )
        assertTrue(res1.success)
        assertEquals("IDEMPOTENT50", res1.couponCode)
        assertEquals(0, BigDecimal("50.0000").compareTo(res1.discountAmount))

        // 2nd redemption for the same order should be idempotent
        val res2 = couponService.redeemCoupon(
            RedeemCouponRequestDto(
                code = "IDEMPOTENT50",
                orderId = order.id,
                orderAmount = BigDecimal("300.0000"),
                customerId = "cust-idem"
            )
        )
        assertTrue(res2.success)
        assertEquals(res1.redemptionId, res2.redemptionId)
        assertTrue(res2.message.contains("Idempotent"))

        // Redemptions list count should be 1
        val redemptions = couponService.listRedemptions()
        assertEquals(1, redemptions.size)
        assertEquals("IDEMPOTENT50", redemptions.first().couponCode)
    }

    @Test
    fun `test Duplicate coupon code creation rejection`() {
        couponService.createCoupon(
            companyId = companyId,
            dto = CreateCouponRequestDto(
                code = "UNIQUE100",
                value = BigDecimal("100.0000")
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            couponService.createCoupon(
                companyId = companyId,
                dto = CreateCouponRequestDto(
                    code = "unique100",
                    value = BigDecimal("50.0000")
                )
            )
        }
    }
}
