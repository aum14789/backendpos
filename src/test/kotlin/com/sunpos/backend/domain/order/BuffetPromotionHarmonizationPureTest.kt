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

    @Test
    fun `test clean buffet promotion creation with surcharges and direct session link`() {
        val branchId = "branch-001"
        val brandId = "brand-shabu"

        val promoRepo = FakeBuffetPromotionRepository()
        val promoMenuItemRepo = FakeBuffetPromotionMenuItemRepository()
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

        // Verify promo saved directly to buffet_promotions
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

        // 2. Test Update with modified items & surcharges
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

        val updatedPromo = promoRepo.findById(res.id).get()
        assertEquals("Gold Buffet ฿999", updatedPromo.name)
        assertEquals(BigDecimal("999.00"), updatedPromo.pricePerPerson)
        assertEquals(100, updatedPromo.durationMinutes)

        val updatedItems = promoMenuItemRepo.findByPromotionId(res.id)
        assertEquals(1, updatedItems.size)
        assertEquals(item1.id, updatedItems[0].menuItemId)
    }
}
