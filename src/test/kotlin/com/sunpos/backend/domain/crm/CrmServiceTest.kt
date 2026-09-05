package com.sunpos.backend.domain.crm

import com.sunpos.backend.common.PhoneUtils
import com.sunpos.backend.domain.promotion.Coupon
import com.sunpos.backend.domain.promotion.CouponRepository
import com.sunpos.backend.domain.promotion.Promotion
import com.sunpos.backend.domain.promotion.PromotionRepository
import com.sunpos.backend.domain.promotion.PromotionType
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CrmServiceTest {

    @Autowired
    private lateinit var crmService: CrmService

    @Autowired
    private lateinit var promotionRepository: PromotionRepository

    @Autowired
    private lateinit var couponRepository: CouponRepository

    @Test
    fun `test phone number normalization utility`() {
        assertEquals("0812345678", PhoneUtils.normalize("081-234-5678"))
        assertEquals("0812345678", PhoneUtils.normalize("081 234 5678"))
        assertEquals("0812345678", PhoneUtils.normalize("+66812345678"))
        assertEquals("0812345678", PhoneUtils.normalize("+66 81-234-5678"))
        assertEquals("0812345678", PhoneUtils.normalize("66812345678"))
        assertEquals("021234567", PhoneUtils.normalize("02-123-4567"))
        assertEquals("0899998888", PhoneUtils.normalize("+66 89 999 8888"))
    }

    @Test
    fun `test search customer with various normalized phone formats`() {
        val companyId = "comp-001"
        // Register customer with standard format: 0812345678
        val created = crmService.createCustomer(
            CreateCustomerDto(
                displayName = "คุณมานี มีแชร์",
                phone = "081-234-5678",
                customerGroup = "VIP"
            ),
            companyId = companyId
        )

        // Search with international prefix +66
        val foundWithPlus66 = crmService.searchCustomers(phone = "+66 81 234 5678", query = null, companyId = companyId)
        assertEquals(1, foundWithPlus66.size)
        assertEquals(created.customer.id, foundWithPlus66.first().customer.id)

        // Search with 66 without plus
        val foundWith66 = crmService.searchCustomers(phone = "66812345678", query = null, companyId = companyId)
        assertEquals(1, foundWith66.size)
        assertEquals(created.customer.id, foundWith66.first().customer.id)

        // Search with query parameter ?q=+66812345678
        val foundWithQ = crmService.searchCustomers(phone = null, query = "+66812345678", companyId = companyId)
        assertEquals(1, foundWithQ.size)
        assertEquals(created.customer.id, foundWithQ.first().customer.id)
    }

    @Test
    fun `test no duplicate customer creation for same phone in same company`() {
        val companyId = "comp-001"

        // 1. Create first customer
        val cust1 = crmService.createCustomer(
            CreateCustomerDto(
                displayName = "สมชาย ลูกค้าเดิม",
                phone = "081-999-1111"
            ),
            companyId = companyId
        )

        // 2. Attempt to create customer with same phone number in same company (even in different format)
        val cust2 = crmService.createCustomer(
            CreateCustomerDto(
                displayName = "สมชาย (สร้างซ้ำ)",
                phone = "+66 81 999 1111"
            ),
            companyId = companyId
        )

        // Rule: Must not duplicate, must return the existing customer
        assertEquals(cust1.customer.id, cust2.customer.id)
        assertEquals(cust1.customer.displayName, cust2.customer.displayName)
    }

    @Test
    fun `test company isolation for customer search and retrieval`() {
        val compA = "comp-001"
        val compB = "comp-999"

        // Create customer in Company A
        val custA = crmService.createCustomer(
            CreateCustomerDto(
                displayName = "ลูกค้าบริษัท A",
                phone = "088-777-6666"
            ),
            companyId = compA
        )

        // Searching in Company A should find the customer
        val searchA = crmService.searchCustomers(phone = "0887776666", query = null, companyId = compA)
        assertEquals(1, searchA.size)
        assertEquals(custA.customer.id, searchA.first().customer.id)

        // Searching in Company B should return empty list (isolated)
        val searchB = crmService.searchCustomers(phone = "0887776666", query = null, companyId = compB)
        assertEquals(0, searchB.size)

        // Direct get in Company B should throw NoSuchElementException
        assertThrows(NoSuchElementException::class.java) {
            crmService.getCustomerDetails(custA.customer.id, companyId = compB)
        }
    }

    @Test
    fun `test patch customer name and soft status`() {
        val companyId = "comp-001"
        val created = crmService.createCustomer(
            CreateCustomerDto(
                displayName = "สมปอง น้องสมชาย",
                phone = "085-555-4444",
                status = "ACTIVE"
            ),
            companyId = companyId
        )
        assertEquals("ACTIVE", created.customer.status)
        assertTrue(created.customer.isActive)

        // Patch displayName and status to INACTIVE
        val updated = crmService.updateCustomer(
            created.customer.id,
            UpdateCustomerDto(displayName = "สมปอง มั่งมี", status = "INACTIVE"),
            companyId = companyId
        )
        assertEquals("สมปอง มั่งมี", updated.customer.displayName)
        assertEquals("INACTIVE", updated.customer.status)
        assertFalse(updated.customer.isActive)
    }

    @Test
    fun `test add customer identity`() {
        val companyId = "comp-001"
        val created = crmService.createCustomer(
            CreateCustomerDto(
                displayName = "วีระ ชัยชนะ",
                phone = "086-111-2233"
            ),
            companyId = companyId
        )

        // Add LINE Identity
        val updatedWithLine = crmService.addCustomerIdentity(
            created.customer.id,
            AddIdentityDto(identityType = IdentityType.LINE, identityValue = "weera_line"),
            companyId = companyId
        )
        assertTrue(updatedWithLine.identities.any { it.identityType == IdentityType.LINE && it.identityValue == "weera_line" })

        // Add Email Identity
        val updatedWithEmail = crmService.addCustomerIdentity(
            created.customer.id,
            AddIdentityDto(identityType = IdentityType.EMAIL, identityValue = "weera@test.com"),
            companyId = companyId
        )
        assertTrue(updatedWithEmail.identities.any { it.identityType == IdentityType.EMAIL && it.identityValue == "weera@test.com" })
    }

    @Test
    fun `test customer creation with multiple identities, membership tier upgrade, point ledger earn redeem adjust reverse`() {
        // 1. Create Customer with Phone, LINE & Member ID
        val cust = crmService.createCustomer(
            CreateCustomerDto(
                firstName = "สมชาย",
                lastName = "ใจดี",
                phone = "0899991234",
                lineId = "somchai_line",
                memberId = "MEM-8888",
                email = "somchai@test.com"
            )
        )
        assertNotNull(cust.customer.id)
        assertEquals(4, cust.identities.size)
        assertEquals("SILVER", cust.currentTier?.code)
        assertEquals(BigDecimal("0.0000"), cust.currentPointsBalance)

        // 2. Search Customer by Phone, Member ID, and LINE ID
        val foundByPhone = crmService.searchCustomerByIdentity("0899991234")
        assertTrue(foundByPhone.isPresent)
        assertEquals(cust.customer.id, foundByPhone.get().customer.id)

        val foundByMemberId = crmService.searchCustomerByIdentity("MEM-8888")
        assertTrue(foundByMemberId.isPresent)
        assertEquals(cust.customer.id, foundByMemberId.get().customer.id)

        val foundByLine = crmService.searchCustomerByIdentity("somchai_line")
        assertTrue(foundByLine.isPresent)
        assertEquals(cust.customer.id, foundByLine.get().customer.id)

        // 3. Earn Points on Order 1 & Evaluate Tier (Spent 6,000 THB -> Should Upgrade Tier to GOLD (Threshold 5,000 THB))
        // Every 25 THB = 1 Point (Silver multiplier = 1.00) -> Points = 6000 / 25 * 1.00 = 240 Points
        val earnLedger = crmService.earnPointsForOrder(cust.customer.id, "ORD-1001", BigDecimal("6000.00"))
        assertEquals(PointTransactionType.EARN, earnLedger.transactionType)
        assertEquals(BigDecimal("240.0000"), earnLedger.points)
        assertEquals(BigDecimal("240.0000"), earnLedger.balanceAfter)

        // Evaluate and Upgrade Membership Tier based on ฿6,000 spending
        crmService.evaluateAndUpgradeMembership(cust.customer.id, BigDecimal("6000.00"))

        // Check Membership Tier Upgrade: Should now be GOLD!
        val custPostEarn = crmService.getCustomerDetails(cust.customer.id)
        assertEquals("GOLD", custPostEarn.currentTier?.code)

        // 4. Earn Points on Order 2 with GOLD Multiplier (1.5x)
        // Spent 2,000 THB -> Base Points = 2000 / 25 = 80 -> Gold Points = 80 * 1.5 = 120 Points
        val earnLedger2 = crmService.earnPointsForOrder(cust.customer.id, "ORD-1002", BigDecimal("2000.00"))
        assertEquals(BigDecimal("120.0000"), earnLedger2.points)
        assertEquals(BigDecimal("360.0000"), earnLedger2.balanceAfter)

        // 5. Redeem 40 Points
        val redeemLedger = crmService.redeemPoints(
            PointTransactionDto(customerId = cust.customer.id, points = BigDecimal("40.0"), notes = "ส่วนลด 4 บาท")
        )
        assertEquals(PointTransactionType.REDEEM, redeemLedger.transactionType)
        assertEquals(BigDecimal("320.0000"), redeemLedger.balanceAfter)

        // 6. Manual Adjust +20 Points by Admin
        val adjustLedger = crmService.adjustPoints(
            PointTransactionDto(customerId = cust.customer.id, points = BigDecimal("20.0"), notes = "ชดเชยแต้มพิเศษ")
        )
        assertEquals(PointTransactionType.ADJUST, adjustLedger.transactionType)
        assertEquals(BigDecimal("340.0000"), adjustLedger.balanceAfter)

        // 7. Reverse Points for Cancelled Order 2 (-120 Points)
        val orderId2 = "ORD-1002"
        val revLedgers = crmService.reversePoints(orderId2, cust.customer.id)
        assertTrue(revLedgers.isNotEmpty())
        val revEarn = revLedgers.first { it.transactionType == PointTransactionType.REVERSE }
        assertEquals(BigDecimal("-120.0000"), revEarn.points)
        assertEquals(BigDecimal("220.0000"), revEarn.balanceAfter)

        // Final Points Balance Check
        assertEquals(BigDecimal("220.0000"), crmService.calculatePointsBalance(cust.customer.id))
    }

    @Test
    fun `test coupon creation and validation with minimum spend and percentage discount`() {
        // Create Promo 1: Fixed ฿50 off min spend ฿300
        val promo1 = promotionRepository.save(
            Promotion(
                code = "PROMO-TEST50",
                name = "Test 50 Baht Off",
                promoType = PromotionType.FIXED_AMOUNT,
                startAt = Instant.now().minusSeconds(3600),
                endAt = Instant.now().plusSeconds(86400),
                minAmount = BigDecimal("300.0000"),
                discountAmount = BigDecimal("50.0000")
            )
        )
        couponRepository.save(
            Coupon(
                promotionId = promo1.id,
                code = "TEST50",
                maxUses = 10
            )
        )

        // Validation 1: Below min spend (฿200 < ฿300) -> Invalid
        val resBelowMin = crmService.validateCoupon(
            ValidateCouponRequestDto(code = "TEST50", orderAmount = BigDecimal("200.0000"))
        )
        assertFalse(resBelowMin.isValid)
        assertEquals(BigDecimal.ZERO, resBelowMin.calculatedDiscountAmount)

        // Validation 2: Meets min spend (฿400 >= ฿300) -> Valid, discount ฿50
        val resValid = crmService.validateCoupon(
            ValidateCouponRequestDto(code = "TEST50", orderAmount = BigDecimal("400.0000"))
        )
        assertTrue(resValid.isValid)
        assertEquals(BigDecimal("50.0000"), resValid.calculatedDiscountAmount)

        // Create Promo 2: 10% off min spend ฿500
        val promo2 = promotionRepository.save(
            Promotion(
                code = "PROMO-TEST10PCT",
                name = "Test 10% Off",
                promoType = PromotionType.PERCENTAGE,
                startAt = Instant.now().minusSeconds(3600),
                endAt = Instant.now().plusSeconds(86400),
                minAmount = BigDecimal("500.0000"),
                discountRate = BigDecimal("10.00")
            )
        )
        couponRepository.save(
            Coupon(
                promotionId = promo2.id,
                code = "TEST10PCT",
                maxUses = 10
            )
        )

        // Validation 3: Order ฿800 -> 10% = ฿80
        val resPct = crmService.validateCoupon(
            ValidateCouponRequestDto(code = "TEST10PCT", orderAmount = BigDecimal("800.0000"))
        )
        assertTrue(resPct.isValid)
        assertEquals(BigDecimal("80.0000"), resPct.calculatedDiscountAmount)
    }

    @Autowired
    private lateinit var orderRepository: com.sunpos.backend.domain.order.OrderRepository

    @Test
    fun `test list customer orders with branch and date range filtering`() {
        val cust = crmService.createCustomer(
            CreateCustomerDto(displayName = "Customer Filter Test", phone = "081-444-5555")
        )

        val ord1 = orderRepository.save(
            com.sunpos.backend.domain.order.Order(
                branchId = "branch-001",
                customerId = cust.customer.id,
                orderNumber = "ORD-TEST-001",
                businessDayId = "bday-001",
                totalAmount = BigDecimal("350.0000")
            )
        )

        val ord2 = orderRepository.save(
            com.sunpos.backend.domain.order.Order(
                branchId = "branch-002",
                customerId = cust.customer.id,
                orderNumber = "ORD-TEST-002",
                businessDayId = "bday-002",
                totalAmount = BigDecimal("500.0000")
            )
        )

        // 1. All orders for customer
        val allOrders = crmService.listCustomerOrders(cust.customer.id)
        assertEquals(2, allOrders.size)

        // 2. Filter by branch-001
        val branch1Orders = crmService.listCustomerOrders(cust.customer.id, branchId = "branch-001")
        assertEquals(1, branch1Orders.size)
        assertEquals(ord1.id, branch1Orders.first().id)

        // 3. Filter by date range (today)
        val todayStr = java.time.LocalDate.now().toString()
        val todayOrders = crmService.listCustomerOrders(cust.customer.id, from = todayStr, to = todayStr)
        assertEquals(2, todayOrders.size)

        // 4. Filter by future date range (empty)
        val futureStr = java.time.LocalDate.now().plusDays(2).toString()
        val futureOrders = crmService.listCustomerOrders(cust.customer.id, from = futureStr)
        assertEquals(0, futureOrders.size)
    }

    @Test
    fun `test new customer receives default SILVER tier automatically`() {
        val companyId = "comp-001"
        val cust = crmService.createCustomer(
            CreateCustomerDto(displayName = "น้องใหม่ สมาชิก", phone = "089-111-2233"),
            companyId = companyId
        )

        val membership = crmService.getCustomerMembership(cust.customer.id, companyId)
        assertEquals("SILVER", membership.tierCode)
        assertEquals(1, membership.rankLevel)
        assertTrue(membership.minimumSpent.compareTo(BigDecimal.ZERO) == 0)
        assertTrue(membership.discountPercentage.compareTo(BigDecimal.ZERO) == 0)
        assertTrue(membership.pointMultiplier.compareTo(BigDecimal.ONE) == 0)
        assertTrue(membership.currentSpent.compareTo(BigDecimal.ZERO) == 0)
        assertEquals("สมาชิก VIP (Gold)", membership.nextTierName)
        assertTrue(membership.spentNeededForNextTier?.compareTo(BigDecimal("5000")) == 0)
    }

    @Test
    fun `test membership tier CRUD operations and company isolation`() {
        val companyId = "comp-001"
        // 1. Create Custom Tier: DIAMOND
        val newTier = crmService.createTier(
            CreateMembershipTierDto(
                code = "DIAMOND",
                name = "สมาชิก Diamond Exclusive",
                rankLevel = 4,
                minimumSpent = BigDecimal("50000.0000"),
                pointMultiplier = BigDecimal("3.00"),
                discountPercentage = BigDecimal("15.00"),
                isActive = true
            ),
            companyId = companyId
        )
        assertNotNull(newTier.id)
        assertEquals("DIAMOND", newTier.code)
        assertEquals(BigDecimal("15.00"), newTier.discountPercentage)

        // 2. Read Tier
        val fetchedTier = crmService.getTier(newTier.id, companyId)
        assertEquals("DIAMOND", fetchedTier.code)

        // 3. Update Tier (Change discount to 18%)
        val updatedTier = crmService.updateTier(
            newTier.id,
            UpdateMembershipTierDto(name = "สมาชิก Diamond Crown", discountPercentage = BigDecimal("18.00")),
            companyId = companyId
        )
        assertEquals("สมาชิก Diamond Crown", updatedTier.name)
        assertEquals(BigDecimal("18.00"), updatedTier.discountPercentage)

        // 4. List Tiers
        val tiers = crmService.listTiers(companyId)
        assertTrue(tiers.any { it.code == "DIAMOND" })

        // 5. Delete Tier (Soft delete isActive = false)
        val deleted = crmService.deleteTier(newTier.id, companyId)
        assertTrue(deleted)
        val afterDelete = crmService.getTier(newTier.id, companyId)
        assertFalse(afterDelete.isActive)
    }

    @Test
    fun `test evaluate and upgrade tier based on cumulative spending`() {
        val companyId = "comp-001"
        val cust = crmService.createCustomer(
            CreateCustomerDto(displayName = "คุณสุชาติ ขาประจำ", phone = "087-333-8899"),
            companyId = companyId
        )

        // Step 1: Initial state = SILVER (Spent 0)
        val initialMembership = crmService.getCustomerMembership(cust.customer.id, companyId)
        assertEquals("SILVER", initialMembership.tierCode)

        // Step 2: Spend ฿3,000 -> Still SILVER (needs ฿2,000 more for GOLD)
        val eval1 = crmService.evaluateAndUpgradeMembership(cust.customer.id, addedSpend = BigDecimal("3000.0000"))
        assertFalse(eval1.isUpgraded)
        assertEquals("SILVER", eval1.currentTierCode)
        assertEquals(BigDecimal("3000.0000"), eval1.currentSpent)
        assertEquals(BigDecimal("2000.0000"), eval1.membership.spentNeededForNextTier)

        // Step 3: Spend additional ฿2,500 (Total ฿5,500) -> Upgrade to GOLD (Min ฿5,000)
        val eval2 = crmService.evaluateAndUpgradeMembership(cust.customer.id, addedSpend = BigDecimal("2500.0000"))
        assertTrue(eval2.isUpgraded)
        assertEquals("SILVER", eval2.previousTierCode)
        assertEquals("GOLD", eval2.currentTierCode)
        assertEquals(BigDecimal("5500.0000"), eval2.currentSpent)
        assertEquals(BigDecimal("5.00"), eval2.membership.discountPercentage)
        assertEquals(BigDecimal("1.50"), eval2.membership.pointMultiplier)
        assertEquals("สมาชิก VVIP (Platinum)", eval2.membership.nextTierName)
        assertEquals(BigDecimal("14500.0000"), eval2.membership.spentNeededForNextTier)

        // Step 4: Spend additional ฿15,000 (Total ฿20,500) -> Upgrade to PLATINUM (Min ฿20,000)
        val eval3 = crmService.evaluateAndUpgradeMembership(cust.customer.id, addedSpend = BigDecimal("15000.0000"))
        assertTrue(eval3.isUpgraded)
        assertEquals("GOLD", eval3.previousTierCode)
        assertEquals("PLATINUM", eval3.currentTierCode)
        assertEquals(BigDecimal("20500.0000"), eval3.currentSpent)
        assertEquals(BigDecimal("10.00"), eval3.membership.discountPercentage)
        assertEquals(BigDecimal("2.00"), eval3.membership.pointMultiplier)
        assertNull(eval3.membership.nextTierName) // Max tier reached
    }

    @Test
    fun `test point calculation every 25 baht with tier multiplier and idempotency`() {
        val companyId = "comp-point-test"
        val cust = crmService.createCustomer(
            CreateCustomerDto(displayName = "Customer Point Calc", phone = "0819991111"),
            companyId = companyId
        )

        // 1. Initial balance = 0
        assertEquals(BigDecimal("0.0000"), crmService.calculatePointsBalance(cust.customer.id))

        // 2. Spend ฿24.50 (< ฿25 threshold) -> 0 points
        val earnSmall = crmService.earnPointsForOrder(cust.customer.id, "ORD-SMALL", BigDecimal("24.50"))
        assertEquals(BigDecimal("0.0000"), earnSmall.points)
        assertEquals(BigDecimal("0.0000"), earnSmall.balanceAfter)

        // 3. Spend ฿275.00 on Silver (1.0x) -> 275 / 25 = 11 base points * 1.0 = 11 points
        val earn1 = crmService.earnPointsForOrder(cust.customer.id, "ORD-275", BigDecimal("275.00"))
        assertEquals(BigDecimal("11.0000"), earn1.points)
        assertEquals(BigDecimal("11.0000"), earn1.balanceAfter)

        // 4. Idempotency test: Re-executing earn for same order ID returns existing ledger without duplicate addition
        val duplicateEarn = crmService.earnPointsForOrder(cust.customer.id, "ORD-275", BigDecimal("275.00"))
        assertEquals(earn1.id, duplicateEarn.id)
        assertEquals(BigDecimal("11.0000"), crmService.calculatePointsBalance(cust.customer.id))
    }

    @Test
    fun `test point over-redemption prevented with exception and exact 100 pts to 10 THB calculation`() {
        val cust = crmService.createCustomer(
            CreateCustomerDto(displayName = "Redeem Tester", phone = "0828882222")
        )

        // Give 250 points via Earn on ฿6,250 order
        crmService.earnPointsForOrder(cust.customer.id, "ORD-EARN-250", BigDecimal("6250.00"))
        assertEquals(BigDecimal("250.0000"), crmService.calculatePointsBalance(cust.customer.id))

        // 1. Attempt to redeem 300 points (Exceeds balance of 250) -> Must fail
        val exception = org.junit.jupiter.api.assertThrows<IllegalArgumentException> {
            crmService.redeemPoints(
                customerId = cust.customer.id,
                pointsToRedeem = BigDecimal("300.0000"),
                orderId = "ORD-FAIL"
            )
        }
        assertTrue(exception.message!!.contains("ยอดแต้มคงเหลือไม่เพียงพอ") || exception.message!!.contains("Insufficient"))

        // 2. Redeem 200 points -> 200 pts * 0.10 = ฿20.00 discount, balance after = 50.0000
        val redeemResult = crmService.redeemPoints(
            customerId = cust.customer.id,
            pointsToRedeem = BigDecimal("200.0000"),
            orderId = "ORD-SUCCESS"
        )
        assertEquals(BigDecimal("200.0000"), redeemResult.redeemedPoints)
        assertEquals(BigDecimal("20.0000"), redeemResult.discountAmount)
        assertEquals(BigDecimal("50.0000"), redeemResult.balanceAfter)
        assertEquals(BigDecimal("50.0000"), crmService.calculatePointsBalance(cust.customer.id))
    }

    @Test
    fun `test manager adjust points requires mandatory reason and prevents negative balance`() {
        val cust = crmService.createCustomer(
            CreateCustomerDto(displayName = "Adjust Tester", phone = "0837773333")
        )

        // Give 50 points
        crmService.earnPointsForOrder(cust.customer.id, "ORD-INIT-50", BigDecimal("1250.00"))

        // 1. Adjust with blank reason -> Must fail
        assertThrows(IllegalArgumentException::class.java) {
            crmService.adjustPoints(
                customerId = cust.customer.id,
                points = BigDecimal("20.0000"),
                reason = "   ",
                operatorId = "mgr-01"
            )
        }

        // 2. Adjust with -100 points (Exceeds balance of 50) -> Must fail
        assertThrows(IllegalArgumentException::class.java) {
            crmService.adjustPoints(
                customerId = cust.customer.id,
                points = BigDecimal("-100.0000"),
                reason = "Deduct error points",
                operatorId = "mgr-01"
            )
        }

        // 3. Valid Adjustment +30 points with reason
        val adjLedger = crmService.adjustPoints(
            customerId = cust.customer.id,
            points = BigDecimal("30.0000"),
            reason = "Special goodwill gesture from manager",
            operatorId = "mgr-01"
        )
        assertEquals(BigDecimal("30.0000"), adjLedger.points)
        assertEquals(BigDecimal("80.0000"), adjLedger.balanceAfter)
        assertEquals(PointTransactionType.ADJUST, adjLedger.transactionType)
    }

    @Test
    fun `test point reversal on voided order restores redeemed points and deducts earned points`() {
        val cust = crmService.createCustomer(
            CreateCustomerDto(displayName = "Reversal Tester", phone = "0846664444")
        )

        // Give 500 initial points
        crmService.earnPointsForOrder(cust.customer.id, "ORD-INIT", BigDecimal("12500.00"))
        assertEquals(BigDecimal("500.0000"), crmService.calculatePointsBalance(cust.customer.id))

        // Order 999: Customer redeems 100 points (-100 pts -> balance 400)
        crmService.redeemPoints(
            customerId = cust.customer.id,
            pointsToRedeem = BigDecimal("100.0000"),
            orderId = "ORD-999"
        )
        assertEquals(BigDecimal("400.0000"), crmService.calculatePointsBalance(cust.customer.id))

        // Order 999 is paid and earns 40 points (+40 pts -> balance 440)
        crmService.earnPointsForOrder(cust.customer.id, "ORD-999", BigDecimal("1000.00"))
        assertEquals(BigDecimal("440.0000"), crmService.calculatePointsBalance(cust.customer.id))

        // Void Order 999 -> reversePoints should:
        // 1. Reverse EARN of 40 points (-40)
        // 2. Refund REDEEM of 100 points (+100)
        // Final balance should be 440 - 40 + 100 = 500.0000!
        val reversed = crmService.reversePoints("ORD-999", cust.customer.id)
        assertEquals(2, reversed.size)
        assertEquals(BigDecimal("500.0000"), crmService.calculatePointsBalance(cust.customer.id))
    }

    @Test
    fun `test point balance details query returns accurate totalEarned and totalRedeemed`() {
        val cust = crmService.createCustomer(
            CreateCustomerDto(displayName = "Balance Dto Tester", phone = "0855555555")
        )

        // Earn 100 pts on Order A
        crmService.earnPointsForOrder(cust.customer.id, "ORD-A", BigDecimal("2500.00"))
        // Earn 50 pts on Order B
        crmService.earnPointsForOrder(cust.customer.id, "ORD-B", BigDecimal("1250.00"))
        // Redeem 30 pts on Order C
        crmService.redeemPoints(cust.customer.id, BigDecimal("30.0000"), "ORD-C")

        val details = crmService.getPointBalanceDetails(cust.customer.id)
        assertEquals(cust.customer.id, details.customerId)
        assertEquals(BigDecimal("120.0000"), details.balance) // 100 + 50 - 30 = 120
        assertEquals(BigDecimal("150.0000"), details.totalEarned) // 100 + 50 = 150
        assertEquals(BigDecimal("30.0000"), details.totalRedeemed) // 30
        assertNotNull(details.latestTransactionAt)
    }
}
