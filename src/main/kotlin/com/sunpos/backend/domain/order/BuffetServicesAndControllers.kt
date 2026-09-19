package com.sunpos.backend.domain.order

import com.sunpos.backend.common.ApiResponse
import com.sunpos.backend.common.JdbcRepository
import org.springframework.jdbc.core.JdbcTemplate
import com.sunpos.backend.domain.catalog.MenuItem
import com.sunpos.backend.domain.catalog.MenuItemRepository
import com.sunpos.backend.domain.catalog.MenuCategoryRepository
import com.sunpos.backend.domain.organization.BranchRepository
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
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

    fun findByBrandId(brandId: String): List<BuffetPromotion> =
        findByField("brandId", brandId)

    fun findByBranchId(branchId: String): List<BuffetPromotion> =
        findByField("branchId", branchId)

    fun findPromotionsForBranch(brandId: String, branchId: String, status: BuffetPromotionStatus? = null): List<BuffetPromotion> {
        val all = if (status != null) findByField("status", status.name) else findAll()
        return all.filter { it.branchId == branchId || (it.branchId.isNullOrBlank() && it.brandId == brandId) }
    }
}

@Repository
class BuffetPromotionMenuItemRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<BuffetPromotionMenuItem>(jdbcTemplate, "buffet_promotion_menu_items", BuffetPromotionMenuItem::class.java) {
    fun findByPromotionId(promotionId: String): List<BuffetPromotionMenuItem> =
        findByField("promotionId", promotionId)

    fun findMenuItemIdsByPromotionId(promotionId: String): List<String> =
        findByField("promotionId", promotionId).map { it.menuItemId }

    fun deleteByIdPromotionId(promotionId: String) {
        val list = findByField("promotionId", promotionId)
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
    private val sessionRepository: BuffetSessionRepository,
    private val branchRepository: BranchRepository,
    private val menuItemRepository: MenuItemRepository,
    private val categoryRepository: MenuCategoryRepository? = null
) {

    // ── Multi-Brand Buffet Promotion APIs ──

    /**
     * Get active buffet promotions available to a brand or branch (inherits from brand).
     */
    fun listPromotions(brandId: String? = null, branchId: String? = null, status: BuffetPromotionStatus? = null): List<BuffetPromotionResponseDto> {
        val promos = if (!branchId.isNullOrBlank()) {
            val branch = branchRepository.findById(branchId).orElse(null)
            val bBrandId = branch?.brandId ?: brandId ?: ""
            if (bBrandId.isNotBlank()) {
                promotionRepository.findPromotionsForBranch(bBrandId, branchId, status)
            } else {
                if (status != null) promotionRepository.findByBranchIdAndStatus(branchId, status)
                else promotionRepository.findByBranchId(branchId)
            }
        } else if (!brandId.isNullOrBlank()) {
            val brandBranches = branchRepository.findByBrandId(brandId).map { it.id }.toSet()
            val all = if (status != null) {
                val byStatus = promotionRepository.findByField("status", status.name)
                if (byStatus.isNotEmpty()) byStatus else promotionRepository.findAll().filter { it.status == status }
            } else {
                promotionRepository.findAll()
            }
            all.filter {
                it.brandId == brandId || (it.branchId != null && brandBranches.contains(it.branchId))
            }
        } else {
            if (status != null) promotionRepository.findAll().filter { it.status == status }
            else promotionRepository.findAll()
        }

        return promos.map { p ->
            val count = promotionMenuItemRepository.findMenuItemIdsByPromotionId(p.id).size
            toPromotionResponseDto(p, count)
        }
    }

    fun listPromotionsByBranch(branchId: String? = null): List<BuffetPromotionResponseDto> =
        listPromotions(null, branchId)

    /**
     * Get full MenuItem objects allowed under a specific buffet promotion with category and pricing details.
     */
    fun getMenuItemsForPromotion(promotionId: String): List<BuffetPromotionMenuItemDetailDto> {
        val links = promotionMenuItemRepository.findByPromotionId(promotionId)
        if (links.isEmpty()) return emptyList()

        val itemIds = links.map { it.menuItemId }
        val items = menuItemRepository.findAllById(itemIds).filter { it.isActive }.associateBy { it.id }
        val categoryMap = categoryRepository?.findAll()?.associateBy { it.id } ?: emptyMap()

        return links.mapNotNull { link ->
            val item = items[link.menuItemId] ?: return@mapNotNull null
            val category = categoryMap[item.categoryId]
            BuffetPromotionMenuItemDetailDto(
                id = item.id,
                promotionId = promotionId,
                menuItemId = item.id,
                name = item.name,
                categoryId = item.categoryId,
                categoryName = category?.name ?: "ทั่วไป (General)",
                basePrice = item.basePrice,
                imageUrl = item.imageUrl,
                isFree = link.isFree,
                additionalPrice = link.additionalPrice
            )
        }
    }

    @Transactional
    fun updatePromotionMenuItems(
        promotionId: String,
        dto: UpdateBuffetPromotionMenuItemsDto
    ): List<BuffetPromotionMenuItemDetailDto> {
        promotionRepository.findById(promotionId)
            .orElseThrow { IllegalArgumentException("Buffet promotion not found: $promotionId") }

        promotionMenuItemRepository.deleteByIdPromotionId(promotionId)
        for (item in dto.items) {
            val link = BuffetPromotionMenuItem(
                promotionId = promotionId,
                menuItemId = item.menuItemId,
                isFree = item.isFree,
                additionalPrice = item.additionalPrice
            )
            promotionMenuItemRepository.save(link)
        }
        return getMenuItemsForPromotion(promotionId)
    }

    /**
     * Backoffice: Create a multi-brand buffet promotion cleanly without legacy duplicate tables.
     */
    @Transactional
    fun createPromotion(dto: CreateBuffetPromotionDto, createdBy: String? = null): BuffetPromotionResponseDto {
        var resolvedBrandId = dto.brandId
        if (resolvedBrandId.isBlank() && !dto.branchId.isNullOrBlank()) {
            val branch = branchRepository.findById(dto.branchId).orElse(null)
            if (branch != null && !branch.brandId.isNullOrBlank()) {
                resolvedBrandId = branch.brandId ?: ""
            }
        }

        val promo = BuffetPromotion(
            brandId = resolvedBrandId,
            branchId = dto.branchId,
            name = dto.name,
            pricePerPerson = dto.pricePerPerson,
            durationMinutes = dto.durationMinutes,
            status = BuffetPromotionStatus.ACTIVE,
            createdBy = createdBy
        )
        promotionRepository.save(promo)

        val itemsToSave = if (dto.items.isNotEmpty()) {
            dto.items
        } else {
            dto.menuItemIds.map { BuffetPromotionItemLinkDto(menuItemId = it) }
        }

        for (item in itemsToSave) {
            val link = BuffetPromotionMenuItem(
                promotionId = promo.id,
                menuItemId = item.menuItemId,
                isFree = item.isFree,
                additionalPrice = item.additionalPrice
            )
            promotionMenuItemRepository.save(link)
        }

        return toPromotionResponseDto(promo, itemsToSave.size)
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

        val itemsToSave = if (dto.items != null) {
            dto.items
        } else if (dto.menuItemIds != null) {
            dto.menuItemIds.map { BuffetPromotionItemLinkDto(menuItemId = it) }
        } else null

        if (itemsToSave != null) {
            promotionMenuItemRepository.deleteByIdPromotionId(promo.id)
            for (item in itemsToSave) {
                val link = BuffetPromotionMenuItem(
                    promotionId = promo.id,
                    menuItemId = item.menuItemId,
                    isFree = item.isFree,
                    additionalPrice = item.additionalPrice
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

    fun getSessionByOrder(orderId: String): BuffetSessionResponseDto? {
        val session = sessionRepository.findByOrderId(orderId) ?: return null
        val promoName = promotionRepository.findById(session.buffetTierId).map { it.name }.orElse("Buffet")
        return toSessionResponseDto(session, promoName)
    }

    @Transactional
    fun closeSession(sessionId: String): BuffetSessionResponseDto? {
        val session = sessionRepository.findById(sessionId).orElse(null) ?: return null
        session.status = BuffetSessionStatus.CLOSED
        session.closedAt = Instant.now()
        session.updatedAt = Instant.now()
        sessionRepository.save(session)

        val promoName = promotionRepository.findById(session.buffetTierId).map { it.name }.orElse("Buffet")
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
        @RequestParam(required = false) brandId: String? = null,
        @RequestParam(required = false) status: BuffetPromotionStatus? = null
    ): ApiResponse<List<BuffetPromotionResponseDto>> {
        return ApiResponse.success(buffetService.listPromotions(brandId, branchId, status))
    }

    @GetMapping("/promotions/{promotionId}/menu-items")
    fun getPromotionMenuItems(@PathVariable promotionId: String): ApiResponse<List<BuffetPromotionMenuItemDetailDto>> {
        return ApiResponse.success(buffetService.getMenuItemsForPromotion(promotionId))
    }

    @PutMapping("/promotions/{promotionId}/menu-items")
    fun updatePromotionMenuItems(
        @PathVariable promotionId: String,
        @RequestBody dto: UpdateBuffetPromotionMenuItemsDto
    ): ApiResponse<List<BuffetPromotionMenuItemDetailDto>> {
        return ApiResponse.success(
            buffetService.updatePromotionMenuItems(promotionId, dto),
            "Buffet promotion menu items updated successfully"
        )
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

    @PostMapping("/sessions")
    fun startSession(@RequestBody dto: StartBuffetPromotionSessionDto): ApiResponse<BuffetSessionResponseDto> {
        return ApiResponse.success(buffetService.startPromotionSession(dto), "Buffet session started")
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
