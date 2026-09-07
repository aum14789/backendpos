package com.sunpos.backend.domain.promotion

import com.sunpos.backend.common.ApiResponse
import com.sunpos.backend.common.JdbcRepository
import org.springframework.jdbc.core.JdbcTemplate
import com.sunpos.backend.domain.order.OrderRepository
import com.sunpos.backend.domain.order.OrderItemRepository
import com.sunpos.backend.domain.order.OrderCalculationService
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.Optional
import java.util.UUID

@Repository
class PromotionRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<Promotion>(jdbcTemplate, "promotions", Promotion::class.java) {
    fun findByIsActiveTrue(): List<Promotion> = findByField("isActive", true)
}

@Repository
class PromotionEligibleProductRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<PromotionEligibleProduct>(jdbcTemplate, "promotion_eligible_products", PromotionEligibleProduct::class.java) {
    fun findByIdPromotionId(promotionId: String): List<PromotionEligibleProduct> = findByField("promotionId", promotionId)
}

@Repository
class PromotionRewardProductRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<PromotionRewardProduct>(jdbcTemplate, "promotion_reward_products", PromotionRewardProduct::class.java) {
    fun findByIdPromotionId(promotionId: String): List<PromotionRewardProduct> = findByField("promotionId", promotionId)
}

@Repository
class CouponRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<Coupon>(jdbcTemplate, "coupons", Coupon::class.java) {
    fun findByCode(code: String): Optional<Coupon> = findOneByField("code", code)
    fun findByCodeIgnoreCase(code: String): Optional<Coupon> =
        Optional.ofNullable(findAll().firstOrNull { it.code.equals(code, ignoreCase = true) })
    fun findByCompanyId(companyId: String): List<Coupon> = findByField("companyId", companyId)
    fun findByCompanyIdAndStatus(companyId: String, status: CouponStatus): List<Coupon> =
        findByFields(mapOf("companyId" to companyId, "status" to status.name))
}

@Repository
class CouponRedemptionRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<CouponRedemption>(jdbcTemplate, "coupon_redemptions", CouponRedemption::class.java) {
    fun findByCouponId(couponId: String): List<CouponRedemption> = findByField("couponId", couponId)
    fun findByCustomerId(customerId: String): List<CouponRedemption> = findByField("customerId", customerId)
    fun findByOrderId(orderId: String): List<CouponRedemption> = findByField("orderId", orderId)
    fun countByCouponId(couponId: String): Long = findByCouponId(couponId).size.toLong()
    fun countByCouponIdAndCustomerId(couponId: String, customerId: String): Long =
        findByFields(mapOf("couponId" to couponId, "customerId" to customerId)).size.toLong()
    fun existsByCouponIdAndOrderId(couponId: String, orderId: String): Boolean =
        findByFields(mapOf("couponId" to couponId, "orderId" to orderId)).isNotEmpty()
}

@Repository
class CouponRedemptionLedgerRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<CouponRedemptionLedger>(jdbcTemplate, "coupon_redemption_ledgers", CouponRedemptionLedger::class.java) {
    fun findByCustomerId(customerId: String): List<CouponRedemptionLedger> = findByField("customerId", customerId)
    fun findByOrderId(orderId: String): List<CouponRedemptionLedger> = findByField("orderId", orderId)
}

@Repository
class OrderAppliedPromotionRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<OrderAppliedPromotion>(jdbcTemplate, "order_applied_promotions", OrderAppliedPromotion::class.java) {
    fun findByOrderId(orderId: String): List<OrderAppliedPromotion> = findByField("orderId", orderId)
}

@Repository
class OrderPromotionAllocationRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<OrderPromotionAllocation>(jdbcTemplate, "order_promotion_allocations", OrderPromotionAllocation::class.java) {
    fun findByOrderId(orderId: String): List<OrderPromotionAllocation> = findByField("orderId", orderId)
}

@Service
class CouponService(
    private val couponRepository: CouponRepository,
    private val couponRedemptionRepository: CouponRedemptionRepository,
    private val promotionRepository: PromotionRepository,
    private val orderRepository: OrderRepository
) {
    companion object {
        const val SCALE = 4
        val ROUNDING = RoundingMode.HALF_UP
    }

    fun listCoupons(
        companyId: String = "comp-001",
        status: CouponStatus? = null,
        brandId: String? = null,
        branchId: String? = null
    ): List<CouponDto> {
        val coupons = if (status != null) {
            couponRepository.findByCompanyIdAndStatus(companyId, status)
        } else {
            couponRepository.findByCompanyId(companyId)
        }
        return coupons
            .filter { brandId == null || it.brandId == null || it.brandId == brandId }
            .filter { branchId == null || it.branchId == null || it.branchId == branchId }
            .map { toDto(it) }
    }

    fun getCoupon(id: String): CouponDto {
        val coupon = couponRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Coupon not found with id: $id") }
        return toDto(coupon)
    }

    @Transactional
    fun createCoupon(companyId: String = "comp-001", dto: CreateCouponRequestDto): CouponDto {
        val cleanCode = dto.code.trim().uppercase()
        if (cleanCode.isBlank()) {
            throw IllegalArgumentException("รหัสคูปองต้องไม่เป็นค่าว่าง")
        }
        if (couponRepository.findByCodeIgnoreCase(cleanCode).isPresent) {
            throw IllegalArgumentException("รหัสคูปอง '$cleanCode' มีอยู่ในระบบแล้ว")
        }
        if (dto.value <= BigDecimal.ZERO) {
            throw IllegalArgumentException("มูลค่าส่วนลดคูปองต้องมากกว่า 0")
        }
        if (dto.type == CouponType.PERCENT && dto.value > BigDecimal("100")) {
            throw IllegalArgumentException("ส่วนลดเปอร์เซ็นต์ต้องไม่เกิน 100%")
        }

        val coupon = Coupon(
            id = UUID.randomUUID().toString(),
            companyId = companyId,
            brandId = dto.brandId,
            branchId = dto.branchId,
            code = cleanCode,
            name = dto.name ?: cleanCode,
            description = dto.description,
            type = dto.type,
            value = dto.value.setScale(SCALE, ROUNDING),
            minSpend = dto.minSpend.setScale(SCALE, ROUNDING),
            maxDiscount = dto.maxDiscount?.setScale(SCALE, ROUNDING),
            usageLimitTotal = dto.usageLimitTotal,
            usageLimitPerCustomer = dto.usageLimitPerCustomer ?: 1,
            validFrom = dto.validFrom,
            validTo = dto.validTo,
            status = dto.status,
            maxUses = dto.usageLimitTotal ?: 1000,
            currentUses = 0,
            isUsed = false
        )

        val saved = couponRepository.save(coupon)
        return toDto(saved)
    }

    @Transactional
    fun updateCoupon(id: String, dto: UpdateCouponRequestDto): CouponDto {
        val coupon = couponRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Coupon not found with id: $id") }

        dto.name?.let { coupon.name = it }
        dto.description?.let { coupon.description = it }
        dto.type?.let { coupon.type = it }
        dto.value?.let { coupon.value = it.setScale(SCALE, ROUNDING) }
        dto.minSpend?.let { coupon.minSpend = it.setScale(SCALE, ROUNDING) }
        dto.maxDiscount?.let { coupon.maxDiscount = it.setScale(SCALE, ROUNDING) }
        dto.usageLimitTotal?.let {
            coupon.usageLimitTotal = it
            coupon.maxUses = it
        }
        dto.usageLimitPerCustomer?.let { coupon.usageLimitPerCustomer = it }
        dto.validFrom?.let { coupon.validFrom = it }
        dto.validTo?.let { coupon.validTo = it }
        dto.brandId?.let { coupon.brandId = it }
        dto.branchId?.let { coupon.branchId = it }
        dto.status?.let { coupon.status = it }
        coupon.updatedAt = Instant.now()

        val saved = couponRepository.save(coupon)
        return toDto(saved)
    }

    fun validateCoupon(dto: ValidateCouponRequestDto): CouponValidationResponseDto {
        val cleanCode = dto.code.trim().uppercase()
        val couponOpt = couponRepository.findByCodeIgnoreCase(cleanCode)
        if (couponOpt.isEmpty) {
            return CouponValidationResponseDto(
                isValid = false,
                couponCode = dto.code,
                couponName = "",
                calculatedDiscountAmount = BigDecimal.ZERO,
                message = "ไม่พบคูปองรหัส '$cleanCode' ในระบบ"
            )
        }

        val coupon = couponOpt.get()

        // 1. Status check
        if (coupon.status != CouponStatus.ACTIVE) {
            return CouponValidationResponseDto(
                isValid = false,
                couponCode = coupon.code,
                couponId = coupon.id,
                couponName = coupon.name ?: coupon.code,
                message = "คูปองนี้ถูกระงับการใช้งาน (${coupon.status})"
            )
        }

        val linkedPromo = coupon.promotionId?.let { pid -> promotionRepository.findById(pid).orElse(null) }
        if (linkedPromo != null && !linkedPromo.isActive) {
            return CouponValidationResponseDto(
                isValid = false,
                couponCode = coupon.code,
                couponId = coupon.id,
                couponName = linkedPromo.name,
                message = "โปรโมชั่นของคูปองนี้ไม่เปิดใช้งาน"
            )
        }

        val now = Instant.now()

        // 2. Validity date range check
        if (coupon.validFrom != null && now.isBefore(coupon.validFrom)) {
            return CouponValidationResponseDto(
                isValid = false,
                couponCode = coupon.code,
                couponId = coupon.id,
                couponName = coupon.name ?: coupon.code,
                message = "คูปองนี้ยังไม่เริ่มเปิดใช้งาน"
            )
        }

        if (coupon.validTo != null && now.isAfter(coupon.validTo)) {
            return CouponValidationResponseDto(
                isValid = false,
                couponCode = coupon.code,
                couponId = coupon.id,
                couponName = coupon.name ?: coupon.code,
                message = "คูปองหมดอายุแล้ว"
            )
        }

        if (coupon.expiresAt != null && now.isAfter(coupon.expiresAt)) {
            return CouponValidationResponseDto(
                isValid = false,
                couponCode = coupon.code,
                couponId = coupon.id,
                couponName = coupon.name ?: coupon.code,
                message = "คูปองหมดอายุแล้ว"
            )
        }

        // 3. Branch / Brand isolation check
        if (coupon.branchId != null && dto.branchId != null && coupon.branchId != dto.branchId) {
            return CouponValidationResponseDto(
                isValid = false,
                couponCode = coupon.code,
                couponId = coupon.id,
                couponName = coupon.name ?: coupon.code,
                message = "คูปองนี้ไม่สามารถใช้กับสาขานี้ได้"
            )
        }

        if (coupon.brandId != null && dto.brandId != null && coupon.brandId != dto.brandId) {
            return CouponValidationResponseDto(
                isValid = false,
                couponCode = coupon.code,
                couponId = coupon.id,
                couponName = coupon.name ?: coupon.code,
                message = "คูปองนี้ไม่สามารถใช้กับแบรนด์นี้ได้"
            )
        }

        // 4. Min spend check
        val effectiveMinSpend = when {
            coupon.minSpend > BigDecimal.ZERO -> coupon.minSpend
            linkedPromo != null -> linkedPromo.minAmount
            else -> BigDecimal.ZERO
        }

        if (dto.orderAmount.compareTo(effectiveMinSpend) < 0) {
            val displayName = coupon.name ?: linkedPromo?.name ?: coupon.code
            return CouponValidationResponseDto(
                isValid = false,
                couponCode = coupon.code,
                couponId = coupon.id,
                couponName = displayName,
                minSpend = effectiveMinSpend,
                calculatedDiscountAmount = BigDecimal.ZERO,
                message = "ยอดสั่งซื้อไม่ถึงขั้นต่ำ ฿${effectiveMinSpend.setScale(2, ROUNDING)} (ยอดปัจจุบัน ฿${dto.orderAmount.setScale(2, ROUNDING)})"
            )
        }

        // 5. Total usage limit check
        val redemptionCount = couponRedemptionRepository.countByCouponId(coupon.id)
        val currentTotalUses = maxOf(coupon.currentUses.toLong(), redemptionCount)
        val maxAllowedTotal = coupon.usageLimitTotal ?: coupon.maxUses
        if (coupon.isUsed || (maxAllowedTotal in 1..currentTotalUses)) {
            return CouponValidationResponseDto(
                isValid = false,
                couponCode = coupon.code,
                couponId = coupon.id,
                couponName = coupon.name ?: coupon.code,
                message = "คูปองนี้ถูกใช้งานเต็มจำนวนสิทธิ์แล้ว"
            )
        }

        // 6. Per-customer usage limit check
        if (!dto.customerId.isNullOrBlank() && (coupon.usageLimitPerCustomer ?: 1) > 0) {
            val limitPerCust = coupon.usageLimitPerCustomer ?: 1
            val customerUses = couponRedemptionRepository.countByCouponIdAndCustomerId(coupon.id, dto.customerId)
            if (customerUses >= limitPerCust) {
                return CouponValidationResponseDto(
                    isValid = false,
                    couponCode = coupon.code,
                    couponId = coupon.id,
                    couponName = coupon.name ?: coupon.code,
                    message = "คุณใช้สิทธิ์คูปองนี้ครบตามจำนวนที่กำหนดแล้ว ($limitPerCust ครั้ง)"
                )
            }
        }

        // 7. Calculate Discount Amount
        val effectiveType = if (linkedPromo != null && coupon.value == BigDecimal.ZERO) {
            when (linkedPromo.promoType) {
                PromotionType.PERCENTAGE -> CouponType.PERCENT
                else -> CouponType.FIXED
            }
        } else {
            coupon.type
        }

        val effectiveValue = if (linkedPromo != null && coupon.value == BigDecimal.ZERO) {
            when (linkedPromo.promoType) {
                PromotionType.PERCENTAGE -> linkedPromo.discountRate
                else -> linkedPromo.discountAmount
            }
        } else {
            coupon.value
        }

        val calculatedDiscount = when (effectiveType) {
            CouponType.PERCENT -> {
                val rawPercentDiscount = dto.orderAmount
                    .multiply(effectiveValue)
                    .divide(BigDecimal("100"), SCALE, ROUNDING)
                val cappedDiscount = if (coupon.maxDiscount != null && coupon.maxDiscount!! > BigDecimal.ZERO) {
                    rawPercentDiscount.min(coupon.maxDiscount!!)
                } else {
                    rawPercentDiscount
                }
                cappedDiscount.min(dto.orderAmount)
            }
            CouponType.FIXED -> {
                effectiveValue.min(dto.orderAmount)
            }
        }.setScale(SCALE, ROUNDING)

        val displayName = coupon.name ?: linkedPromo?.name ?: coupon.code

        return CouponValidationResponseDto(
            isValid = true,
            couponCode = coupon.code,
            couponId = coupon.id,
            couponName = displayName,
            type = effectiveType,
            value = effectiveValue,
            calculatedDiscountAmount = calculatedDiscount,
            minSpend = effectiveMinSpend,
            message = "ใช้งานคูปอง '$displayName' สำเร็จ ลดทันที ฿${calculatedDiscount.setScale(2, ROUNDING)}"
        )
    }

    @Transactional
    fun redeemCoupon(dto: RedeemCouponRequestDto): RedeemCouponResponseDto {
        val cleanCode = dto.code.trim().uppercase()
        val couponOpt = couponRepository.findByCodeIgnoreCase(cleanCode)

        // Check duplicate redemption on same order (Idempotency) BEFORE validation
        if (couponOpt.isPresent) {
            val cp = couponOpt.get()
            if (couponRedemptionRepository.existsByCouponIdAndOrderId(cp.id, dto.orderId)) {
                val existing = couponRedemptionRepository.findByOrderId(dto.orderId).first { it.couponId == cp.id }
                return RedeemCouponResponseDto(
                    success = true,
                    redemptionId = existing.id,
                    couponCode = cp.code,
                    couponName = cp.name ?: cp.code,
                    discountAmount = existing.discountAmount,
                    message = "คูปองได้รับการใช้กับออเดอร์นี้แล้ว (Idempotent)"
                )
            }
        }

        val validation = validateCoupon(
            ValidateCouponRequestDto(
                code = dto.code,
                orderAmount = dto.orderAmount,
                customerId = dto.customerId,
                branchId = dto.branchId,
                brandId = dto.brandId
            )
        )

        if (!validation.isValid) {
            throw IllegalArgumentException(validation.message)
        }

        val coupon = couponOpt.get()

        val redemption = CouponRedemption(
            id = UUID.randomUUID().toString(),
            couponId = coupon.id,
            customerId = dto.customerId,
            orderId = dto.orderId,
            discountAmount = validation.calculatedDiscountAmount,
            redeemedAt = Instant.now()
        )

        val savedRedemption = couponRedemptionRepository.save(redemption)

        coupon.currentUses += 1
        val totalLimit = coupon.usageLimitTotal ?: coupon.maxUses
        if (totalLimit > 0 && coupon.currentUses >= totalLimit) {
            coupon.isUsed = true
        }
        couponRepository.save(coupon)

        return RedeemCouponResponseDto(
            success = true,
            redemptionId = savedRedemption.id,
            couponCode = coupon.code,
            couponName = validation.couponName,
            discountAmount = validation.calculatedDiscountAmount,
            message = "แลกรับส่วนลดคูปองสำเร็จ"
        )
    }

    fun listRedemptions(couponId: String? = null, customerId: String? = null): List<CouponRedemptionDto> {
        val redemptions = when {
            couponId != null -> couponRedemptionRepository.findByCouponId(couponId)
            customerId != null -> couponRedemptionRepository.findByCustomerId(customerId)
            else -> couponRedemptionRepository.findAll()
        }

        val couponsMap = couponRepository.findAll().associateBy { it.id }

        return redemptions.sortedByDescending { it.redeemedAt }.map { r ->
            val cp = couponsMap[r.couponId]
            CouponRedemptionDto(
                id = r.id,
                couponId = r.couponId,
                couponCode = cp?.code ?: "UNKNOWN",
                couponName = cp?.name ?: cp?.code ?: "Unknown Coupon",
                customerId = r.customerId,
                orderId = r.orderId,
                discountAmount = r.discountAmount,
                redeemedAt = r.redeemedAt
            )
        }
    }

    private fun toDto(c: Coupon): CouponDto {
        return CouponDto(
            id = c.id,
            companyId = c.companyId,
            brandId = c.brandId,
            branchId = c.branchId,
            code = c.code,
            name = c.name,
            description = c.description,
            type = c.type,
            value = c.value,
            minSpend = c.minSpend,
            maxDiscount = c.maxDiscount,
            usageLimitTotal = c.usageLimitTotal,
            usageLimitPerCustomer = c.usageLimitPerCustomer,
            currentUses = c.currentUses,
            validFrom = c.validFrom,
            validTo = c.validTo,
            status = c.status,
            createdAt = c.createdAt,
            updatedAt = c.updatedAt
        )
    }
}

@RestController
@RequestMapping("/api/v1/coupons")
class CouponController(
    private val couponService: CouponService
) {
    @GetMapping
    fun listCoupons(
        @RequestParam(defaultValue = "comp-001") companyId: String,
        @RequestParam(required = false) status: CouponStatus?,
        @RequestParam(required = false) brandId: String?,
        @RequestParam(required = false) branchId: String?
    ): ApiResponse<List<CouponDto>> {
        val list = couponService.listCoupons(companyId, status, brandId, branchId)
        return ApiResponse.success(list)
    }

    @GetMapping("/{id}")
    fun getCoupon(@PathVariable id: String): ApiResponse<CouponDto> {
        val coupon = couponService.getCoupon(id)
        return ApiResponse.success(coupon)
    }

    @PostMapping
    @PreAuthorize("hasAuthority('crm.coupon.manage') or hasAuthority('COUPON_MANAGE') or hasAuthority('PROMOTION_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_STORE_MANAGER') or hasAuthority('ROLE_BRANCH_MANAGER')")
    fun createCoupon(
        @RequestParam(defaultValue = "comp-001") companyId: String,
        @RequestBody dto: CreateCouponRequestDto
    ): ApiResponse<CouponDto> {
        val created = couponService.createCoupon(companyId, dto)
        return ApiResponse.success(created)
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('crm.coupon.manage') or hasAuthority('COUPON_MANAGE') or hasAuthority('PROMOTION_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_STORE_MANAGER') or hasAuthority('ROLE_BRANCH_MANAGER')")
    fun updateCoupon(
        @PathVariable id: String,
        @RequestBody dto: UpdateCouponRequestDto
    ): ApiResponse<CouponDto> {
        val updated = couponService.updateCoupon(id, dto)
        return ApiResponse.success(updated)
    }

    @PostMapping("/validate")
    fun validateCoupon(@RequestBody dto: ValidateCouponRequestDto): ApiResponse<CouponValidationResponseDto> {
        val result = couponService.validateCoupon(dto)
        return ApiResponse.success(result)
    }

    @PostMapping("/redeem")
    fun redeemCoupon(@RequestBody dto: RedeemCouponRequestDto): ApiResponse<RedeemCouponResponseDto> {
        val result = couponService.redeemCoupon(dto)
        return ApiResponse.success(result)
    }

    @GetMapping("/redemptions")
    fun listAllRedemptions(
        @RequestParam(required = false) customerId: String?
    ): ApiResponse<List<CouponRedemptionDto>> {
        val list = couponService.listRedemptions(customerId = customerId)
        return ApiResponse.success(list)
    }

    @GetMapping("/{id}/redemptions")
    fun listCouponRedemptions(@PathVariable id: String): ApiResponse<List<CouponRedemptionDto>> {
        val list = couponService.listRedemptions(couponId = id)
        return ApiResponse.success(list)
    }
}

@Service
class PromotionService(
    private val promotionRepository: PromotionRepository,
    private val eligibleProductRepository: PromotionEligibleProductRepository,
    private val rewardProductRepository: PromotionRewardProductRepository,
    private val couponRepository: CouponRepository,
    private val redemptionLedgerRepository: CouponRedemptionLedgerRepository,
    private val orderAppliedPromotionRepository: OrderAppliedPromotionRepository,
    private val orderPromotionAllocationRepository: OrderPromotionAllocationRepository,
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val calculationService: OrderCalculationService
) {
    companion object {
        const val SCALE = 4
        val ROUNDING = RoundingMode.HALF_UP
    }

    fun listActivePromotions(): List<Promotion> = promotionRepository.findByIsActiveTrue()

    @Transactional
    fun applyPromotionsToOrder(orderId: String, channel: String?, customerId: String?, couponCode: String? = null): BigDecimal {
        val order = orderRepository.findById(orderId).orElseThrow { IllegalArgumentException("Order not found") }
        val items = orderItemRepository.findByOrderId(orderId)
        if (items.isEmpty()) return BigDecimal.ZERO

        // Remove old applied promotions snapshots & allocations
        val oldApplied = orderAppliedPromotionRepository.findByOrderId(orderId)
        orderAppliedPromotionRepository.deleteAll(oldApplied)

        val oldAllocations = orderPromotionAllocationRepository.findByOrderId(orderId)
        orderPromotionAllocationRepository.deleteAll(oldAllocations)

        val activePromos = promotionRepository.findByIsActiveTrue()
            .filter { it.startAt.isBefore(Instant.now()) && it.endAt.isAfter(Instant.now()) }
            .filter { it.branchId == null || it.branchId == order.branchId }
            .filter { it.channel == null || it.channel.equals(channel, ignoreCase = true) }
            .sortedByDescending { it.priority }

        val grossSubtotal = items.map { it.subtotal }.fold(BigDecimal.ZERO) { acc, a -> acc.add(a) }
        var remainingDiscountableAmount = grossSubtotal
        var totalPromoDiscount = BigDecimal.ZERO

        // If coupon is passed
        val matchedCouponPromo = couponCode?.let { code ->
            val cp = couponRepository.findByCode(code)
                .orElseThrow { IllegalArgumentException("Invalid coupon code") }
            if (cp.isUsed || cp.currentUses >= cp.maxUses || (cp.expiresAt != null && cp.expiresAt!!.isBefore(Instant.now()))) {
                throw IllegalArgumentException("Coupon code expired or fully used")
            }
            cp.promotionId?.let { pid -> promotionRepository.findById(pid).orElse(null) }
        }

        val eligiblePromos = mutableListOf<Promotion>()
        matchedCouponPromo?.let { eligiblePromos.add(it) }
        eligiblePromos.addAll(activePromos)

        var hasNonStackableApplied = false

        for (promo in eligiblePromos) {
            if (hasNonStackableApplied) break
            
            // Check condition bounds
            val eligibleLinks = eligibleProductRepository.findByIdPromotionId(promo.id)
            val eligibleItemIds = eligibleLinks.map { it.menuItemId }.toSet()
            
            val targetItems = if (eligibleItemIds.isEmpty()) items else items.filter { it.menuItemId in eligibleItemIds }
            if (targetItems.isEmpty()) continue

            val targetQty = targetItems.map { it.quantity }.fold(BigDecimal.ZERO) { acc, q -> acc.add(q) }
            val targetAmount = targetItems.map { it.subtotal }.fold(BigDecimal.ZERO) { acc, a -> acc.add(a) }

            if (targetQty < promo.minQuantity || targetAmount < promo.minAmount) continue

            // Calculate Discount & Allocations
            var currentDiscount = BigDecimal.ZERO
            val rewardLinks = rewardProductRepository.findByIdPromotionId(promo.id)

            when (promo.promoType) {
                PromotionType.PERCENTAGE -> {
                    currentDiscount = targetAmount.multiply(promo.discountRate.divide(BigDecimal("100"), SCALE, ROUNDING)).setScale(SCALE, ROUNDING)
                    for (item in targetItems) {
                        val itemDisc = item.subtotal.multiply(promo.discountRate.divide(BigDecimal("100"), SCALE, ROUNDING)).setScale(SCALE, ROUNDING)
                        orderPromotionAllocationRepository.save(
                            OrderPromotionAllocation(
                                orderId = orderId,
                                orderItemId = item.id,
                                promotionId = promo.id,
                                promotionCode = promo.code,
                                promotionName = promo.name,
                                discountAmount = itemDisc
                            )
                        )
                    }
                }
                PromotionType.FIXED_AMOUNT -> {
                    currentDiscount = promo.discountAmount.setScale(SCALE, ROUNDING)
                    orderPromotionAllocationRepository.save(
                        OrderPromotionAllocation(
                            orderId = orderId,
                            orderItemId = targetItems.first().id,
                            promotionId = promo.id,
                            promotionCode = promo.code,
                            promotionName = promo.name,
                            discountAmount = currentDiscount
                        )
                    )
                }
                PromotionType.BUY_1_GET_1 -> {
                    if (targetQty >= BigDecimal("2")) {
                        val freeUnits = (targetQty.toInt() / 2).toBigDecimal()
                        val lowestPrice = targetItems.map { it.unitPriceSnapshot }.minOrNull() ?: BigDecimal.ZERO
                        currentDiscount = lowestPrice.multiply(freeUnits).setScale(SCALE, ROUNDING)

                        val rewardItemId = if (rewardLinks.isNotEmpty()) rewardLinks.first().menuItemId else targetItems.first().menuItemId
                        orderPromotionAllocationRepository.save(
                            OrderPromotionAllocation(
                                orderId = orderId,
                                orderItemId = targetItems.first().id,
                                promotionId = promo.id,
                                promotionCode = promo.code,
                                promotionName = promo.name,
                                discountAmount = currentDiscount,
                                rewardMenuItemId = rewardItemId,
                                freeQuantity = freeUnits
                            )
                        )
                    }
                }
                PromotionType.BUY_1_GET_N -> {
                    if (targetQty >= BigDecimal.ONE && rewardLinks.isNotEmpty()) {
                        for (rLink in rewardLinks) {
                            orderPromotionAllocationRepository.save(
                                OrderPromotionAllocation(
                                    orderId = orderId,
                                    orderItemId = targetItems.first().id,
                                    promotionId = promo.id,
                                    promotionCode = promo.code,
                                    promotionName = promo.name,
                                    discountAmount = BigDecimal.ZERO,
                                    rewardMenuItemId = rLink.menuItemId,
                                    freeQuantity = rLink.quantity.multiply(targetQty).setScale(SCALE, ROUNDING)
                                )
                            )
                        }
                    }
                }
                PromotionType.BUY_N_GET_ITEM -> {
                    if (targetQty >= promo.minQuantity && rewardLinks.isNotEmpty()) {
                        val rewardItemId = rewardLinks.first().menuItemId
                        orderPromotionAllocationRepository.save(
                            OrderPromotionAllocation(
                                orderId = orderId,
                                orderItemId = targetItems.first().id,
                                promotionId = promo.id,
                                promotionCode = promo.code,
                                promotionName = promo.name,
                                discountAmount = BigDecimal.ZERO,
                                rewardMenuItemId = rewardItemId,
                                freeQuantity = BigDecimal.ONE
                            )
                        )
                    }
                }
                else -> {}
            }

            if (currentDiscount > BigDecimal.ZERO) {
                val actualAppliedDiscount = currentDiscount.coerceAtMost(remainingDiscountableAmount)
                orderAppliedPromotionRepository.save(
                    OrderAppliedPromotion(
                        orderId = orderId,
                        promotionId = promo.id,
                        promotionCode = promo.code,
                        promotionName = promo.name,
                        discountAmount = actualAppliedDiscount
                    )
                )
                totalPromoDiscount = totalPromoDiscount.add(actualAppliedDiscount)
                remainingDiscountableAmount = remainingDiscountableAmount.subtract(actualAppliedDiscount)

                if (promo.stackingPolicy == StackingPolicy.NON_STACKABLE) {
                    hasNonStackableApplied = true
                }
            }
        }

        // Record coupon usage ledger if successful
        if (couponCode != null && totalPromoDiscount > BigDecimal.ZERO) {
            val cp = couponRepository.findByCode(couponCode).get()
            cp.currentUses += 1
            if (cp.currentUses >= cp.maxUses) {
                cp.isUsed = true
            }
            couponRepository.save(cp)
            redemptionLedgerRepository.save(
                CouponRedemptionLedger(
                    couponId = cp.id,
                    customerId = customerId ?: "ANONYMOUS",
                    orderId = orderId
                )
            )
        }

        order.discountAmount = totalPromoDiscount
        val pipelineCalc = calculationService.calculateFullOrderPipeline(
            itemSubtotals = items.map { it.subtotal },
            promotionDiscount = totalPromoDiscount,
            isVatInclusive = true
        )
        order.subtotalAmount = pipelineCalc.grossItemTotal
        order.taxAmount = pipelineCalc.taxAmount
        order.totalAmount = pipelineCalc.grandTotal
        orderRepository.save(order)
        return totalPromoDiscount
    }
}
