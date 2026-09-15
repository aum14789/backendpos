package com.sunpos.backend.domain.order

import com.sunpos.backend.domain.catalog.MenuItem
import com.sunpos.backend.domain.catalog.MenuItemRepository
import com.sunpos.backend.domain.organization.Branch
import com.sunpos.backend.domain.organization.BranchRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.util.Optional

class BuffetPromotionHarmonizationPureTest {

    class FakeBuffetPromotionRepository : BuffetPromotionRepository(mock(JdbcTemplate::class.java)) {
        val items = mutableMapOf<String, BuffetPromotion>()
        override fun save(entity: BuffetPromotion): BuffetPromotion {
            items[entity.id] = entity
            return entity
        }
        override fun findById(id: Any): Optional<BuffetPromotion> = Optional.ofNullable(items[id.toString()])
        override fun findAll(): List<BuffetPromotion> = items.values.toList()
    }

    class FakeBuffetPromotionMenuItemRepository : BuffetPromotionMenuItemRepository(mock(JdbcTemplate::class.java)) {
        val items = mutableListOf<BuffetPromotionMenuItem>()
        override fun save(entity: BuffetPromotionMenuItem): BuffetPromotionMenuItem {
            items.add(entity)
            return entity
        }
        override fun findByPromotionId(promotionId: String): List<BuffetPromotionMenuItem> =
            items.filter { it.promotionId == promotionId }
        override fun findMenuItemIdsByPromotionId(promotionId: String): List<String> =
            findByPromotionId(promotionId).map { it.menuItemId }
        override fun deleteByIdPromotionId(promotionId: String) {
            items.removeIf { it.promotionId == promotionId }
        }
    }

    class FakeBuffetPromotionTierRepository : BuffetPromotionTierRepository(mock(JdbcTemplate::class.java)) {
        val items = mutableMapOf<String, BuffetPromotionTier>()
        override fun save(entity: BuffetPromotionTier): BuffetPromotionTier {
            items[entity.id] = entity
            return entity
        }
        override fun findById(id: Any): Optional<BuffetPromotionTier> = Optional.ofNullable(items[id.toString()])
        override fun findByBranchIdAndIsActiveTrue(branchId: String): List<BuffetPromotionTier> =
            items.values.filter { it.branchId == branchId && it.isActive }
    }

    class FakeBuffetTierMenuItemRepository : BuffetTierMenuItemRepository(mock(JdbcTemplate::class.java)) {
        val items = mutableListOf<BuffetTierMenuItem>()
        override fun save(entity: BuffetTierMenuItem): BuffetTierMenuItem {
            items.add(entity)
            return entity
        }
        override fun findMenuItemIdsByTierId(tierId: String): List<String> =
            items.filter { it.buffetTierId == tierId }.map { it.menuItemId }
        override fun deleteByIdBuffetTierId(tierId: String) {
            items.removeIf { it.buffetTierId == tierId }
        }
    }

    @Test
    fun `test atomic buffet promotion creation with surcharges and tier harmonization`() {
        val branchId = "branch-001"
        val brandId = "brand-shabu"

        val promoRepo = FakeBuffetPromotionRepository()
        val promoMenuItemRepo = FakeBuffetPromotionMenuItemRepository()
        val tierRepo = FakeBuffetPromotionTierRepository()
        val tierMenuItemRepo = FakeBuffetTierMenuItemRepository()
        val sessionRepo = mock(BuffetSessionRepository::class.java)
        val branchRepo = mock(BranchRepository::class.java)
        val menuItemRepo = mock(MenuItemRepository::class.java)

        val branch = Branch(id = branchId, companyId = "c1", brandId = brandId, name = "Branch 1", code = "B1")
        org.mockito.Mockito.`when`(branchRepo.findById(branchId)).thenReturn(Optional.of(branch))

        val item1 = MenuItem(id = "item-1", branchId = branchId, categoryId = "c1", name = "Pork Slice", basePrice = BigDecimal("100.00"))
        val item2 = MenuItem(id = "item-2", branchId = branchId, categoryId = "c1", name = "Wagyu Beef", basePrice = BigDecimal("350.00"))
        org.mockito.Mockito.`when`(menuItemRepo.findAllById(listOf("item-1", "item-2"))).thenReturn(listOf(item1, item2))

        val buffetService = BuffetService(
            promotionRepository = promoRepo,
            promotionMenuItemRepository = promoMenuItemRepo,
            tierRepository = tierRepo,
            tierMenuItemRepository = tierMenuItemRepo,
            sessionRepository = sessionRepo,
            branchRepository = branchRepo,
            menuItemRepository = menuItemRepo
        )

        // 1. Create promotion with atomic items & surcharges
        val createDto = CreateBuffetPromotionDto(
            brandId = brandId,
            branchId = branchId,
            name = "Gold Buffet ฿899",
            pricePerPerson = BigDecimal("899.00"),
            durationMinutes = 120,
            items = listOf(
                BuffetPromotionItemLinkDto(menuItemId = item1.id, isFree = true, additionalPrice = BigDecimal.ZERO),
                BuffetPromotionItemLinkDto(menuItemId = item2.id, isFree = false, additionalPrice = BigDecimal("199.00"))
            )
        )

        val res = buffetService.createPromotion(createDto)

        // Verify promo saved
        assertNotNull(res.id)
        val savedPromo = promoRepo.findById(res.id).get()
        assertEquals("Gold Buffet ฿899", savedPromo.name)
        assertEquals(BigDecimal("899.00"), savedPromo.pricePerPerson)
        assertEquals(120, savedPromo.durationMinutes)

        // Verify promo menu items saved with surcharges
        val savedItems = promoMenuItemRepo.findByPromotionId(res.id)
        assertEquals(2, savedItems.size)
        val freeItem = savedItems.find { it.menuItemId == item1.id }!!
        assertTrue(freeItem.isFree)
        assertEquals(BigDecimal.ZERO, freeItem.additionalPrice)

        val surchargeItem = savedItems.find { it.menuItemId == item2.id }!!
        assertFalse(surchargeItem.isFree)
        assertEquals(BigDecimal("199.00"), surchargeItem.additionalPrice)

        // Verify harmonized BuffetPromotionTier saved
        val savedTier = tierRepo.findById(res.id).get()
        assertEquals(savedPromo.id, savedTier.id)
        assertEquals(savedPromo.id, savedTier.promotionId)
        assertEquals("Gold Buffet ฿899", savedTier.name)
        assertEquals(BigDecimal("899.00"), savedTier.adultPrice)

        // Verify tier menu items synchronized
        val tierItemIds = tierMenuItemRepo.findMenuItemIdsByTierId(res.id)
        assertEquals(2, tierItemIds.size)
        assertTrue(tierItemIds.contains(item1.id))
        assertTrue(tierItemIds.contains(item2.id))

        // 2. Test Atomic Update with modified items & surcharges
        val updateDto = UpdateBuffetPromotionDto(
            name = "Gold Buffet ฿999",
            pricePerPerson = BigDecimal("999.00"),
            durationMinutes = 100,
            items = listOf(
                BuffetPromotionItemLinkDto(menuItemId = item1.id, isFree = true, additionalPrice = BigDecimal.ZERO)
            )
        )

        val updatedRes = buffetService.updatePromotion(res.id, updateDto)
        assertEquals("Gold Buffet ฿999", updatedRes.name)
        assertEquals(1, updatedRes.eligibleMenuItemCount)

        // Verify tier updated in sync
        val updatedTier = tierRepo.findById(res.id).get()
        assertEquals("Gold Buffet ฿999", updatedTier.name)
        assertEquals(BigDecimal("999.00"), updatedTier.adultPrice)
        assertEquals(100, updatedTier.timeLimitMinutes)

        val updatedTierItemIds = tierMenuItemRepo.findMenuItemIdsByTierId(res.id)
        assertEquals(1, updatedTierItemIds.size)
        assertEquals(item1.id, updatedTierItemIds[0])
    }
}
