package com.sunpos.backend.domain.crm

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class IdentityType {
    PHONE,
    LINE,
    MEMBER_ID,
    EMAIL,
    OTHER
}

enum class PointTransactionType {
    EARN,
    REDEEM,
    ADJUST,
    EXPIRE,
    REVERSE
}

class Customer(
    val id: String = UUID.randomUUID().toString(),
    var companyId: String = "comp-001",
    var firstName: String = "",
    var lastName: String? = null,
    var displayName: String = "",
    var status: String = "ACTIVE", // ACTIVE, INACTIVE
    var gender: String? = null,
    var birthDate: Instant? = null,
    var customerGroup: String = "GENERAL",
    var primaryBranchId: String? = null,
    var isActive: Boolean = true,
    val createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now(),
    var version: Long = 0L
)

class CustomerIdentity(
    val id: String = UUID.randomUUID().toString(),
    var companyId: String = "comp-001",
    var customerId: String = "",
    var identityType: IdentityType = IdentityType.PHONE,
    var identityValue: String = "",
    var isPrimary: Boolean = false,
    val createdAt: Instant = Instant.now()
)

class MembershipTier(
    val id: String = UUID.randomUUID().toString(),
    var companyId: String = "comp-001",
    var code: String = "",
    var name: String = "",
    var rankLevel: Int = 1,
    var minimumSpent: BigDecimal = BigDecimal.ZERO,
    var pointMultiplier: BigDecimal = BigDecimal.ONE,
    var discountPercentage: BigDecimal = BigDecimal.ZERO,
    var isActive: Boolean = true,
    val createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now()
)

class CustomerMembership(
    val id: String = UUID.randomUUID().toString(),
    var customerId: String = "",
    var membershipTierId: String = "",
    var effectiveDate: Instant = Instant.now(),
    var expirationDate: Instant? = null,
    var currentSpent: BigDecimal = BigDecimal.ZERO,
    var updatedAt: Instant = Instant.now()
)

class PointLedger(
    val id: String = UUID.randomUUID().toString(),
    var customerId: String = "",
    var transactionType: PointTransactionType = PointTransactionType.EARN,
    var points: BigDecimal = BigDecimal.ZERO,
    var balanceAfter: BigDecimal = BigDecimal.ZERO,
    var referenceType: String? = null, // ORDER, MANUAL_ADJUST, REVERSAL
    var referenceId: String? = null,
    var notes: String? = null,
    var createdBy: String? = null,
    val createdAt: Instant = Instant.now()
)

class CustomerSegment(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var code: String = "",
    var description: String? = null,
    var minPurchaseFrequency: Int = 0,
    var minTotalSpending: BigDecimal = BigDecimal.ZERO,
    var maxRecencyDays: Int = 365,
    var favoriteCategory: String? = null,
    var isActive: Boolean = true
)

// ── DTOs ──

data class CustomerIdentityDto(
    val id: String? = null,
    val identityType: IdentityType = IdentityType.PHONE,
    val identityValue: String = "",
    val isPrimary: Boolean = false
)

data class CreateCustomerIdentityDto(
    val identityType: IdentityType = IdentityType.PHONE,
    val identityValue: String = "",
    val isPrimary: Boolean = false
)

data class CreateCustomerDto(
    val displayName: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val phone: String? = null,
    val lineId: String? = null,
    val email: String? = null,
    val memberId: String? = null,
    val identities: List<CreateCustomerIdentityDto> = emptyList(),
    val customerGroup: String = "GENERAL",
    val primaryBranchId: String? = null,
    val status: String = "ACTIVE"
)

data class UpdateCustomerDto(
    val displayName: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val status: String? = null, // ACTIVE, INACTIVE
    val customerGroup: String? = null,
    val primaryBranchId: String? = null
)

data class AddIdentityDto(
    val identityType: IdentityType = IdentityType.PHONE,
    val identityValue: String = "",
    val isPrimary: Boolean = false
)

data class CustomerResponseDto(
    val id: String = "",
    val companyId: String = "",
    val displayName: String = "",
    val firstName: String = "",
    val lastName: String? = null,
    val status: String = "ACTIVE",
    val customerGroup: String = "GENERAL",
    val primaryBranchId: String? = null,
    val identities: List<CustomerIdentityDto> = emptyList(),
    val membershipTier: String? = null,
    val currentPointsBalance: BigDecimal = BigDecimal.ZERO,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val version: Long = 0L
)

data class CustomerDetailsDto(
    val customer: Customer,
    val identities: List<CustomerIdentity> = emptyList(),
    val membership: CustomerMembership? = null,
    val currentTier: MembershipTier? = null,
    val currentPointsBalance: BigDecimal = BigDecimal.ZERO
)

data class CreateMembershipTierDto(
    val code: String = "",
    val name: String = "",
    val rankLevel: Int = 1,
    val minimumSpent: BigDecimal = BigDecimal.ZERO,
    val pointMultiplier: BigDecimal = BigDecimal.ONE,
    val discountPercentage: BigDecimal = BigDecimal.ZERO,
    val companyId: String? = null,
    val isActive: Boolean = true
)

data class UpdateMembershipTierDto(
    val name: String? = null,
    val rankLevel: Int? = null,
    val minimumSpent: BigDecimal? = null,
    val pointMultiplier: BigDecimal? = null,
    val discountPercentage: BigDecimal? = null,
    val isActive: Boolean? = null
)

data class MembershipTierResponseDto(
    val id: String = "",
    val companyId: String = "",
    val code: String = "",
    val name: String = "",
    val rankLevel: Int = 1,
    val minimumSpent: BigDecimal = BigDecimal.ZERO,
    val pointMultiplier: BigDecimal = BigDecimal.ONE,
    val discountPercentage: BigDecimal = BigDecimal.ZERO,
    val isActive: Boolean = true,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now()
)

data class CustomerMembershipResponseDto(
    val id: String = "",
    val customerId: String = "",
    val customerDisplayName: String = "",
    val tierId: String = "",
    val tierCode: String = "",
    val tierName: String = "",
    val rankLevel: Int = 1,
    val minimumSpent: BigDecimal = BigDecimal.ZERO,
    val pointMultiplier: BigDecimal = BigDecimal.ONE,
    val discountPercentage: BigDecimal = BigDecimal.ZERO,
    val currentSpent: BigDecimal = BigDecimal.ZERO,
    val nextTierName: String? = null,
    val spentNeededForNextTier: BigDecimal? = null,
    val effectiveDate: Instant = Instant.now(),
    val expirationDate: Instant? = null,
    val updatedAt: Instant = Instant.now()
)

data class EvaluateMembershipRequestDto(
    val addedSpend: BigDecimal = BigDecimal.ZERO,
    val reason: String? = null
)

data class EvaluateMembershipResponseDto(
    val customerId: String = "",
    val previousTierCode: String = "",
    val currentTierCode: String = "",
    val currentSpent: BigDecimal = BigDecimal.ZERO,
    val isUpgraded: Boolean = false,
    val message: String = "",
    val membership: CustomerMembershipResponseDto
)

data class PointTransactionDto(
    val customerId: String = "",
    val points: BigDecimal = BigDecimal.ZERO,
    val referenceType: String? = null,
    val referenceId: String? = null,
    val notes: String? = null,
    val operatorId: String? = null
)

data class PointBalanceResponseDto(
    val customerId: String = "",
    val balance: BigDecimal = BigDecimal.ZERO,
    val totalEarned: BigDecimal = BigDecimal.ZERO,
    val totalRedeemed: BigDecimal = BigDecimal.ZERO,
    val latestTransactionAt: Instant? = null
)

data class RedeemPointsRequestDto(
    val points: BigDecimal = BigDecimal.ZERO,
    val orderId: String = "",
    val notes: String? = null,
    val operatorId: String? = null
)

data class RedeemPointsResponseDto(
    val customerId: String = "",
    val orderId: String = "",
    val redeemedPoints: BigDecimal = BigDecimal.ZERO,
    val discountAmount: BigDecimal = BigDecimal.ZERO,
    val balanceAfter: BigDecimal = BigDecimal.ZERO,
    val ledgerId: String = ""
)

data class AdjustPointsRequestDto(
    val points: BigDecimal = BigDecimal.ZERO,
    val reason: String = "",
    val operatorId: String? = null,
    val referenceId: String? = null
)

data class ValidateCouponRequestDto(
    val code: String = "",
    val orderAmount: BigDecimal = BigDecimal.ZERO,
    val branchId: String? = null
)

data class CouponValidationResponseDto(
    val isValid: Boolean = false,
    val couponCode: String = "",
    val promotionName: String = "",
    val calculatedDiscountAmount: BigDecimal = BigDecimal.ZERO,
    val message: String = ""
)
