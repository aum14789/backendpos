package com.sunpos.backend.domain.promotion

import java.io.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class PromotionType {
    BUY_1_GET_1,
    BUY_1_GET_N,
    BUY_N_GET_ITEM,
    BUY_N_GET_N,
    PERCENTAGE,
    FIXED_AMOUNT,
    SET_PRICE
}

enum class StackingPolicy {
    STACKABLE,
    NON_STACKABLE
}

enum class CouponType {
    PERCENT,
    FIXED
}

enum class CouponStatus {
    ACTIVE,
    INACTIVE,
    EXPIRED
}

class Promotion(
    val id: String = UUID.randomUUID().toString(),
    var code: String = "",
    var name: String = "",
    var description: String? = null,
    var promoType: PromotionType = PromotionType.PERCENTAGE,
    var priority: Int = 0,
    var isActive: Boolean = true,
    var startAt: Instant = Instant.now(),
    var endAt: Instant = Instant.now().plusSeconds(86400 * 30),
    var branchId: String? = null,
    var channel: String? = null,
    var minQuantity: BigDecimal = BigDecimal.ZERO,
    var minAmount: BigDecimal = BigDecimal.ZERO,
    var discountRate: BigDecimal = BigDecimal.ZERO,
    var discountAmount: BigDecimal = BigDecimal.ZERO,
    var stackingPolicy: StackingPolicy = StackingPolicy.STACKABLE,
    var usageLimit: Int? = null,
    var perCustomerLimit: Int? = null,
    val createdAt: Instant = Instant.now()
)

class Coupon(
    val id: String = UUID.randomUUID().toString(),
    var promotionId: String? = null,
    var companyId: String = "comp-001",
    var brandId: String? = null,
    var branchId: String? = null,
    var code: String = "",
    var name: String? = null,
    var description: String? = null,
    var type: CouponType = CouponType.FIXED,
    var value: BigDecimal = BigDecimal.ZERO,
    var minSpend: BigDecimal = BigDecimal.ZERO,
    var maxDiscount: BigDecimal? = null,
    var usageLimitTotal: Int? = null,
    var usageLimitPerCustomer: Int? = 1,
    var validFrom: Instant? = null,
    var validTo: Instant? = null,
    var status: CouponStatus = CouponStatus.ACTIVE,
    var isUsed: Boolean = false,
    var maxUses: Int = 1,
    var currentUses: Int = 0,
    var expiresAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now(),
    var version: Int = 0
)

class CouponRedemption(
    val id: String = UUID.randomUUID().toString(),
    var couponId: String = "",
    var customerId: String? = null,
    var orderId: String = "",
    var discountAmount: BigDecimal = BigDecimal.ZERO,
    val redeemedAt: Instant = Instant.now(),
    val createdAt: Instant = Instant.now()
)

class CouponRedemptionLedger(
    val id: String = UUID.randomUUID().toString(),
    var couponId: String = "",
    var customerId: String = "",
    var orderId: String = "",
    val redeemedAt: Instant = Instant.now()
)

class OrderAppliedPromotion(
    val id: String = UUID.randomUUID().toString(),
    var orderId: String = "",
    var promotionId: String = "",
    var promotionCode: String = "",
    var promotionName: String = "",
    var discountAmount: BigDecimal = BigDecimal.ZERO,
    val createdAt: Instant = Instant.now()
)

class OrderPromotionAllocation(
    val id: String = UUID.randomUUID().toString(),
    var orderId: String = "",
    var orderItemId: String? = null,
    var promotionId: String = "",
    var promotionCode: String = "",
    var promotionName: String = "",
    var discountAmount: BigDecimal = BigDecimal.ZERO,
    var rewardMenuItemId: String? = null,
    var freeQuantity: BigDecimal = BigDecimal.ZERO,
    val createdAt: Instant = Instant.now()
)

data class PromotionProductId(
    var promotionId: String = "",
    var menuItemId: String = ""
) : Serializable

class PromotionEligibleProduct(
    val id: String = UUID.randomUUID().toString(),
    var promotionId: String = "",
    var menuItemId: String = ""
) {
    constructor(promotionId: String, menuItemId: String) : this(
        id = "${promotionId}_$menuItemId",
        promotionId = promotionId,
        menuItemId = menuItemId
    )
}

class PromotionRewardProduct(
    val id: String = UUID.randomUUID().toString(),
    var promotionId: String = "",
    var menuItemId: String = "",
    var quantity: BigDecimal = BigDecimal.ONE
) {
    constructor(promotionId: String, menuItemId: String, quantity: BigDecimal) : this(
        id = "${promotionId}_$menuItemId",
        promotionId = promotionId,
        menuItemId = menuItemId,
        quantity = quantity
    )
}

// ── DTOs ──

data class CouponDto(
    val id: String = "",
    val companyId: String = "",
    val brandId: String? = null,
    val branchId: String? = null,
    val code: String = "",
    val name: String? = null,
    val description: String? = null,
    val type: CouponType = CouponType.FIXED,
    val value: BigDecimal = BigDecimal.ZERO,
    val minSpend: BigDecimal = BigDecimal.ZERO,
    val maxDiscount: BigDecimal? = null,
    val usageLimitTotal: Int? = null,
    val usageLimitPerCustomer: Int? = null,
    val currentUses: Int = 0,
    val validFrom: Instant? = null,
    val validTo: Instant? = null,
    val status: CouponStatus = CouponStatus.ACTIVE,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

data class CreateCouponRequestDto(
    val code: String = "",
    val name: String? = null,
    val description: String? = null,
    val type: CouponType = CouponType.FIXED,
    val value: BigDecimal = BigDecimal.ZERO,
    val minSpend: BigDecimal = BigDecimal.ZERO,
    val maxDiscount: BigDecimal? = null,
    val usageLimitTotal: Int? = null,
    val usageLimitPerCustomer: Int? = 1,
    val validFrom: Instant? = null,
    val validTo: Instant? = null,
    val brandId: String? = null,
    val branchId: String? = null,
    val status: CouponStatus = CouponStatus.ACTIVE
)

data class UpdateCouponRequestDto(
    val name: String? = null,
    val description: String? = null,
    val type: CouponType? = null,
    val value: BigDecimal? = null,
    val minSpend: BigDecimal? = null,
    val maxDiscount: BigDecimal? = null,
    val usageLimitTotal: Int? = null,
    val usageLimitPerCustomer: Int? = null,
    val validFrom: Instant? = null,
    val validTo: Instant? = null,
    val brandId: String? = null,
    val branchId: String? = null,
    val status: CouponStatus? = null
)

data class ValidateCouponRequestDto(
    val code: String = "",
    val orderAmount: BigDecimal = BigDecimal.ZERO,
    val customerId: String? = null,
    val branchId: String? = null,
    val brandId: String? = null
)

data class CouponValidationResponseDto(
    val isValid: Boolean = false,
    val couponCode: String = "",
    val couponId: String? = null,
    val couponName: String = "",
    val type: CouponType? = null,
    val value: BigDecimal? = null,
    val calculatedDiscountAmount: BigDecimal = BigDecimal.ZERO,
    val minSpend: BigDecimal? = null,
    val message: String = ""
)

data class RedeemCouponRequestDto(
    val code: String = "",
    val orderId: String = "",
    val orderAmount: BigDecimal = BigDecimal.ZERO,
    val customerId: String? = null,
    val branchId: String? = null,
    val brandId: String? = null
)

data class RedeemCouponResponseDto(
    val success: Boolean = false,
    val redemptionId: String? = null,
    val couponCode: String = "",
    val couponName: String = "",
    val discountAmount: BigDecimal = BigDecimal.ZERO,
    val message: String = ""
)

data class CouponRedemptionDto(
    val id: String = "",
    val couponId: String = "",
    val couponCode: String = "",
    val couponName: String = "",
    val customerId: String? = null,
    val orderId: String = "",
    val discountAmount: BigDecimal = BigDecimal.ZERO,
    val redeemedAt: Instant = Instant.now()
)
