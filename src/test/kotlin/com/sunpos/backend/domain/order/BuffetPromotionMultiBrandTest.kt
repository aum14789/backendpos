package com.sunpos.backend.domain.order

import com.sunpos.backend.domain.catalog.MenuItem
import com.sunpos.backend.domain.catalog.MenuItemRepository
import com.sunpos.backend.domain.organization.Brand
import com.sunpos.backend.domain.organization.BrandRepository
import com.sunpos.backend.domain.organization.Branch
import com.sunpos.backend.domain.organization.BranchRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class BuffetPromotionMultiBrandTest {

    @Autowired
    private lateinit var buffetService: BuffetService

    @Autowired
    private lateinit var brandRepository: BrandRepository

    @Autowired
    private lateinit var branchRepository: BranchRepository

    @Autowired
    private lateinit var menuItemRepository: MenuItemRepository

    @Test
    fun `test multi-brand buffet promotion inheritance and menu filtering`() {
        val companyId = "comp-buffet-01"

        // 1. Create Brands A and B
        val brandA = brandRepository.save(
            Brand(companyId = companyId, name = "Shabu Brand A", code = "SHABU-A")
        )
        val brandB = brandRepository.save(
            Brand(companyId = companyId, name = "Yakiniku Brand B", code = "YAKI-B")
        )

        // 2. Create Branches under Brand A and B
        val branchA1 = branchRepository.save(
            Branch(companyId = companyId, brandId = brandA.id, name = "Siam Paragon A1", code = "BR-A1")
        )
        val branchB1 = branchRepository.save(
            Branch(companyId = companyId, brandId = brandB.id, name = "Central World B1", code = "BR-B1")
        )

        // 3. Create Menu Items
        val itemBeef = menuItemRepository.save(
            MenuItem(branchId = branchA1.id, categoryId = "cat-meat", name = "Wagyu Beef", basePrice = BigDecimal("350.00"))
        )
        val itemPork = menuItemRepository.save(
            MenuItem(branchId = branchA1.id, categoryId = "cat-meat", name = "Kurobuta Pork", basePrice = BigDecimal("250.00"))
        )
        val itemSalmon = menuItemRepository.save(
            MenuItem(branchId = branchB1.id, categoryId = "cat-fish", name = "Fresh Salmon", basePrice = BigDecimal("450.00"))
        )

        // 4. Create Brand-level Buffet Promotions:
        //    Brand A Promo: Standard Shabu ฿399 (90 mins, includes Pork)
        val promoA = buffetService.createPromotion(
            CreateBuffetPromotionDto(
                brandId = brandA.id,
                branchId = null, // Applies to all Brand A branches!
                name = "Standard Shabu ฿399",
                pricePerPerson = BigDecimal("399.0000"),
                durationMinutes = 90,
                menuItemIds = listOf(itemPork.id)
            )
        )

        //    Brand A Promo: Premium Shabu ฿599 (120 mins, includes Beef & Pork)
        val promoAPrem = buffetService.createPromotion(
            CreateBuffetPromotionDto(
                brandId = brandA.id,
                branchId = null,
                name = "Premium Shabu ฿599",
                pricePerPerson = BigDecimal("599.0000"),
                durationMinutes = 120,
                menuItemIds = listOf(itemBeef.id, itemPork.id)
            )
        )

        //    Brand B Promo: Yakiniku Buffet ฿699 (100 mins, includes Salmon)
        val promoB = buffetService.createPromotion(
            CreateBuffetPromotionDto(
                brandId = brandB.id,
                branchId = null,
                name = "Yakiniku Grand ฿699",
                pricePerPerson = BigDecimal("699.0000"),
                durationMinutes = 100,
                menuItemIds = listOf(itemSalmon.id)
            )
        )

        // 5. Query Promotions for Branch A1 (Should receive Brand A promos only)
        val branchAPromos = buffetService.listPromotionsByBranch(branchA1.id)
        assertEquals(2, branchAPromos.size)
        assertTrue(branchAPromos.any { it.name == "Standard Shabu ฿399" && it.durationMinutes == 90 })
        assertTrue(branchAPromos.any { it.name == "Premium Shabu ฿599" && it.durationMinutes == 120 })

        // Query Promotions for Branch B1 (Should receive Brand B promos only)
        val branchBPromos = buffetService.listPromotionsByBranch(branchB1.id)
        assertEquals(1, branchBPromos.size)
        assertEquals("Yakiniku Grand ฿699", branchBPromos[0].name)
        assertEquals(100, branchBPromos[0].durationMinutes)

        // 6. Test Menu Item filtering per promotion
        val promoAItems = buffetService.getMenuItemsForPromotion(promoA.id)
        assertEquals(1, promoAItems.size)
        assertEquals("Kurobuta Pork", promoAItems[0].name)

        val promoAPremItems = buffetService.getMenuItemsForPromotion(promoAPrem.id)
        assertEquals(2, promoAPremItems.size)

        // 7. Start Session & calculate headcount charge
        val session = buffetService.startPromotionSession(
            StartBuffetPromotionSessionDto(
                orderId = "ord-buf-001",
                branchId = branchA1.id,
                promotionId = promoAPrem.id,
                headcount = 4
            )
        )
        assertNotNull(session.id)
        assertEquals(4, session.adultCount)
        assertEquals(BigDecimal("2396.0000"), session.totalCharge) // 599 * 4 = 2396.00
        assertEquals(120, session.timeLimitMinutes)
    }
}
