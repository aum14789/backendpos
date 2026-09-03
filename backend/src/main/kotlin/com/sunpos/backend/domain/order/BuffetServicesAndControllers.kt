package com.sunpos.backend.domain.order

import com.sunpos.backend.common.ApiResponse
import com.sunpos.backend.common.JdbcRepository
import org.springframework.jdbc.core.JdbcTemplate
import com.sunpos.backend.domain.catalog.MenuItem
import com.sunpos.backend.domain.catalog.MenuItemRepository
import com.sunpos.backend.domain.organization.BranchRepository
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.time.Instant

// ──────────────────────────────────────────────────────
// Repositories
// ──────────────────────────────────────────────────────

@Repository
class BuffetPromotionRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<BuffetPromotion>(jdbcTemplate, "buffet_promotions", BuffetPromotion::class.java) {
    fun findByBrandIdAndStatus(brandId: String, status: BuffetPromotionStatus): List<BuffetPromotion> =
        findByFields(mapOf("brandId" to brandId, "status" to status.name))
    fun findByBranchIdAndStatus(branchId: String, status: BuffetPromotionStatus): List<BuffetPromotion> =
        findByFields(mapOf("branchId" to branchId, "status" to status.name))

    fun findActivePromotionsForBranch(brandId: String, branchId: String): List<BuffetPromotion> {
        val allActive = findByField("status", BuffetPromotionStatus.ACTIVE.name)
        return allActive.filter { it.branchId == branchId || (it.branchId.isNullOrBlank() && it.brandId == brandId) }
    }
}

@Repository
class BuffetPromotionMenuItemRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<BuffetPromotionMenuItem>(jdbcTemplate, "buffet_promotion_menu_items", BuffetPromotionMenuItem::class.java) {
    fun findMenuItemIdsByPromotionId(promotionId: String): List<String> =
        findByField("promotionId", promotionId).map { it.menuItemId }

    fun deleteByIdPromotionId(promotionId: String) {
        val list = findByField("promotionId", promotionId)
        list.forEach { deleteById(it.id) }
    }
}

@Repository
class BuffetPromotionTierRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<BuffetPromotionTier>(jdbcTemplate, "buffet_promotion_tiers", BuffetPromotionTier::class.java) {
    fun findByBranchIdAndIsActiveTrue(branchId: String): List<BuffetPromotionTier> =
        findByFields(mapOf("branchId" to branchId, "isActive" to true))
    fun findByBrandIdAndIsActiveTrue(brandId: String): List<BuffetPromotionTier> =
        findByFields(mapOf("brandId" to brandId, "isActive" to true))
    fun findByPromotionIdAndIsActiveTrue(promotionId: String): List<BuffetPromotionTier> =
        findByFields(mapOf("promotionId" to promotionId, "isActive" to true))
}

@Repository
class BuffetTierMenuItemRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<BuffetTierMenuItem>(jdbcTemplate, "buffet_tier_menu_items", BuffetTierMenuItem::class.java) {
    fun findMenuItemIdsByTierId(tierId: String): List<String> =
        findByField("buffetTierId", tierId).map { it.menuItemId }

    fun deleteByIdBuffetTierId(tierId: String) {
        val list = findByField("buffetTierId", tierId)
        list.forEach { deleteById(it.id) }
    }
}

@Repository
class BuffetSessionRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<BuffetSession>(jdbcTemplate, "buffet_sessions", BuffetSession::class.java) {
    fun findByOrderId(orderId: String): BuffetSession? =
        findOneByField("orderId", orderId).orElse(null)
    fun findByBranchIdAndStatus(branchId: String, status: BuffetSessionStatus): List<BuffetSession> =
        findByFields(mapOf("branchId" to branchId, "status" to status.name))
}

// ──────────────────────────────────────────────────────
// Service
// ──────────────────────────────────────────────────────

@Service
class BuffetService(
    private val promotionRepository: BuffetPromotionRepository,
    private val promotionMenuItemRepository: BuffetPromotionMenuItemRepository,
    private val tierRepository: BuffetPromotionTierRepository,
    private val tierMenuItemRepository: BuffetTierMenuItemRepository,
    private val sessionRepository: BuffetSessionRepository,
    private val branchRepository: BranchRepository,
    private val menuItemRepository: MenuItemRepository
) {

    // ── Multi-Brand Buffet Promotion APIs ──

    /**
     * Get active buffet promotions available to a brand or branch (inherits from brand).
     */
    fun listPromotions(brandId: String? = null, branchId: String? = null): List<BuffetPromotionResponseDto> {
        val promos = if (!branchId.isNullOrBlank()) {
            val branch = branchRepository.findById(branchId).orElse(null)
            val bBrandId = branch?.brandId ?: brandId ?: ""
            if (bBrandId.isNotBlank()) {
                promotionRepository.findActivePromotionsForBranch(bBrandId, branchId)
            } else {
                promotionRepository.findByBranchIdAndStatus(branchId, BuffetPromotionStatus.ACTIVE)
            }
        } else if (!brandId.isNullOrBlank()) {
            promotionRepository.findByBrandIdAndStatus(brandId, BuffetPromotionStatus.ACTIVE)
        } else {
            promotionRepository.findAll().filter { it.status == BuffetPromotionStatus.ACTIVE }
        }

        return promos.map { p ->
            val count = promotionMenuItemRepository.findMenuItemIdsByPromotionId(p.id).size
            toPromotionResponseDto(p, count)
        }
    }

    fun listPromotionsByBranch(branchId: String? = null): List<BuffetPromotionResponseDto> =
        listPromotions(null, branchId)

    /**
     * Get full MenuItem objects allowed under a specific buffet promotion.
     */
    fun getMenuItemsForPromotion(promotionId: String): List<MenuItem> {
        val itemIds = promotionMenuItemRepository.findMenuItemIdsByPromotionId(promotionId)
        if (itemIds.isEmpty()) return emptyList()
        return menuItemRepository.findAllById(itemIds).filter { it.isActive }
    }

    /**
     * Backoffice: Create a multi-brand buffet promotion.
     */
    @Transactional
    fun createPromotion(dto: CreateBuffetPromotionDto, createdBy: String? = null): BuffetPromotionResponseDto {
        val promo = BuffetPromotion(
            brandId = dto.brandId,
            branchId = dto.branchId,
            name = dto.name,
            pricePerPerson = dto.pricePerPerson,
            durationMinutes = dto.durationMinutes,
            status = BuffetPromotionStatus.ACTIVE,
            createdBy = createdBy
        )
        promotionRepository.save(promo)

        for (itemId in dto.menuItemIds) {
            val link = BuffetPromotionMenuItem(
                promotionId = promo.id,
                menuItemId = itemId
            )
            promotionMenuItemRepository.save(link)
        }

        return toPromotionResponseDto(promo, dto.menuItemIds.size)
    }

    /**
     * Backoffice: Update buffet promotion.
     */
    @Transactional
    fun updatePromotion(id: String, dto: UpdateBuffetPromotionDto, updatedBy: String? = null): BuffetPromotionResponseDto {
        val promo = promotionRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Buffet promotion not found: $id") }

        promo.name = dto.name
        promo.pricePerPerson = dto.pricePerPerson
        promo.durationMinutes = dto.durationMinutes
        promo.status = dto.status
        promo.updatedAt = Instant.now()
        promo.updatedBy = updatedBy
        promotionRepository.save(promo)

        if (dto.menuItemIds != null) {
            promotionMenuItemRepository.deleteByIdPromotionId(promo.id)
            for (itemId in dto.menuItemIds) {
                val link = BuffetPromotionMenuItem(
                    promotionId = promo.id,
                    menuItemId = itemId
                )
                promotionMenuItemRepository.save(link)
            }
        }

        val count = promotionMenuItemRepository.findMenuItemIdsByPromotionId(promo.id).size
        return toPromotionResponseDto(promo, count)
    }

    /**
     * Backoffice: Soft-delete promotion.
     */
    @Transactional
    fun deletePromotion(id: String) {
        val promo = promotionRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Buffet promotion not found: $id") }
        promo.status = BuffetPromotionStatus.INACTIVE
        promo.updatedAt = Instant.now()
        promotionRepository.save(promo)
    }

    /**
     * Start a buffet session via BuffetPromotion.
     */
    @Transactional
    fun startPromotionSession(dto: StartBuffetPromotionSessionDto): BuffetSessionResponseDto {
        val promo = promotionRepository.findById(dto.promotionId)
            .orElseThrow { IllegalArgumentException("Buffet promotion not found: ${dto.promotionId}") }

        val now = Instant.now()
        val expiresAt = now.plusSeconds(promo.durationMinutes.toLong() * 60)

        val session = BuffetSession(
            orderId = dto.orderId,
            branchId = dto.branchId,
            buffetTierId = promo.id,
            adultCount = dto.headcount,
            childCount = 0,
            adultPriceSnapshot = promo.pricePerPerson,
            childPriceSnapshot = promo.pricePerPerson,
            timeLimitMinutes = promo.durationMinutes,
            startedAt = now,
            expiresAt = expiresAt,
            createdBy = dto.createdBy
        )
        sessionRepository.save(session)

        return toSessionResponseDto(session, promo.name)
    }

    // ── Tier APIs (Legacy & Multi-Tier) ──

    @Transactional
    fun createTier(dto: CreateBuffetTierDto): BuffetTierResponseDto {
        val tier = BuffetPromotionTier(
            promotionId = dto.promotionId,
            name = dto.name,
            adultPrice = dto.adultPrice,
            childPrice = dto.childPrice,
            timeLimitMinutes = dto.timeLimitMinutes,
            brandId = dto.brandId,
            branchId = dto.branchId
        )
        tierRepository.save(tier)

        for (menuItemId in dto.eligibleMenuItemIds) {
            val link = BuffetTierMenuItem(
                buffetTierId = tier.id,
                menuItemId = menuItemId
            )
            tierMenuItemRepository.save(link)
        }

        return toTierResponseDto(tier, dto.eligibleMenuItemIds.size)
    }

    fun listTiersByBranch(branchId: String? = null): List<BuffetTierResponseDto> {
        val tiers = if (!branchId.isNullOrBlank()) {
            tierRepository.findByBranchIdAndIsActiveTrue(branchId)
        } else {
            tierRepository.findAll().filter { it.isActive }
        }
        return tiers.map { tier ->
            val menuItemCount = tierMenuItemRepository.findMenuItemIdsByTierId(tier.id).size
            toTierResponseDto(tier, menuItemCount)
        }
    }

    fun getEligibleMenuItemIds(tierId: String): List<String> {
        return tierMenuItemRepository.findMenuItemIdsByTierId(tierId)
    }

    @Transactional
    fun startSession(dto: StartBuffetSessionDto): BuffetSessionResponseDto {
        val tier = tierRepository.findById(dto.buffetTierId)
            .orElseThrow { IllegalArgumentException("Buffet tier not found: ${dto.buffetTierId}") }

        val now = Instant.now()
        val expiresAt = now.plusSeconds(tier.timeLimitMinutes.toLong() * 60)

        val session = BuffetSession(
            orderId = dto.orderId,
            branchId = dto.branchId,
            buffetTierId = dto.buffetTierId,
            adultCount = dto.adultCount,
            childCount = dto.childCount,
            adultPriceSnapshot = tier.adultPrice,
            childPriceSnapshot = tier.childPrice,
            timeLimitMinutes = tier.timeLimitMinutes,
            startedAt = now,
            expiresAt = expiresAt,
            createdBy = dto.createdBy
        )
        sessionRepository.save(session)

        return toSessionResponseDto(session, tier.name)
    }

    fun getSessionByOrder(orderId: String): BuffetSessionResponseDto? {
        val session = sessionRepository.findByOrderId(orderId) ?: return null
        val promoName = promotionRepository.findById(session.buffetTierId).map { it.name }
            .orElseGet { tierRepository.findById(session.buffetTierId).map { it.name }.orElse("Buffet") }
        return toSessionResponseDto(session, promoName)
    }

    @Transactional
    fun closeSession(sessionId: String): BuffetSessionResponseDto? {
        val session = sessionRepository.findById(sessionId).orElse(null) ?: return null
        session.status = BuffetSessionStatus.CLOSED
        session.closedAt = Instant.now()
        session.updatedAt = Instant.now()
        sessionRepository.save(session)

        val promoName = promotionRepository.findById(session.buffetTierId).map { it.name }
            .orElseGet { tierRepository.findById(session.buffetTierId).map { it.name }.orElse("Buffet") }
        return toSessionResponseDto(session, promoName)
    }

    @Transactional
    fun expireOverdueSessions(branchId: String): Int {
        val activeSessions = sessionRepository.findByBranchIdAndStatus(branchId, BuffetSessionStatus.ACTIVE)
        var expiredCount = 0
        for (session in activeSessions) {
            if (session.isExpired()) {
                session.status = BuffetSessionStatus.EXPIRED
                session.updatedAt = Instant.now()
                sessionRepository.save(session)
                expiredCount++
            }
        }
        return expiredCount
    }

    private fun toPromotionResponseDto(promo: BuffetPromotion, itemCount: Int) = BuffetPromotionResponseDto(
        id = promo.id,
        brandId = promo.brandId,
        branchId = promo.branchId,
        name = promo.name,
        pricePerPerson = promo.pricePerPerson,
        durationMinutes = promo.durationMinutes,
        status = promo.status,
        eligibleMenuItemCount = itemCount
    )

    private fun toTierResponseDto(tier: BuffetPromotionTier, menuItemCount: Int) = BuffetTierResponseDto(
        id = tier.id,
        promotionId = tier.promotionId,
        name = tier.name,
        adultPrice = tier.adultPrice,
        childPrice = tier.childPrice,
        timeLimitMinutes = tier.timeLimitMinutes,
        brandId = tier.brandId,
        branchId = tier.branchId,
        isActive = tier.isActive,
        eligibleMenuItemCount = menuItemCount
    )

    private fun toSessionResponseDto(session: BuffetSession, tierName: String) = BuffetSessionResponseDto(
        id = session.id,
        orderId = session.orderId,
        branchId = session.branchId,
        buffetTierId = session.buffetTierId,
        tierName = tierName,
        adultCount = session.adultCount,
        childCount = session.childCount,
        adultPriceSnapshot = session.adultPriceSnapshot,
        childPriceSnapshot = session.childPriceSnapshot,
        totalCharge = session.calculateTotalCharge(),
        timeLimitMinutes = session.timeLimitMinutes,
        startedAt = session.startedAt,
        expiresAt = session.expiresAt,
        remainingMinutes = session.remainingMinutes(),
        status = session.status
    )
}

// ──────────────────────────────────────────────────────
// Controller
// ──────────────────────────────────────────────────────

@RestController
@RequestMapping("/api/v1/buffet")
class BuffetController(
    private val buffetService: BuffetService
) {

    // ── Multi-Brand Buffet Promotion Endpoints ──

    @GetMapping("/promotions")
    fun listPromotions(
        @RequestParam(required = false) branchId: String? = null,
        @RequestParam(required = false) brandId: String? = null
    ): ApiResponse<List<BuffetPromotionResponseDto>> {
        return ApiResponse.success(buffetService.listPromotions(brandId, branchId))
    }

    @GetMapping("/promotions/{promotionId}/menu-items")
    fun getPromotionMenuItems(@PathVariable promotionId: String): ApiResponse<List<MenuItem>> {
        return ApiResponse.success(buffetService.getMenuItemsForPromotion(promotionId))
    }

    @PostMapping("/promotions")
    fun createPromotion(@RequestBody dto: CreateBuffetPromotionDto): ApiResponse<BuffetPromotionResponseDto> {
        return ApiResponse.success(buffetService.createPromotion(dto), "Buffet promotion created")
    }

    @PutMapping("/promotions/{id}")
    fun updatePromotion(
        @PathVariable id: String,
        @RequestBody dto: UpdateBuffetPromotionDto
    ): ApiResponse<BuffetPromotionResponseDto> {
        return ApiResponse.success(buffetService.updatePromotion(id, dto), "Buffet promotion updated")
    }

    @DeleteMapping("/promotions/{id}")
    fun deletePromotion(@PathVariable id: String): ApiResponse<Unit> {
        buffetService.deletePromotion(id)
        return ApiResponse.success(Unit, "Buffet promotion deactivated")
    }

    @PostMapping("/promotions/sessions")
    fun startPromotionSession(@RequestBody dto: StartBuffetPromotionSessionDto): ApiResponse<BuffetSessionResponseDto> {
        return ApiResponse.success(buffetService.startPromotionSession(dto), "Buffet session started")
    }

    // ── Tier Endpoints ──

    @PostMapping("/tiers")
    fun createTier(@RequestBody dto: CreateBuffetTierDto): ApiResponse<BuffetTierResponseDto> {
        return ApiResponse.success(buffetService.createTier(dto), "Buffet tier created")
    }

    @GetMapping("/tiers")
    fun listTiers(@RequestParam(required = false) branchId: String?): ApiResponse<List<BuffetTierResponseDto>> {
        return ApiResponse.success(buffetService.listTiersByBranch(branchId))
    }

    @GetMapping("/tiers/{tierId}/menu-items")
    fun getEligibleMenuItems(@PathVariable tierId: String): ApiResponse<List<String>> {
        return ApiResponse.success(buffetService.getEligibleMenuItemIds(tierId))
    }

    @PostMapping("/sessions")
    fun startSession(@RequestBody dto: StartBuffetSessionDto): ApiResponse<BuffetSessionResponseDto> {
        return ApiResponse.success(buffetService.startSession(dto), "Buffet session started")
    }

    @GetMapping("/sessions/order/{orderId}")
    fun getSessionByOrder(@PathVariable orderId: String): ApiResponse<BuffetSessionResponseDto?> {
        return ApiResponse.success(buffetService.getSessionByOrder(orderId))
    }

    @PostMapping("/sessions/{sessionId}/close")
    fun closeSession(@PathVariable sessionId: String): ApiResponse<BuffetSessionResponseDto?> {
        return ApiResponse.success(buffetService.closeSession(sessionId), "Session closed")
    }

    @PostMapping("/sessions/expire-overdue")
    fun expireOverdue(@RequestParam branchId: String): ApiResponse<Map<String, Int>> {
        val count = buffetService.expireOverdueSessions(branchId)
        return ApiResponse.success(mapOf("expiredCount" to count), "$count sessions expired")
    }
}
