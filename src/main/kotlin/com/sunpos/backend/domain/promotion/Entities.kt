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

/**
 * Active Days & Time Window (ADR 0009)
 * - activeDays: CSV ของรหัสวัน ISO (MON..SUN), NULL/ว่าง = ทุกวัน
 * - activeStartTime/activeEndTime: "HH:MM" เวลาท้องถิ่นสาขา (Asia/Bangkok)
 *   end <= start = หน้าต่างข้ามเที่ยงคืน; ข้อมูลเดิมที่ NULL = ใช้ได้ทั้งวัน
 */
object ActiveSchedule {
    val DAY_CODES = listOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

    /** เวลาท้องถิ่นสาขา (ADR 0009) — v1 hardcode; ถ้ามีสาขาต่างประเทศต้องเพิ่ม timezone ที่ Branch */
    val ZONE: java.time.ZoneId = java.time.ZoneId.of("Asia/Bangkok")

    fun parseDays(csv: String?): Set<String> {
        if (csv.isNullOrBlank()) return DAY_CODES.toSet()
        return csv.split(",").map { it.trim().uppercase() }.filter { it in DAY_CODES }.toSet()
    }

    fun formatDays(days: Collection<String>): String =
        days.map { it.trim().uppercase() }.filter { it in DAY_CODES }.distinct().joinToString(",")

    /** คำอธิบายกำหนดการสำหรับแสดงข้อความผู้ใช้ เช่น "วันจันทร์, วันพุธ 11:00–14:00" */
    fun describe(activeDays: String?, startTime: String?, endTime: String?): String {
        val dayNames = mapOf(
            "MON" to "วันจันทร์", "TUE" to "วันอังคาร", "WED" to "วันพุธ",
            "THU" to "วันพฤหัสบดี", "FRI" to "วันศุกร์", "SAT" to "วันเสาร์", "SUN" to "วันอาทิตย์"
        )
        val days = parseDays(activeDays)
        val dayText = if (days.size == DAY_CODES.size) "ทุกวัน"
        else days.mapNotNull { dayNames[it] }.ifEmpty { listOf("ทุกวัน") }.joinToString(" ")
        val hasTime = !startTime.isNullOrBlank() && !endTime.isNullOrBlank()
        val timeText = if (hasTime) " เวลา $startTime–$endTime" else ""
        return "$dayText$timeText"
    }

    /** วันนี้ (Asia/Bangkok) อยู่ใน activeDays หรือไม่ (ว่าง = ทุกวัน) */
    fun isDayActive(activeDays: String?, localDate: java.time.LocalDate): Boolean {
        val allowed = parseDays(activeDays)
        if (allowed.size == DAY_CODES.size) return true
        val code = when (localDate.dayOfWeek) {
            java.time.DayOfWeek.MONDAY -> "MON"
            java.time.DayOfWeek.TUESDAY -> "TUE"
            java.time.DayOfWeek.WEDNESDAY -> "WED"
            java.time.DayOfWeek.THURSDAY -> "THU"
            java.time.DayOfWeek.FRIDAY -> "FRI"
            java.time.DayOfWeek.SATURDAY -> "SAT"
            java.time.DayOfWeek.SUNDAY -> "SUN"
        }
        return code in allowed
    }

    /** เวลาปัจจุบัน (นาฬิกาปฏิทิน) อยู่ในหน้าต่างเวลาหรือไม่ (ว่าง = ทั้งวัน, ข้ามเที่ยงคืนได้) */
    fun isTimeActive(startTime: String?, endTime: String?, localTime: java.time.LocalTime): Boolean {
        if (startTime.isNullOrBlank() || endTime.isNullOrBlank()) return true
        val start = runCatching { java.time.LocalTime.parse(startTime) }.getOrNull() ?: return true
        val end = runCatching { java.time.LocalTime.parse(endTime) }.getOrNull() ?: return true
        return if (start <= end) localTime >= start && localTime <= end
        else localTime >= start || localTime <= end // ข้ามเที่ยงคืน: 20:00–02:00
    }

    /** ตรวจทั้งวันและเวลาด้วยเวลาท้องถิ่นสาขา */
    fun isScheduleActive(activeDays: String?, startTime: String?, endTime: String?, localDateTime: java.time.LocalDateTime): Boolean {
        return isDayActive(activeDays, localDateTime.toLocalDate()) &&
            isTimeActive(startTime, endTime, localDateTime.toLocalTime())
    }
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
    var brandId: String? = null,
    var branchId: String? = null,
    var channel: String? = null,
    var minQuantity: BigDecimal = BigDecimal.ZERO,
    var minAmount: BigDecimal = BigDecimal.ZERO,
    var discountRate: BigDecimal = BigDecimal.ZERO,
    var discountAmount: BigDecimal = BigDecimal.ZERO,
    var stackingPolicy: StackingPolicy = StackingPolicy.STACKABLE,
    var usageLimit: Int? = null,
    var perCustomerLimit: Int? = null,
    var activeDays: String? = null,
    var activeStartTime: String? = null,
    var activeEndTime: String? = null,
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
    var activeDays: String? = null,
    var activeStartTime: String? = null,
    var activeEndTime: String? = null,
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
    val activeDays: String? = null,
    val activeStartTime: String? = null,
    val activeEndTime: String? = null,
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
    val activeDays: List<String> = emptyList(),
    val activeStartTime: String? = null,
    val activeEndTime: String? = null,
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
    val activeDays: List<String>? = null,
    val activeStartTime: String? = null,
    val activeEndTime: String? = null,
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
    val activeDays: String? = null,
    val activeStartTime: String? = null,
    val activeEndTime: String? = null,
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

// ── Promotion DTOs ──

data class PromotionDto(
    val id: String = "",
    val code: String = "",
    val name: String = "",
    val description: String? = null,
    val promoType: PromotionType = PromotionType.PERCENTAGE,
    val priority: Int = 0,
    val isActive: Boolean = true,
    val startAt: Instant = Instant.now(),
    val endAt: Instant = Instant.now(),
    val brandId: String? = null,
    val branchId: String? = null,
    val channel: String? = null,
    val minQuantity: BigDecimal = BigDecimal.ZERO,
    val minAmount: BigDecimal = BigDecimal.ZERO,
    val discountRate: BigDecimal = BigDecimal.ZERO,
    val discountAmount: BigDecimal = BigDecimal.ZERO,
    val stackingPolicy: StackingPolicy = StackingPolicy.STACKABLE,
    val usageLimit: Int? = null,
    val perCustomerLimit: Int? = null,
    val activeDays: List<String> = emptyList(),
    val activeStartTime: String? = null,
    val activeEndTime: String? = null,
    val createdAt: Instant = Instant.now()
)

data class CreatePromotionRequestDto(
    val code: String = "",
    val name: String = "",
    val description: String? = null,
    val promoType: PromotionType = PromotionType.PERCENTAGE,
    val priority: Int = 0,
    val startAt: Instant = Instant.now(),
    val endAt: Instant = Instant.now().plusSeconds(86400 * 30),
    val brandId: String? = null,
    val branchId: String? = null,
    val channel: String? = null,
    val minQuantity: BigDecimal = BigDecimal.ZERO,
    val minAmount: BigDecimal = BigDecimal.ZERO,
    val discountRate: BigDecimal = BigDecimal.ZERO,
    val discountAmount: BigDecimal = BigDecimal.ZERO,
    val stackingPolicy: StackingPolicy = StackingPolicy.STACKABLE,
    val usageLimit: Int? = null,
    val perCustomerLimit: Int? = null,
    val activeDays: List<String> = emptyList(),
    val activeStartTime: String? = null,
    val activeEndTime: String? = null,
    val eligibleProductIds: List<String> = emptyList(),
    val rewardProductIds: List<String> = emptyList()
)

data class UpdatePromotionRequestDto(
    val name: String? = null,
    val description: String? = null,
    val promoType: PromotionType? = null,
    val priority: Int? = null,
    val startAt: Instant? = null,
    val endAt: Instant? = null,
    val brandId: String? = null,
    val branchId: String? = null,
    val channel: String? = null,
    val minQuantity: BigDecimal? = null,
    val minAmount: BigDecimal? = null,
    val discountRate: BigDecimal? = null,
    val discountAmount: BigDecimal? = null,
    val stackingPolicy: StackingPolicy? = null,
    val usageLimit: Int? = null,
    val perCustomerLimit: Int? = null,
    val activeDays: List<String>? = null,
    val activeStartTime: String? = null,
    val activeEndTime: String? = null,
    val isActive: Boolean? = null
)

data class UpdatePromotionProductsRequestDto(
    val eligibleProductIds: List<String> = emptyList(),
    val rewardProductIds: List<String> = emptyList()
)

data class PromotionProductDto(
    val id: String = "",
    val promotionId: String = "",
    val menuItemId: String = "",
    val quantity: BigDecimal = BigDecimal.ONE
)
