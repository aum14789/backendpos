package com.sunpos.backend.domain.order

import java.io.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class BuffetPromotionStatus {
    ACTIVE,
    INACTIVE,
    ARCHIVED
}

class BuffetPromotion(
    val id: String = UUID.randomUUID().toString(),
    var brandId: String = "",
    var branchId: String? = null,
    var name: String = "",
    var pricePerPerson: BigDecimal = BigDecimal.ZERO,
    var durationMinutes: Int = 90,
    var status: BuffetPromotionStatus = BuffetPromotionStatus.ACTIVE,
    val createdAt: Instant = Instant.now(),
    var createdBy: String? = null,
    var updatedAt: Instant = Instant.now(),
    var updatedBy: String? = null,
    var version: Long = 0
)

data class BuffetPromotionMenuItemId(
    var promotionId: String = "",
    var menuItemId: String = ""
) : Serializable

class BuffetPromotionMenuItem(
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

class BuffetPromotionTier(
    val id: String = UUID.randomUUID().toString(),
    var promotionId: String = "",
    var name: String = "",
    var adultPrice: BigDecimal = BigDecimal.ZERO,
    var childPrice: BigDecimal = BigDecimal.ZERO,
    var timeLimitMinutes: Int = 90,
    var brandId: String? = null,
    var branchId: String? = null,
    var isActive: Boolean = true,
    val createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now(),
    var version: Long = 0
)

data class BuffetTierMenuItemId(
    var buffetTierId: String = "",
    var menuItemId: String = ""
) : Serializable

class BuffetTierMenuItem(
    val id: String = UUID.randomUUID().toString(),
    var buffetTierId: String = "",
    var menuItemId: String = ""
) {
    constructor(buffetTierId: String, menuItemId: String) : this(
        id = "${buffetTierId}_$menuItemId",
        buffetTierId = buffetTierId,
        menuItemId = menuItemId
    )
}

enum class BuffetSessionStatus {
    ACTIVE,
    TIME_WARNING,
    EXPIRED,
    CLOSED
}

class BuffetSession(
    val id: String = UUID.randomUUID().toString(),
    var orderId: String = "",
    var branchId: String = "",
    var buffetTierId: String = "",
    var adultCount: Int = 1,
    var childCount: Int = 0,
    var adultPriceSnapshot: BigDecimal = BigDecimal.ZERO,
    var childPriceSnapshot: BigDecimal = BigDecimal.ZERO,
    var timeLimitMinutes: Int = 90,
    val startedAt: Instant = Instant.now(),
    var expiresAt: Instant = Instant.now().plusSeconds(5400),
    var closedAt: Instant? = null,
    var status: BuffetSessionStatus = BuffetSessionStatus.ACTIVE,
    var createdBy: String? = null,
    val createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now(),
    var version: Long = 0
) {
    fun calculateTotalCharge(): BigDecimal {
        val adultTotal = adultPriceSnapshot.multiply(BigDecimal(adultCount))
        val childTotal = childPriceSnapshot.multiply(BigDecimal(childCount))
        return adultTotal.add(childTotal)
    }

    fun isExpired(): Boolean = Instant.now().isAfter(expiresAt)

    fun remainingMinutes(): Long {
        val remaining = java.time.Duration.between(Instant.now(), expiresAt).toMinutes()
        return remaining.coerceAtLeast(0)
    }
}

// DTOs
data class CreateBuffetPromotionDto(
    val brandId: String = "",
    val branchId: String? = null,
    val name: String = "",
    val pricePerPerson: BigDecimal = BigDecimal.ZERO,
    val durationMinutes: Int = 90,
    val menuItemIds: List<String> = emptyList()
)

data class UpdateBuffetPromotionDto(
    val name: String = "",
    val pricePerPerson: BigDecimal = BigDecimal.ZERO,
    val durationMinutes: Int = 90,
    val status: BuffetPromotionStatus = BuffetPromotionStatus.ACTIVE,
    val menuItemIds: List<String>? = null
)

data class BuffetPromotionResponseDto(
    val id: String = "",
    val brandId: String = "",
    val branchId: String? = null,
    val name: String = "",
    val pricePerPerson: BigDecimal = BigDecimal.ZERO,
    val durationMinutes: Int = 90,
    val status: BuffetPromotionStatus = BuffetPromotionStatus.ACTIVE,
    val eligibleMenuItemCount: Int = 0
)

data class StartBuffetPromotionSessionDto(
    val orderId: String = "",
    val branchId: String = "",
    val promotionId: String = "",
    val headcount: Int = 1,
    val createdBy: String? = null
)

data class CreateBuffetTierDto(
    val promotionId: String = "",
    val name: String = "",
    val adultPrice: BigDecimal = BigDecimal.ZERO,
    val childPrice: BigDecimal = BigDecimal.ZERO,
    val timeLimitMinutes: Int = 90,
    val brandId: String? = null,
    val branchId: String? = null,
    val eligibleMenuItemIds: List<String> = emptyList()
)

data class StartBuffetSessionDto(
    val orderId: String = "",
    val branchId: String = "",
    val buffetTierId: String = "",
    val adultCount: Int = 1,
    val childCount: Int = 0,
    val createdBy: String? = null
)

data class BuffetSessionResponseDto(
    val id: String = "",
    val orderId: String = "",
    val branchId: String = "",
    val buffetTierId: String = "",
    val tierName: String = "",
    val adultCount: Int = 1,
    val childCount: Int = 0,
    val adultPriceSnapshot: BigDecimal = BigDecimal.ZERO,
    val childPriceSnapshot: BigDecimal = BigDecimal.ZERO,
    val totalCharge: BigDecimal = BigDecimal.ZERO,
    val timeLimitMinutes: Int = 90,
    val startedAt: Instant = Instant.now(),
    val expiresAt: Instant = Instant.now(),
    val remainingMinutes: Long = 0,
    val status: BuffetSessionStatus = BuffetSessionStatus.ACTIVE
)

data class BuffetTierResponseDto(
    val id: String = "",
    val promotionId: String = "",
    val name: String = "",
    val adultPrice: BigDecimal = BigDecimal.ZERO,
    val childPrice: BigDecimal = BigDecimal.ZERO,
    val timeLimitMinutes: Int = 90,
    val brandId: String? = null,
    val branchId: String? = null,
    val isActive: Boolean = true,
    val eligibleMenuItemCount: Int = 0
)
