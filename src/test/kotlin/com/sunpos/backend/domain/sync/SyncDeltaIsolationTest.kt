package com.sunpos.backend.domain.sync

import com.fasterxml.jackson.databind.ObjectMapper
import com.sunpos.backend.domain.businessday.BusinessDayRepository
import com.sunpos.backend.domain.catalog.*
import com.sunpos.backend.domain.order.*
import com.sunpos.backend.domain.organization.Branch
import com.sunpos.backend.domain.organization.BranchRepository
import com.sunpos.backend.domain.payment.PaymentTransactionRepository
import com.sunpos.backend.domain.shift.CashierShiftRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.math.BigDecimal
import java.util.Optional

class SyncDeltaIsolationTest {

    @Test
    fun `test POS sync isolation guard, brand category inheritance, and buffet item sync`() {
        val branchId = "branch-001"
        val brandId = "brand-shabu"

        // Mock Repositories
        val syncEventRepo = mock(SyncEventRepository::class.java)
        val deviceSyncStateRepo = mock(DeviceSyncStateRepository::class.java)
        val menuItemRepo = mock(MenuItemRepository::class.java)
        val categoryRepo = mock(MenuCategoryRepository::class.java)
        val orderRepo = mock(OrderRepository::class.java)
        val paymentRepo = mock(PaymentTransactionRepository::class.java)
        val shiftRepo = mock(CashierShiftRepository::class.java)
        val businessDayRepo = mock(BusinessDayRepository::class.java)
        val branchRepo = mock(BranchRepository::class.java)
        val menuItemBranchRepo = mock(MenuItemBranchRepository::class.java)
        val buffetPromotionRepo = mock(BuffetPromotionRepository::class.java)
        val buffetPromotionMenuItemRepo = mock(BuffetPromotionMenuItemRepository::class.java)

        // Branch setup
        val branch = Branch(
            id = branchId,
            companyId = "comp-01",
            brandId = brandId,
            name = "Siam Paragon Branch",
            code = "SP01"
        )
        `when`(branchRepo.findById(branchId)).thenReturn(Optional.of(branch))

        // Categories: 1 brand-level category (branchId = null), 1 other brand category
        val brandCat = MenuCategory(
            id = "cat-brand-1",
            brandId = brandId,
            branchId = null,
            name = "Meats",
            sortOrder = 1,
            isActive = true
        )
        val otherBrandCat = MenuCategory(
            id = "cat-other-brand",
            brandId = "brand-pizza",
            branchId = null,
            name = "Pizzas",
            sortOrder = 2,
            isActive = true
        )
        `when`(categoryRepo.findAll()).thenReturn(listOf(brandCat, otherBrandCat))

        // Menu Items
        // 1. Draft master item (brandId = brandId, NOT allocated to branch, NOT in buffet)
        val draftItem = MenuItem(
            id = "item-draft-lobster",
            brandId = brandId,
            branchId = "",
            categoryId = brandCat.id,
            name = "Canadian Lobster",
            basePrice = BigDecimal("1200.00"),
            isActive = true
        )
        // 2. À la carte allocated item
        val alacarteItem = MenuItem(
            id = "item-alacarte-wagyu",
            brandId = brandId,
            branchId = "",
            categoryId = brandCat.id,
            name = "Wagyu A5 Slice",
            basePrice = BigDecimal("450.00"),
            isActive = true
        )
        // 3. Buffet-only item
        val buffetItem = MenuItem(
            id = "item-buffet-pork",
            brandId = brandId,
            branchId = "",
            categoryId = brandCat.id,
            name = "Pork Belly Slice",
            basePrice = BigDecimal("0.00"),
            isActive = true
        )

        `when`(menuItemRepo.findAll()).thenReturn(listOf(draftItem, alacarteItem, buffetItem))

        // Branch allocations: Only alacarteItem is active for this branch
        val branchLink = MenuItemBranch(
            id = "link-01",
            menuItemId = alacarteItem.id,
            brandId = brandId,
            branchId = branchId,
            isActive = true,
            priceOverride = BigDecimal("480.00")
        )
        `when`(menuItemBranchRepo.findByBranchId(branchId)).thenReturn(listOf(branchLink))

        // Buffet promotions: Active buffet promotion for branch
        val buffetPromo = BuffetPromotion(
            id = "promo-buffet-gold",
            brandId = brandId,
            branchId = branchId,
            name = "Gold Buffet Package",
            pricePerPerson = BigDecimal("699.00"),
            durationMinutes = 100,
            status = BuffetPromotionStatus.ACTIVE
        )
        `when`(buffetPromotionRepo.findPromotionsForBranch(brandId, branchId, BuffetPromotionStatus.ACTIVE))
            .thenReturn(listOf(buffetPromo))
        `when`(buffetPromotionMenuItemRepo.findMenuItemIdsByPromotionId(buffetPromo.id))
            .thenReturn(listOf(buffetItem.id))

        val syncService = SyncService(
            syncEventRepository = syncEventRepo,
            deviceSyncStateRepository = deviceSyncStateRepo,
            menuItemRepository = menuItemRepo,
            categoryRepository = categoryRepo,
            orderRepository = orderRepo,
            paymentRepository = paymentRepo,
            shiftRepository = shiftRepo,
            businessDayRepository = businessDayRepo,
            objectMapper = ObjectMapper(),
            branchRepository = branchRepo,
            menuItemBranchRepository = menuItemBranchRepo,
            buffetPromotionRepository = buffetPromotionRepo,
            buffetPromotionMenuItemRepository = buffetPromotionMenuItemRepo
        )

        // Execute getDelta
        val delta = syncService.getDelta(branchId, null)

        // Assert 1: Brand category inherited, other brand's category excluded
        val categoryIds = delta.categories.map { it.categoryId }
        assertTrue(categoryIds.contains(brandCat.id), "Brand category must inherit to branch POS")
        assertFalse(categoryIds.contains(otherBrandCat.id), "Other brand category must not leak")

        // Assert 2: POS Isolation Guard - Draft brand item must NEVER leak to POS
        val menuItemIds = delta.menuItems.map { it.itemId }
        assertFalse(menuItemIds.contains(draftItem.id), "Draft brand master item must NOT leak into POS delta!")

        // Assert 3: Active branch À la carte item IS present with price override
        val alacarteDto = delta.menuItems.find { it.itemId == alacarteItem.id }
        assertNotNull(alacarteDto, "Active branch À la carte item must be present")
        assertEquals(48000L, alacarteDto?.basePrice, "Price override 480.00 (48000 satang) must be reflected")

        // Assert 4: Buffet promotion is present and eligibleItemIds is populated
        assertEquals(1, delta.buffetTiers.size)
        val tierDto = delta.buffetTiers.first()
        assertEquals(buffetPromo.id, tierDto.promotionId)
        assertTrue(tierDto.eligibleItemIds.contains(buffetItem.id), "eligibleItemIds must contain buffet item id")

        // Assert 5: Buffet item is present in menuItems so POS terminal can order it
        assertTrue(menuItemIds.contains(buffetItem.id), "Buffet item must be delivered in menuItems for POS buffet ordering")
    }
}
