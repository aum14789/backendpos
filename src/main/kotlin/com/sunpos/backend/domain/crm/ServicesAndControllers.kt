package com.sunpos.backend.domain.crm

import com.sunpos.backend.common.ApiResponse
import com.sunpos.backend.common.JdbcRepository
import org.springframework.jdbc.core.JdbcTemplate
import com.sunpos.backend.common.PhoneUtils
import com.sunpos.backend.domain.identity.UserRepository
import com.sunpos.backend.domain.order.Order
import com.sunpos.backend.domain.order.OrderRepository
import com.sunpos.backend.domain.promotion.CouponRepository
import com.sunpos.backend.domain.promotion.PromotionRepository
import com.sunpos.backend.domain.promotion.PromotionType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.Principal
import java.time.Instant
import java.time.ZoneId
import java.util.Optional

@Repository
class CustomerRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<Customer>(jdbcTemplate, "customers", Customer::class.java) {
    fun findByCompanyId(companyId: String): List<Customer> = findByField("companyId", companyId)
    fun findByIdAndCompanyId(id: String, companyId: String): Optional<Customer> {
        val cust = findById(id)
        return if (cust.isPresent && cust.get().companyId == companyId) cust else Optional.empty()
    }
    fun findByCompanyIdAndStatus(companyId: String, status: String): List<Customer> =
        findByFields(mapOf("companyId" to companyId, "status" to status))
    fun findByCompanyIdAndDisplayNameContainingIgnoreCase(companyId: String, name: String): List<Customer> =
        findByField("companyId", companyId).filter { it.displayName.contains(name, ignoreCase = true) }
    fun findByCompanyIdAndFirstNameContainingIgnoreCaseOrCompanyIdAndLastNameContainingIgnoreCase(
        comp1: String, first: String,
        comp2: String, last: String
    ): List<Customer> = findByField("companyId", comp1).filter {
        it.firstName.contains(first, ignoreCase = true) || (it.lastName?.contains(last, ignoreCase = true) == true)
    }
}

@Repository
class CustomerIdentityRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<CustomerIdentity>(jdbcTemplate, "customer_identities", CustomerIdentity::class.java) {
    fun findByCustomerId(customerId: String): List<CustomerIdentity> = findByField("customerId", customerId)
    fun findByCompanyIdAndCustomerId(companyId: String, customerId: String): List<CustomerIdentity> =
        findByFields(mapOf("companyId" to companyId, "customerId" to customerId))
    fun findByCompanyIdAndIdentityTypeAndIdentityValue(companyId: String, identityType: IdentityType, identityValue: String): Optional<CustomerIdentity> {
        val list = findByFields(mapOf("companyId" to companyId, "identityType" to identityType, "identityValue" to identityValue))
        return Optional.ofNullable(list.firstOrNull())
    }
    fun findByIdentityTypeAndIdentityValue(identityType: IdentityType, identityValue: String): Optional<CustomerIdentity> {
        val list = findByFields(mapOf("identityType" to identityType, "identityValue" to identityValue))
        return Optional.ofNullable(list.firstOrNull())
    }
    fun findByCompanyIdAndIdentityValueContaining(companyId: String, value: String): List<CustomerIdentity> =
        findByField("companyId", companyId).filter { it.identityValue.contains(value) }
}

@Repository
class MembershipTierRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<MembershipTier>(jdbcTemplate, "membership_tiers", MembershipTier::class.java) {
    fun findByCode(code: String): Optional<MembershipTier> = findOneByField("code", code)
    fun findByCompanyIdAndCode(companyId: String, code: String): Optional<MembershipTier> {
        val list = findByFields(mapOf("companyId" to companyId, "code" to code))
        return Optional.ofNullable(list.firstOrNull())
    }
    fun findByCompanyIdAndIsActiveTrueOrderByRankLevelAsc(companyId: String): List<MembershipTier> =
        findByFields(mapOf("companyId" to companyId, "isActive" to true)).sortedBy { it.rankLevel }
    fun findByCompanyIdOrderByRankLevelAsc(companyId: String): List<MembershipTier> =
        findByField("companyId", companyId).sortedBy { it.rankLevel }
    fun findByCompanyId(companyId: String): List<MembershipTier> = findByField("companyId", companyId)
}

@Repository
class CustomerMembershipRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<CustomerMembership>(jdbcTemplate, "customer_memberships", CustomerMembership::class.java) {
    fun findByCustomerId(customerId: String): Optional<CustomerMembership> = findOneByField("customerId", customerId)
}

@Repository
class PointLedgerRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<PointLedger>(jdbcTemplate, "point_ledgers", PointLedger::class.java) {
    fun findByCustomerIdOrderByCreatedAtDesc(customerId: String): List<PointLedger> =
        findByField("customerId", customerId).sortedByDescending { it.createdAt }
}

@Repository
class CustomerSegmentRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<CustomerSegment>(jdbcTemplate, "customer_segments", CustomerSegment::class.java)

@Service
class CrmService(
    private val customerRepository: CustomerRepository,
    private val identityRepository: CustomerIdentityRepository,
    private val tierRepository: MembershipTierRepository,
    private val membershipRepository: CustomerMembershipRepository,
    private val pointLedgerRepository: PointLedgerRepository,
    private val couponRepository: CouponRepository,
    private val promotionRepository: PromotionRepository,
    private val segmentRepository: CustomerSegmentRepository,
    private val orderRepository: OrderRepository,
    private val userRepository: UserRepository,
    private val couponService: com.sunpos.backend.domain.promotion.CouponService
) {
    companion object {
        const val SCALE = 4
        val ROUNDING = RoundingMode.HALF_UP
    }

    fun resolveCompanyId(principalName: String?): String {
        if (!principalName.isNullOrBlank()) {
            val user = userRepository.findByUsername(principalName)
            if (user.isPresent) {
                return user.get().companyId
            }
        }
        return "comp-001" // Default fallback company
    }

    fun ensureDefaultTiers(companyId: String = "comp-001") {
        if (tierRepository.findByCompanyIdAndCode(companyId, "SILVER").isEmpty && tierRepository.findByCode("SILVER").isEmpty) {
            tierRepository.save(
                MembershipTier(
                    id = "tier-silver",
                    companyId = companyId,
                    code = "SILVER",
                    name = "สมาชิกทั่วไป (Silver)",
                    rankLevel = 1,
                    minimumSpent = BigDecimal.ZERO,
                    pointMultiplier = BigDecimal.ONE,
                    discountPercentage = BigDecimal.ZERO,
                    isActive = true
                )
            )
        }
        if (tierRepository.findByCompanyIdAndCode(companyId, "GOLD").isEmpty && tierRepository.findByCode("GOLD").isEmpty) {
            tierRepository.save(
                MembershipTier(
                    id = "tier-gold",
                    companyId = companyId,
                    code = "GOLD",
                    name = "สมาชิก VIP (Gold)",
                    rankLevel = 2,
                    minimumSpent = BigDecimal("5000.00"),
                    pointMultiplier = BigDecimal("1.50"),
                    discountPercentage = BigDecimal("5.00"),
                    isActive = true
                )
            )
        }
        if (tierRepository.findByCompanyIdAndCode(companyId, "PLATINUM").isEmpty && tierRepository.findByCode("PLATINUM").isEmpty) {
            tierRepository.save(
                MembershipTier(
                    id = "tier-platinum",
                    companyId = companyId,
                    code = "PLATINUM",
                    name = "สมาชิก VVIP (Platinum)",
                    rankLevel = 3,
                    minimumSpent = BigDecimal("20000.00"),
                    pointMultiplier = BigDecimal("2.00"),
                    discountPercentage = BigDecimal("10.00"),
                    isActive = true
                )
            )
        }
    }

    // ── 1. Create or Find Existing Customer with Identities & Default Tier ──

    @Transactional
    fun createCustomer(dto: CreateCustomerDto, companyId: String = "comp-001"): CustomerDetailsDto {
        ensureDefaultTiers(companyId)

        // Prepare identities to be created
        val identitiesToCreate = mutableListOf<CreateCustomerIdentityDto>()

        // 1. Add explicit identities from list if present
        for (ident in dto.identities) {
            val normVal = if (ident.identityType == IdentityType.PHONE) PhoneUtils.normalize(ident.identityValue) else ident.identityValue.trim()
            if (normVal.isNotBlank()) {
                identitiesToCreate.add(ident.copy(identityValue = normVal))
            }
        }

        // 2. Add shorthand phone if provided
        dto.phone?.let { p ->
            val norm = PhoneUtils.normalize(p)
            if (norm.isNotBlank() && identitiesToCreate.none { it.identityType == IdentityType.PHONE && it.identityValue == norm }) {
                identitiesToCreate.add(CreateCustomerIdentityDto(IdentityType.PHONE, norm, isPrimary = true))
            }
        }

        // 3. Add shorthand member ID if provided
        dto.memberId?.let { m ->
            val trimmed = m.trim()
            if (trimmed.isNotBlank() && identitiesToCreate.none { it.identityType == IdentityType.MEMBER_ID && it.identityValue == trimmed }) {
                identitiesToCreate.add(CreateCustomerIdentityDto(IdentityType.MEMBER_ID, trimmed, isPrimary = false))
            }
        }

        // 4. Add shorthand LINE if provided
        dto.lineId?.let { l ->
            val trimmed = l.trim()
            if (trimmed.isNotBlank() && identitiesToCreate.none { it.identityType == IdentityType.LINE && it.identityValue == trimmed }) {
                identitiesToCreate.add(CreateCustomerIdentityDto(IdentityType.LINE, trimmed, isPrimary = false))
            }
        }

        // 5. Add shorthand Email if provided
        dto.email?.let { e ->
            val trimmed = e.trim()
            if (trimmed.isNotBlank() && identitiesToCreate.none { it.identityType == IdentityType.EMAIL && it.identityValue == trimmed }) {
                identitiesToCreate.add(CreateCustomerIdentityDto(IdentityType.EMAIL, trimmed, isPrimary = false))
            }
        }

        // Rule: Check if ANY phone identity already exists in this company (no duplicate customer per phone in same company)
        val phoneIdentity = identitiesToCreate.firstOrNull { it.identityType == IdentityType.PHONE }
        if (phoneIdentity != null) {
            val existingIdent = identityRepository.findByCompanyIdAndIdentityTypeAndIdentityValue(
                companyId, IdentityType.PHONE, phoneIdentity.identityValue
            )
            if (existingIdent.isPresent) {
                val existingCustomer = customerRepository.findByIdAndCompanyId(existingIdent.get().customerId, companyId)
                if (existingCustomer.isPresent) {
                    // Return existing customer details without creating duplicate
                    return getCustomerDetails(existingCustomer.get().id, companyId)
                }
            }
        }

        if (identitiesToCreate.isEmpty()) {
            throw IllegalArgumentException("At least one valid customer identity is required (e.g. PHONE, LINE, EMAIL, MEMBER_ID)")
        }

        val finalFirstName = dto.firstName?.trim()?.ifBlank { null }
            ?: dto.displayName?.trim()?.split(" ")?.firstOrNull()
            ?: "Customer"

        val finalLastName = dto.lastName?.trim()?.ifBlank { null }
            ?: dto.displayName?.trim()?.split(" ")?.drop(1)?.joinToString(" ")?.ifBlank { null }

        val finalDisplayName = dto.displayName?.trim()?.ifBlank { null }
            ?: listOfNotNull(finalFirstName, finalLastName).joinToString(" ")

        val customer = Customer(
            companyId = companyId,
            firstName = finalFirstName,
            lastName = finalLastName,
            displayName = finalDisplayName,
            status = if (dto.status.equals("INACTIVE", ignoreCase = true)) "INACTIVE" else "ACTIVE",
            customerGroup = dto.customerGroup,
            primaryBranchId = dto.primaryBranchId
        )
        val savedCustomer = customerRepository.save(customer)

        // Save all identities with companyId
        var hasPrimary = false
        for (ident in identitiesToCreate) {
            val isPrim = if (!hasPrimary && (ident.isPrimary || ident.identityType == IdentityType.PHONE)) {
                hasPrimary = true
                true
            } else {
                ident.isPrimary
            }

            identityRepository.save(
                CustomerIdentity(
                    companyId = companyId,
                    customerId = savedCustomer.id,
                    identityType = ident.identityType,
                    identityValue = ident.identityValue,
                    isPrimary = isPrim
                )
            )
        }

        // Rule 1: Assign Default Initial Membership Tier (e.g. lowest rankLevel or SILVER)
        val defaultTier = getDefaultTier(companyId)
        val membership = CustomerMembership(
            customerId = savedCustomer.id,
            membershipTierId = defaultTier.id,
            currentSpent = BigDecimal.ZERO,
            effectiveDate = Instant.now()
        )
        membershipRepository.save(membership)

        return getCustomerDetails(savedCustomer.id, companyId)
    }

    private fun getDefaultTier(companyId: String): MembershipTier {
        ensureDefaultTiers(companyId)
        val activeTiers = tierRepository.findByCompanyIdAndIsActiveTrueOrderByRankLevelAsc(companyId)
        if (activeTiers.isNotEmpty()) {
            return activeTiers.first()
        }
        return tierRepository.findByCode("SILVER").orElseGet {
            tierRepository.findAll().firstOrNull() ?: throw IllegalStateException("No default membership tier configured")
        }
    }

    // ── 2. Get Customer by ID with Company Isolation ──

    fun getCustomerDetails(customerId: String, companyId: String? = null): CustomerDetailsDto {
        val resolvedCompanyId = companyId ?: "comp-001"
        ensureDefaultTiers(resolvedCompanyId)
        val customer = if (companyId != null) {
            customerRepository.findByIdAndCompanyId(customerId, companyId)
                .orElseThrow { NoSuchElementException("Customer '$customerId' not found in company '$companyId'") }
        } else {
            customerRepository.findById(customerId)
                .orElseThrow { NoSuchElementException("Customer '$customerId' not found") }
        }

        val identities = identityRepository.findByCustomerId(customerId)
        val membershipOpt = membershipRepository.findByCustomerId(customerId).or {
            // Self-heal: ensure default tier exists if missing
            val defTier = getDefaultTier(customer.companyId)
            val newMem = membershipRepository.save(
                CustomerMembership(
                    customerId = customer.id,
                    membershipTierId = defTier.id,
                    currentSpent = BigDecimal.ZERO
                )
            )
            Optional.of(newMem)
        }
        val tier = membershipOpt.map { tierRepository.findById(it.membershipTierId).orElse(null) }.orElse(null)
        val currentPoints = calculatePointsBalance(customerId)

        return CustomerDetailsDto(
            customer = customer,
            identities = identities,
            membership = membershipOpt.orElse(null),
            currentTier = tier,
            currentPointsBalance = currentPoints
        )
    }

    // ── 3. Search Customer by Normalized Phone or General Query (Multi-Identity) ──

    fun searchCustomers(phone: String?, query: String?, companyId: String): List<CustomerDetailsDto> {
        ensureDefaultTiers(companyId)

        val results = LinkedHashMap<String, CustomerDetailsDto>()

        // 1. Search by Phone (normalized +66, spaces, hyphens)
        if (!phone.isNullOrBlank()) {
            val normalizedPhone = PhoneUtils.normalize(phone)
            if (normalizedPhone.isNotBlank()) {
                val phoneMatches = identityRepository.findByCompanyIdAndIdentityTypeAndIdentityValue(
                    companyId, IdentityType.PHONE, normalizedPhone
                )
                phoneMatches.ifPresent { ident ->
                    val details = getCustomerDetails(ident.customerId, companyId)
                    results[details.customer.id] = details
                }

                // Fallback: partial normalized phone match
                if (results.isEmpty() && normalizedPhone.length >= 4) {
                    val partials = identityRepository.findByCompanyIdAndIdentityValueContaining(companyId, normalizedPhone)
                    for (p in partials) {
                        val details = getCustomerDetails(p.customerId, companyId)
                        results[details.customer.id] = details
                    }
                }
            }
        }

        // 2. Search by General Query (?q=...)
        if (!query.isNullOrBlank()) {
            val trimmed = query.trim()
            val normalizedAsPhone = PhoneUtils.normalize(trimmed)

            // Try normalized phone match first
            if (normalizedAsPhone.length >= 8) {
                val phoneMatch = identityRepository.findByCompanyIdAndIdentityTypeAndIdentityValue(
                    companyId, IdentityType.PHONE, normalizedAsPhone
                )
                phoneMatch.ifPresent { ident ->
                    val details = getCustomerDetails(ident.customerId, companyId)
                    results[details.customer.id] = details
                }
            }

            // Search by exact identity value (MEMBER_ID, LINE, EMAIL)
            for (type in listOf(IdentityType.MEMBER_ID, IdentityType.LINE, IdentityType.EMAIL, IdentityType.PHONE)) {
                val identMatch = identityRepository.findByCompanyIdAndIdentityTypeAndIdentityValue(companyId, type, trimmed)
                identMatch.ifPresent { ident ->
                    val details = getCustomerDetails(ident.customerId, companyId)
                    results[details.customer.id] = details
                }
            }

            // Partial identity match
            val partialIdentities = identityRepository.findByCompanyIdAndIdentityValueContaining(companyId, trimmed)
            for (p in partialIdentities) {
                val details = getCustomerDetails(p.customerId, companyId)
                results[details.customer.id] = details
            }

            // Search by Display Name or First/Last Name in company
            val nameMatches = customerRepository.findByCompanyIdAndDisplayNameContainingIgnoreCase(companyId, trimmed)
            for (cust in nameMatches) {
                val details = getCustomerDetails(cust.id, companyId)
                results[details.customer.id] = details
            }
        }

        return results.values.toList()
    }

    fun searchCustomerByIdentity(value: String, companyId: String = "comp-001"): Optional<CustomerDetailsDto> {
        val list = searchCustomers(phone = value, query = value, companyId = companyId)
        return if (list.isNotEmpty()) Optional.of(list.first()) else Optional.empty()
    }

    // ── 4. PATCH Customer (Update Name / Status / Group) ──

    @Transactional
    fun updateCustomer(customerId: String, dto: UpdateCustomerDto, companyId: String): CustomerDetailsDto {
        val customer = customerRepository.findByIdAndCompanyId(customerId, companyId)
            .orElseThrow { NoSuchElementException("Customer '$customerId' not found in company '$companyId'") }

        dto.firstName?.takeIf { it.isNotBlank() }?.let { customer.firstName = it.trim() }
        dto.lastName?.let { customer.lastName = it.trim().ifBlank { null } }
        dto.displayName?.takeIf { it.isNotBlank() }?.let { customer.displayName = it.trim() }
        dto.status?.takeIf { it.isNotBlank() }?.let {
            val upper = it.trim().uppercase()
            customer.status = if (upper == "INACTIVE") "INACTIVE" else "ACTIVE"
            customer.isActive = (customer.status == "ACTIVE")
        }
        dto.customerGroup?.takeIf { it.isNotBlank() }?.let { customer.customerGroup = it.trim() }
        dto.primaryBranchId?.let { customer.primaryBranchId = it.trim().ifBlank { null } }

        customerRepository.save(customer)
        return getCustomerDetails(customerId, companyId)
    }

    // ── 5. Add Customer Identity ──

    @Transactional
    fun addCustomerIdentity(customerId: String, dto: AddIdentityDto, companyId: String): CustomerDetailsDto {
        val customer = customerRepository.findByIdAndCompanyId(customerId, companyId)
            .orElseThrow { NoSuchElementException("Customer '$customerId' not found in company '$companyId'") }

        val normVal = if (dto.identityType == IdentityType.PHONE) PhoneUtils.normalize(dto.identityValue) else dto.identityValue.trim()
        if (normVal.isBlank()) {
            throw IllegalArgumentException("Identity value cannot be blank")
        }

        // Check if identity already exists
        val existing = identityRepository.findByCompanyIdAndIdentityTypeAndIdentityValue(
            companyId, dto.identityType, normVal
        )
        if (existing.isPresent) {
            if (existing.get().customerId == customerId) {
                return getCustomerDetails(customerId, companyId) // Already belongs to this customer
            }
            throw IllegalArgumentException("Identity '${dto.identityType}' with value '$normVal' is already registered to another customer")
        }

        identityRepository.save(
            CustomerIdentity(
                companyId = companyId,
                customerId = customer.id,
                identityType = dto.identityType,
                identityValue = normVal,
                isPrimary = dto.isPrimary
            )
        )

        return getCustomerDetails(customerId, companyId)
    }

    // ── 6. Membership Tier Management & Evaluation ──

    @Transactional
    fun createTier(dto: CreateMembershipTierDto, companyId: String): MembershipTierResponseDto {
        val comp = dto.companyId ?: companyId
        ensureDefaultTiers(comp)

        val cleanCode = dto.code.trim().uppercase()
        val existing = tierRepository.findByCompanyIdAndCode(comp, cleanCode)
        if (existing.isPresent) {
            throw IllegalArgumentException("Membership Tier with code '$cleanCode' already exists in company '$comp'")
        }

        val tier = MembershipTier(
            companyId = comp,
            code = cleanCode,
            name = dto.name.trim(),
            rankLevel = dto.rankLevel,
            minimumSpent = dto.minimumSpent.setScale(SCALE, ROUNDING),
            pointMultiplier = dto.pointMultiplier.setScale(2, ROUNDING),
            discountPercentage = dto.discountPercentage.setScale(2, ROUNDING),
            isActive = dto.isActive
        )
        val saved = tierRepository.save(tier)
        return saved.toDto()
    }

    fun getTier(tierId: String, companyId: String): MembershipTierResponseDto {
        ensureDefaultTiers(companyId)
        val tier = tierRepository.findById(tierId)
            .orElseThrow { NoSuchElementException("Membership Tier '$tierId' not found") }
        return tier.toDto()
    }

    @Transactional
    fun updateTier(tierId: String, dto: UpdateMembershipTierDto, companyId: String): MembershipTierResponseDto {
        ensureDefaultTiers(companyId)
        val tier = tierRepository.findById(tierId)
            .orElseThrow { NoSuchElementException("Membership Tier '$tierId' not found") }

        dto.name?.takeIf { it.isNotBlank() }?.let { tier.name = it.trim() }
        dto.rankLevel?.let { tier.rankLevel = it }
        dto.minimumSpent?.let { tier.minimumSpent = it.setScale(SCALE, ROUNDING) }
        dto.pointMultiplier?.let { tier.pointMultiplier = it.setScale(2, ROUNDING) }
        dto.discountPercentage?.let { tier.discountPercentage = it.setScale(2, ROUNDING) }
        dto.isActive?.let { tier.isActive = it }
        tier.updatedAt = Instant.now()

        val saved = tierRepository.save(tier)
        return saved.toDto()
    }

    @Transactional
    fun deleteTier(tierId: String, companyId: String): Boolean {
        ensureDefaultTiers(companyId)
        val tier = tierRepository.findById(tierId)
            .orElseThrow { NoSuchElementException("Membership Tier '$tierId' not found") }
        tier.isActive = false
        tier.updatedAt = Instant.now()
        tierRepository.save(tier)
        return true
    }

    fun listTiers(companyId: String = "comp-001"): List<MembershipTierResponseDto> {
        ensureDefaultTiers(companyId)
        val list = tierRepository.findByCompanyIdOrderByRankLevelAsc(companyId)
        if (list.isNotEmpty()) {
            return list.map { it.toDto() }
        }
        return tierRepository.findAll().sortedBy { it.rankLevel }.map { it.toDto() }
    }

    fun getCustomerMembership(customerId: String, companyId: String? = null): CustomerMembershipResponseDto {
        val details = getCustomerDetails(customerId, companyId)
        val membership = details.membership ?: throw NoSuchElementException("No membership found for customer '$customerId'")
        val tier = details.currentTier ?: getDefaultTier(details.customer.companyId)

        // Determine next tier
        val allActiveTiers = tierRepository.findByCompanyIdAndIsActiveTrueOrderByRankLevelAsc(details.customer.companyId)
            .ifEmpty { tierRepository.findAll().filter { it.isActive }.sortedBy { it.rankLevel } }

        val nextTier = allActiveTiers.firstOrNull { it.rankLevel > tier.rankLevel && it.minimumSpent > membership.currentSpent }
        val needed = nextTier?.let { it.minimumSpent.subtract(membership.currentSpent).max(BigDecimal.ZERO).setScale(SCALE, ROUNDING) }

        return CustomerMembershipResponseDto(
            id = membership.id,
            customerId = customerId,
            customerDisplayName = details.customer.displayName,
            tierId = tier.id,
            tierCode = tier.code,
            tierName = tier.name,
            rankLevel = tier.rankLevel,
            minimumSpent = tier.minimumSpent,
            pointMultiplier = tier.pointMultiplier,
            discountPercentage = tier.discountPercentage,
            currentSpent = membership.currentSpent,
            nextTierName = nextTier?.name,
            spentNeededForNextTier = needed,
            effectiveDate = membership.effectiveDate,
            expirationDate = membership.expirationDate,
            updatedAt = membership.updatedAt
        )
    }

    /**
     * Centralized Membership Evaluation & Tier Upgrade Rule Engine.
     * Evaluates current cumulative spent, finds eligible tiers, and upgrades if higher rank is reached.
     */
    @Transactional
    fun evaluateAndUpgradeMembership(customerId: String, addedSpend: BigDecimal = BigDecimal.ZERO): EvaluateMembershipResponseDto {
        val details = getCustomerDetails(customerId)
        val customer = details.customer
        val membership = details.membership ?: run {
            val defTier = getDefaultTier(customer.companyId)
            membershipRepository.save(
                CustomerMembership(
                    customerId = customer.id,
                    membershipTierId = defTier.id,
                    currentSpent = BigDecimal.ZERO
                )
            )
        }

        val previousTier = tierRepository.findById(membership.membershipTierId).orElseGet { getDefaultTier(customer.companyId) }
        val previousTierCode = previousTier.code

        if (addedSpend > BigDecimal.ZERO) {
            membership.currentSpent = membership.currentSpent.add(addedSpend).setScale(SCALE, ROUNDING)
        }

        // Evaluate all active tiers in the company
        val allActiveTiers = tierRepository.findByCompanyIdAndIsActiveTrueOrderByRankLevelAsc(customer.companyId)
            .ifEmpty { tierRepository.findAll().filter { it.isActive }.sortedBy { it.rankLevel } }

        // Find highest tier matching currentSpent
        val eligibleTiers = allActiveTiers.filter { membership.currentSpent.compareTo(it.minimumSpent) >= 0 }
        val highestEligibleTier = eligibleTiers.maxByOrNull { it.rankLevel } ?: previousTier

        var isUpgraded = false
        if (highestEligibleTier.rankLevel > previousTier.rankLevel) {
            membership.membershipTierId = highestEligibleTier.id
            membership.updatedAt = Instant.now()
            isUpgraded = true
        }

        membershipRepository.save(membership)

        val updatedDto = getCustomerMembership(customerId, customer.companyId)
        val message = if (isUpgraded) {
            "อัปเกรดระดับสมาชิกสำเร็จเป็น ${highestEligibleTier.name} (ยอดสะสม ฿${membership.currentSpent.toFixed()})"
        } else {
            "คงระดับสมาชิก ${highestEligibleTier.name} (ยอดสะสม ฿${membership.currentSpent.toFixed()})"
        }

        return EvaluateMembershipResponseDto(
            customerId = customerId,
            previousTierCode = previousTierCode,
            currentTierCode = highestEligibleTier.code,
            currentSpent = membership.currentSpent,
            isUpgraded = isUpgraded,
            message = message,
            membership = updatedDto
        )
    }

    // ── 7. Loyalty Points Calculations & Ledger ──

    fun calculatePointsBalance(customerId: String): BigDecimal {
        val ledgers = pointLedgerRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
        if (ledgers.isEmpty()) return BigDecimal.ZERO.setScale(SCALE, ROUNDING)
        return ledgers.first().balanceAfter.setScale(SCALE, ROUNDING)
    }

    fun getPointBalanceDetails(customerId: String): PointBalanceResponseDto {
        val ledgers = pointLedgerRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
        val currentBal = if (ledgers.isEmpty()) BigDecimal.ZERO.setScale(SCALE, ROUNDING) else ledgers.first().balanceAfter.setScale(SCALE, ROUNDING)
        val totalEarned = ledgers
            .filter { it.transactionType == PointTransactionType.EARN || (it.transactionType == PointTransactionType.ADJUST && it.points > BigDecimal.ZERO) }
            .fold(BigDecimal.ZERO) { acc, l -> acc.add(l.points) }
            .setScale(SCALE, ROUNDING)
        val totalRedeemed = ledgers
            .filter { it.transactionType == PointTransactionType.REDEEM }
            .fold(BigDecimal.ZERO) { acc, l -> acc.add(l.points.abs()) }
            .setScale(SCALE, ROUNDING)

        return PointBalanceResponseDto(
            customerId = customerId,
            balance = currentBal,
            totalEarned = totalEarned,
            totalRedeemed = totalRedeemed,
            latestTransactionAt = ledgers.firstOrNull()?.createdAt
        )
    }

    @Transactional
    fun earnPointsForOrder(customerId: String, orderId: String, netOrderAmount: BigDecimal): PointLedger {
        ensureDefaultTiers()

        // Idempotency check: Do not re-earn points if already earned for this order
        val existingEarn = pointLedgerRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
            .firstOrNull { it.referenceId == orderId && it.transactionType == PointTransactionType.EARN }
        if (existingEarn != null) {
            return existingEarn
        }

        val currentBal = calculatePointsBalance(customerId)
        val membershipOpt = membershipRepository.findByCustomerId(customerId)
        val multiplier = membershipOpt
            .flatMap { tierRepository.findById(it.membershipTierId) }
            .map { it.pointMultiplier }
            .orElse(BigDecimal.ONE)

        // MVP Rule: 1 Point for every 25 THB net spent * tier point multiplier
        val spendPerPoint = BigDecimal("25.0000")
        val basePoints = if (netOrderAmount >= spendPerPoint) {
            netOrderAmount.divide(spendPerPoint, 0, RoundingMode.FLOOR)
        } else {
            BigDecimal.ZERO
        }
        val earnedPoints = basePoints.multiply(multiplier).setScale(SCALE, ROUNDING)
        val newBal = currentBal.add(earnedPoints).setScale(SCALE, ROUNDING)

        val ledger = PointLedger(
            customerId = customerId,
            transactionType = PointTransactionType.EARN,
            points = earnedPoints,
            balanceAfter = newBal,
            referenceType = "ORDER",
            referenceId = orderId,
            notes = "สะสมแต้มจากยอดสั่งซื้อสุทธิ ฿${netOrderAmount.toFixed()} (ทุก ฿25 = 1 แต้ม × ${multiplier.toFixed()}x)"
        )
        return pointLedgerRepository.save(ledger)
    }

    @Transactional
    fun redeemPoints(
        customerId: String,
        pointsToRedeem: BigDecimal,
        orderId: String,
        notes: String? = null,
        operatorId: String? = null
    ): RedeemPointsResponseDto {
        val currentBal = calculatePointsBalance(customerId)
        val reqPts = pointsToRedeem.setScale(SCALE, ROUNDING)

        if (reqPts <= BigDecimal.ZERO) {
            throw IllegalArgumentException("จำนวนแต้มที่ต้องการแลกต้องมากกว่า 0 (pointsToRedeem must be positive)")
        }
        if (currentBal.compareTo(reqPts) < 0) {
            throw IllegalArgumentException("ยอดแต้มคงเหลือไม่เพียงพอ (คงเหลือ: ${currentBal.setScale(2, ROUNDING)}, ต้องการแลก: ${reqPts.setScale(2, ROUNDING)})")
        }

        // Conversion Rate: 100 Points = 10 THB (1 Point = 0.10 THB)
        val discountAmount = reqPts.multiply(BigDecimal("0.1000")).setScale(SCALE, ROUNDING)
        val newBal = currentBal.subtract(reqPts).setScale(SCALE, ROUNDING)

        val ledger = PointLedger(
            customerId = customerId,
            transactionType = PointTransactionType.REDEEM,
            points = reqPts.negate(),
            balanceAfter = newBal,
            referenceType = "ORDER",
            referenceId = orderId,
            notes = notes ?: "แลก ${reqPts.setScale(0, ROUNDING)} แต้ม เป็นส่วนลด ฿${discountAmount.toFixed()} (ออเดอร์ $orderId)",
            createdBy = operatorId
        )
        val saved = pointLedgerRepository.save(ledger)

        return RedeemPointsResponseDto(
            customerId = customerId,
            orderId = orderId,
            redeemedPoints = reqPts,
            discountAmount = discountAmount,
            balanceAfter = newBal,
            ledgerId = saved.id
        )
    }

    @Transactional
    fun redeemPoints(dto: PointTransactionDto): PointLedger {
        val orderId = dto.referenceId ?: "ORDER-MANUAL"
        val res = redeemPoints(
            customerId = dto.customerId,
            pointsToRedeem = dto.points,
            orderId = orderId,
            notes = dto.notes,
            operatorId = dto.operatorId
        )
        return pointLedgerRepository.findById(res.ledgerId).orElseThrow()
    }

    @Transactional
    fun adjustPoints(
        customerId: String,
        points: BigDecimal,
        reason: String,
        operatorId: String? = null,
        referenceId: String? = null
    ): PointLedger {
        if (reason.isBlank()) {
            throw IllegalArgumentException("ต้องระบุเหตุผลในการปรับปรุงแต้ม (reason is required for manager adjustment)")
        }

        val currentBal = calculatePointsBalance(customerId)
        val adjPts = points.setScale(SCALE, ROUNDING)
        val newBal = currentBal.add(adjPts).setScale(SCALE, ROUNDING)
        if (newBal < BigDecimal.ZERO) {
            throw IllegalArgumentException("ยอดแต้มคงเหลือหลังปรับปรุงต้องไม่ติดลบ (คงเหลือ: $currentBal, ปรับปรุง: $adjPts)")
        }

        val ledger = PointLedger(
            customerId = customerId,
            transactionType = PointTransactionType.ADJUST,
            points = adjPts,
            balanceAfter = newBal,
            referenceType = "MANUAL_ADJUST",
            referenceId = referenceId,
            notes = reason.trim(),
            createdBy = operatorId
        )
        return pointLedgerRepository.save(ledger)
    }

    @Transactional
    fun adjustPoints(dto: PointTransactionDto): PointLedger {
        val reason = dto.notes ?: "ปรับปรุงแต้มโดยผู้จัดการ"
        return adjustPoints(
            customerId = dto.customerId,
            points = dto.points,
            reason = reason,
            operatorId = dto.operatorId,
            referenceId = dto.referenceId
        )
    }

    @Transactional
    fun reversePoints(orderId: String, customerId: String): List<PointLedger> {
        val ledgers = pointLedgerRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
        val reversedList = mutableListOf<PointLedger>()

        // 1. If points were EARNED on this order, reverse the earn (deduct earned points)
        val targetOrderEarn = ledgers.firstOrNull { it.referenceId == orderId && it.transactionType == PointTransactionType.EARN }
        val existingEarnReversal = ledgers.firstOrNull { it.referenceId == orderId && it.transactionType == PointTransactionType.REVERSE && it.notes?.contains("EARN") == true }

        if (targetOrderEarn != null && existingEarnReversal == null) {
            val currentBal = calculatePointsBalance(customerId)
            val revPts = targetOrderEarn.points.negate()
            val newBal = currentBal.add(revPts).coerceAtLeast(BigDecimal.ZERO).setScale(SCALE, ROUNDING)

            val reversalEarn = PointLedger(
                customerId = customerId,
                transactionType = PointTransactionType.REVERSE,
                points = revPts,
                balanceAfter = newBal,
                referenceType = "ORDER_REVERSAL",
                referenceId = orderId,
                notes = "ยกเลิกแต้มสะสม EARN จากออเดอร์ $orderId (${targetOrderEarn.points} แต้ม) เนื่องจากบิลถูกยกเลิก/void"
            )
            reversedList.add(pointLedgerRepository.save(reversalEarn))
        }

        // 2. If points were REDEEMED on this order, refund the redeemed points back (restore points)
        val targetOrderRedeem = ledgers.firstOrNull { it.referenceId == orderId && it.transactionType == PointTransactionType.REDEEM }
        val existingRedeemRefund = ledgers.firstOrNull { it.referenceId == orderId && it.transactionType == PointTransactionType.REVERSE && it.notes?.contains("REDEEM") == true }

        if (targetOrderRedeem != null && existingRedeemRefund == null) {
            val currentBal = calculatePointsBalance(customerId)
            val refundPts = targetOrderRedeem.points.abs()
            val newBal = currentBal.add(refundPts).setScale(SCALE, ROUNDING)

            val refundRedeem = PointLedger(
                customerId = customerId,
                transactionType = PointTransactionType.REVERSE,
                points = refundPts,
                balanceAfter = newBal,
                referenceType = "ORDER_REFUND",
                referenceId = orderId,
                notes = "คืนแต้มที่ใช้ REDEEM ในออเดอร์ $orderId ($refundPts แต้ม) เนื่องจากบิลถูกยกเลิก/void"
            )
            reversedList.add(pointLedgerRepository.save(refundRedeem))
        }

        return reversedList
    }

    // ── 8. Marketing Coupons & Segments ──

    fun validateCoupon(dto: ValidateCouponRequestDto): CouponValidationResponseDto {
        val result = couponService.validateCoupon(
            com.sunpos.backend.domain.promotion.ValidateCouponRequestDto(
                code = dto.code,
                orderAmount = dto.orderAmount,
                branchId = dto.branchId
            )
        )
        return CouponValidationResponseDto(
            isValid = result.isValid,
            couponCode = result.couponCode,
            promotionName = result.couponName,
            calculatedDiscountAmount = result.calculatedDiscountAmount,
            message = result.message
        )
    }

    fun listCustomers(companyId: String = "comp-001"): List<Customer> = customerRepository.findByCompanyId(companyId)

    fun listPointLedger(customerId: String): List<PointLedger> = pointLedgerRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)

    fun listCustomerOrders(customerId: String, from: String? = null, to: String? = null, branchId: String? = null): List<Order> {
        var orders = orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId)
        if (!branchId.isNullOrBlank()) {
            orders = orders.filter { it.branchId == branchId }
        }
        if (!from.isNullOrBlank()) {
            val fromInstant = try { Instant.parse(from) } catch (_: Exception) {
                try { java.time.LocalDate.parse(from).atStartOfDay(ZoneId.systemDefault()).toInstant() } catch (_: Exception) { null }
            }
            if (fromInstant != null) {
                orders = orders.filter { !it.createdAt.isBefore(fromInstant) }
            }
        }
        if (!to.isNullOrBlank()) {
            val toInstant = try { Instant.parse(to) } catch (_: Exception) {
                try { java.time.LocalDate.parse(to).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant() } catch (_: Exception) { null }
            }
            if (toInstant != null) {
                orders = orders.filter { !it.createdAt.isAfter(toInstant) }
            }
        }
        return orders
    }

    fun listSegments(): List<CustomerSegment> = segmentRepository.findAll()

    private fun MembershipTier.toDto() = MembershipTierResponseDto(
        id = this.id,
        companyId = this.companyId,
        code = this.code,
        name = this.name,
        rankLevel = this.rankLevel,
        minimumSpent = this.minimumSpent,
        pointMultiplier = this.pointMultiplier,
        discountPercentage = this.discountPercentage,
        isActive = this.isActive,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt
    )

    private fun BigDecimal.toFixed(): String = this.setScale(2, ROUNDING).toString()
}

// ── 1. Unified Customer API Controller (/api/v1/customers) ──

@RestController
@RequestMapping("/api/v1/customers")
class CustomerController(
    private val crmService: CrmService
) {
    @PostMapping
    @PreAuthorize("hasAuthority('crm.customer.write') or hasAuthority('CUSTOMER_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_STORE_MANAGER') or hasAuthority('ROLE_CASHIER')")
    fun createCustomer(
        @RequestBody dto: CreateCustomerDto,
        principal: Principal?
    ): ApiResponse<CustomerDetailsDto> {
        val companyId = crmService.resolveCompanyId(principal?.name)
        val result = crmService.createCustomer(dto, companyId)
        return ApiResponse.success(result, "Customer created or retrieved successfully")
    }

    @GetMapping("/{id}")
    fun getCustomer(
        @PathVariable id: String,
        principal: Principal?
    ): ApiResponse<CustomerDetailsDto> {
        val companyId = crmService.resolveCompanyId(principal?.name)
        val result = crmService.getCustomerDetails(id, companyId)
        return ApiResponse.success(result)
    }

    @GetMapping("/search")
    fun searchCustomer(
        @RequestParam(required = false) phone: String?,
        @RequestParam(required = false) q: String?,
        principal: Principal?
    ): ApiResponse<List<CustomerDetailsDto>> {
        val companyId = crmService.resolveCompanyId(principal?.name)
        val results = crmService.searchCustomers(phone = phone, query = q, companyId = companyId)
        return ApiResponse.success(results)
    }

    @GetMapping("/{id}/orders")
    fun getCustomerOrders(
        @PathVariable id: String,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) branchId: String?
    ): ApiResponse<List<Order>> {
        return ApiResponse.success(crmService.listCustomerOrders(id, from, to, branchId))
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('crm.customer.write') or hasAuthority('CUSTOMER_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_STORE_MANAGER') or hasAuthority('ROLE_CASHIER')")
    fun updateCustomer(
        @PathVariable id: String,
        @RequestBody dto: UpdateCustomerDto,
        principal: Principal?
    ): ApiResponse<CustomerDetailsDto> {
        val companyId = crmService.resolveCompanyId(principal?.name)
        val result = crmService.updateCustomer(id, dto, companyId)
        return ApiResponse.success(result, "Customer updated successfully")
    }

    @PostMapping("/{id}/identities")
    @PreAuthorize("hasAuthority('crm.customer.write') or hasAuthority('CUSTOMER_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_STORE_MANAGER') or hasAuthority('ROLE_CASHIER')")
    fun addIdentity(
        @PathVariable id: String,
        @RequestBody dto: AddIdentityDto,
        principal: Principal?
    ): ApiResponse<CustomerDetailsDto> {
        val companyId = crmService.resolveCompanyId(principal?.name)
        val result = crmService.addCustomerIdentity(id, dto, companyId)
        return ApiResponse.success(result, "Identity added successfully")
    }

    @GetMapping("/{id}/membership")
    fun getCustomerMembership(
        @PathVariable id: String,
        principal: Principal?
    ): ApiResponse<CustomerMembershipResponseDto> {
        val companyId = crmService.resolveCompanyId(principal?.name)
        val result = crmService.getCustomerMembership(id, companyId)
        return ApiResponse.success(result)
    }

    @PostMapping("/{id}/membership/evaluate")
    @PreAuthorize("hasAuthority('crm.customer.write') or hasAuthority('CUSTOMER_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_CASHIER') or hasAuthority('ROLE_STORE_MANAGER')")
    fun evaluateMembership(
        @PathVariable id: String,
        @RequestBody(required = false) req: EvaluateMembershipRequestDto?
    ): ApiResponse<EvaluateMembershipResponseDto> {
        val addedSpend = req?.addedSpend ?: BigDecimal.ZERO
        val result = crmService.evaluateAndUpgradeMembership(id, addedSpend)
        return ApiResponse.success(result, result.message)
    }

    @GetMapping("/{id}/points/balance")
    fun getPointsBalance(@PathVariable id: String): ApiResponse<PointBalanceResponseDto> {
        return ApiResponse.success(crmService.getPointBalanceDetails(id))
    }

    @GetMapping("/{id}/points/ledgers")
    fun getPointLedgers(@PathVariable id: String): ApiResponse<List<PointLedger>> {
        return ApiResponse.success(crmService.listPointLedger(id))
    }

    @GetMapping("/{id}/points")
    fun getPoints(@PathVariable id: String): ApiResponse<List<PointLedger>> {
        return ApiResponse.success(crmService.listPointLedger(id))
    }

    @PostMapping("/{id}/points/redeem")
    @PreAuthorize("hasAuthority('crm.points.redeem') or hasAuthority('POINT_REDEEM') or hasAuthority('CUSTOMER_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_STORE_MANAGER') or hasAuthority('ROLE_CASHIER')")
    fun redeemCustomerPoints(
        @PathVariable id: String,
        @RequestBody req: RedeemPointsRequestDto,
        principal: Principal?
    ): ApiResponse<RedeemPointsResponseDto> {
        val operator = req.operatorId ?: principal?.name
        val result = crmService.redeemPoints(
            customerId = id,
            pointsToRedeem = req.points,
            orderId = req.orderId,
            notes = req.notes,
            operatorId = operator
        )
        return ApiResponse.success(result, "Points redeemed successfully")
    }

    @PostMapping("/{id}/points/adjust")
    @PreAuthorize("hasAuthority('crm.points.adjust') or hasAuthority('POINT_ADJUST') or hasAuthority('ROLE_STORE_MANAGER') or hasAuthority('ROLE_BRANCH_MANAGER') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun adjustCustomerPoints(
        @PathVariable id: String,
        @RequestBody req: AdjustPointsRequestDto,
        principal: Principal?
    ): ApiResponse<PointLedger> {
        val operator = req.operatorId ?: principal?.name
        val result = crmService.adjustPoints(
            customerId = id,
            points = req.points,
            reason = req.reason,
            operatorId = operator,
            referenceId = req.referenceId
        )
        return ApiResponse.success(result, "Points adjusted successfully")
    }
}

// ── 2. Membership Tier Management Controller (/api/v1/membership-tiers) ──

@RestController
@RequestMapping("/api/v1/membership-tiers")
class MembershipTierController(
    private val crmService: CrmService
) {
    @GetMapping
    fun listTiers(principal: Principal?): ApiResponse<List<MembershipTierResponseDto>> {
        val companyId = crmService.resolveCompanyId(principal?.name)
        return ApiResponse.success(crmService.listTiers(companyId))
    }

    @PostMapping
    @PreAuthorize("hasAuthority('crm.customer.write') or hasAuthority('CUSTOMER_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_STORE_MANAGER') or hasAuthority('ROLE_BRANCH_MANAGER')")
    fun createTier(
        @RequestBody dto: CreateMembershipTierDto,
        principal: Principal?
    ): ApiResponse<MembershipTierResponseDto> {
        val companyId = crmService.resolveCompanyId(principal?.name)
        val result = crmService.createTier(dto, companyId)
        return ApiResponse.success(result, "Membership Tier created successfully")
    }

    @GetMapping("/{id}")
    fun getTier(
        @PathVariable id: String,
        principal: Principal?
    ): ApiResponse<MembershipTierResponseDto> {
        val companyId = crmService.resolveCompanyId(principal?.name)
        return ApiResponse.success(crmService.getTier(id, companyId))
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('crm.customer.write') or hasAuthority('CUSTOMER_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_STORE_MANAGER') or hasAuthority('ROLE_BRANCH_MANAGER')")
    fun updateTier(
        @PathVariable id: String,
        @RequestBody dto: UpdateMembershipTierDto,
        principal: Principal?
    ): ApiResponse<MembershipTierResponseDto> {
        val companyId = crmService.resolveCompanyId(principal?.name)
        val result = crmService.updateTier(id, dto, companyId)
        return ApiResponse.success(result, "Membership Tier updated successfully")
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('crm.customer.write') or hasAuthority('CUSTOMER_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun deleteTier(
        @PathVariable id: String,
        principal: Principal?
    ): ApiResponse<Boolean> {
        val companyId = crmService.resolveCompanyId(principal?.name)
        val result = crmService.deleteTier(id, companyId)
        return ApiResponse.success(result, "Membership Tier deactivated successfully")
    }
}

// ── 3. Backwards-Compatible CRM Controller (/api/v1/crm) ──

@RestController
@RequestMapping("/api/v1/crm")
class CrmController(
    private val crmService: CrmService
) {
    @GetMapping("/customers")
    fun listCustomers(principal: Principal?): ApiResponse<List<Customer>> {
        val companyId = crmService.resolveCompanyId(principal?.name)
        return ApiResponse.success(crmService.listCustomers(companyId))
    }

    @PostMapping("/customers")
    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_CASHIER')")
    fun createCustomer(@RequestBody dto: CreateCustomerDto, principal: Principal?): ApiResponse<CustomerDetailsDto> {
        val companyId = crmService.resolveCompanyId(principal?.name)
        return ApiResponse.success(crmService.createCustomer(dto, companyId), "Customer created successfully")
    }

    @GetMapping("/customers/{id}")
    fun getCustomer(@PathVariable id: String, principal: Principal?): ApiResponse<CustomerDetailsDto> {
        val companyId = crmService.resolveCompanyId(principal?.name)
        return ApiResponse.success(crmService.getCustomerDetails(id, companyId))
    }

    @GetMapping("/customers/search")
    fun searchCustomer(@RequestParam value: String, principal: Principal?): ApiResponse<CustomerDetailsDto?> {
        val companyId = crmService.resolveCompanyId(principal?.name)
        return ApiResponse.success(crmService.searchCustomerByIdentity(value, companyId).orElse(null))
    }

    @GetMapping("/customers/{id}/orders")
    fun getCustomerOrders(
        @PathVariable id: String,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) branchId: String?
    ): ApiResponse<List<Order>> {
        return ApiResponse.success(crmService.listCustomerOrders(id, from, to, branchId))
    }

    @GetMapping("/tiers")
    fun listTiers(principal: Principal?): ApiResponse<List<MembershipTierResponseDto>> {
        val companyId = crmService.resolveCompanyId(principal?.name)
        return ApiResponse.success(crmService.listTiers(companyId))
    }

    @GetMapping("/points/{customerId}")
    fun getPointLedger(@PathVariable customerId: String): ApiResponse<List<PointLedger>> {
        return ApiResponse.success(crmService.listPointLedger(customerId))
    }

    @PostMapping("/points/redeem")
    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_CASHIER')")
    fun redeemPoints(@RequestBody dto: PointTransactionDto): ApiResponse<PointLedger> {
        return ApiResponse.success(crmService.redeemPoints(dto), "Points redeemed successfully")
    }

    @PostMapping("/points/adjust")
    @PreAuthorize("hasAuthority('CUSTOMER_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun adjustPoints(@RequestBody dto: PointTransactionDto): ApiResponse<PointLedger> {
        return ApiResponse.success(crmService.adjustPoints(dto), "Points adjusted successfully")
    }

    @PostMapping("/coupons/validate")
    fun validateCoupon(@RequestBody dto: ValidateCouponRequestDto): ApiResponse<CouponValidationResponseDto> {
        return ApiResponse.success(crmService.validateCoupon(dto))
    }

    @GetMapping("/segments")
    fun listSegments(): ApiResponse<List<CustomerSegment>> = ApiResponse.success(crmService.listSegments())
}
