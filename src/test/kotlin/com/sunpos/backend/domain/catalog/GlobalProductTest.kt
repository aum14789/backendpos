package com.sunpos.backend.domain.catalog

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.util.Optional

class GlobalProductTest {

    class FakeGlobalProductRepository : GlobalProductRepository(mock(JdbcTemplate::class.java)) {
        val items = mutableMapOf<String, GlobalProduct>()
        override fun save(entity: GlobalProduct): GlobalProduct {
            items[entity.id] = entity
            return entity
        }
        override fun findById(id: Any): Optional<GlobalProduct> = Optional.ofNullable(items[id.toString()])
        override fun findByName(name: String): Optional<GlobalProduct> =
            Optional.ofNullable(items.values.find { it.name.equals(name, ignoreCase = true) })
        override fun findByCompanyId(companyId: String): List<GlobalProduct> = items.values.toList()
        override fun findAll(): List<GlobalProduct> = items.values.toList()
    }

    class FakeMenuItemRepository : MenuItemRepository(mock(JdbcTemplate::class.java)) {
        val items = mutableMapOf<String, MenuItem>()
        override fun save(entity: MenuItem): MenuItem {
            items[entity.id] = entity
            return entity
        }
        override fun findById(id: Any): Optional<MenuItem> = Optional.ofNullable(items[id.toString()])
        override fun findAll(): List<MenuItem> = items.values.toList()
        override fun existsById(id: Any): Boolean = items.containsKey(id.toString())
    }

    @Test
    fun `test 2-tier product concept creation and multi-brand aggregation`() {
        val categoryRepo = mock(MenuCategoryRepository::class.java)
        val itemRepo = FakeMenuItemRepository()
        val modGroupRepo = mock(ModifierGroupRepository::class.java)
        val modRepo = mock(ModifierRepository::class.java)
        val itemModRepo = mock(MenuItemModifierGroupRepository::class.java)
        val comboDefRepo = mock(ComboDefinitionRepository::class.java)
        val comboGroupRepo = mock(ComboGroupRepository::class.java)
        val comboChoiceRepo = mock(ComboChoiceRepository::class.java)
        val itemBranchRepo = mock(MenuItemBranchRepository::class.java)
        val jdbcTemplate = mock(JdbcTemplate::class.java)
        val globalProductRepo = FakeGlobalProductRepository()

        val catalogService = CatalogService(
            categoryRepository = categoryRepo,
            itemRepository = itemRepo,
            modifierGroupRepository = modGroupRepo,
            modifierRepository = modRepo,
            menuItemModifierGroupRepository = itemModRepo,
            comboDefinitionRepository = comboDefRepo,
            comboGroupRepository = comboGroupRepo,
            comboChoiceRepository = comboChoiceRepo,
            menuItemBranchRepository = itemBranchRepo,
            jdbcTemplate = jdbcTemplate,
            globalProductRepository = globalProductRepo
        )

        // 1. Create Core Dish Concept (Tier 1)
        val gp = catalogService.createGlobalProduct(
            GlobalProductCreateDto(
                companyId = "comp-001",
                code = "GP-PORK-BOWL",
                name = "ข้าวหน้าหมู (Pork Rice Bowl Concept)",
                description = "สูตรหลักข้าวหน้าหมูประจำเครือ"
            )
        )
        assertNotNull(gp.id)
        assertEquals("GP-PORK-BOWL", gp.code)

        // 2. Brand A creates item linked to Tier 1
        val itemBrandA = catalogService.createMenuItem(
            MenuItemCreateDto(
                brandId = "brand-shabu",
                categoryId = "cat-01",
                globalProductId = gp.id,
                name = "ชาบูหมูด้งพรีเมียม (Shabu Premium Pork Bowl)",
                basePrice = BigDecimal("189.00")
            )
        )
        assertEquals(gp.id, itemBrandA.globalProductId)
        assertEquals(gp.name, itemBrandA.globalProductName)

        // 3. Brand B creates item linked to SAME Tier 1
        val itemBrandB = catalogService.createMenuItem(
            MenuItemCreateDto(
                brandId = "brand-izakaya",
                categoryId = "cat-02",
                globalProductId = gp.id,
                name = "บูตะด้งย่างถ่าน (Charcoal Butadon)",
                basePrice = BigDecimal("159.00")
            )
        )
        assertEquals(gp.id, itemBrandB.globalProductId)
        assertEquals(gp.name, itemBrandB.globalProductName)

        // 4. Brand C creates item with autoCreateGlobalProduct = true
        val itemBrandC = catalogService.createMenuItem(
            MenuItemCreateDto(
                brandId = "brand-express",
                categoryId = "cat-03",
                autoCreateGlobalProduct = true,
                name = "เกี๊ยวซ่าทอดกรอบ (Crispy Gyoza)",
                basePrice = BigDecimal("89.00")
            )
        )
        assertNotNull(itemBrandC.globalProductId, "Auto-created Global Product ID must not be null")
        assertEquals("เกี๊ยวซ่าทอดกรอบ (Crispy Gyoza)", itemBrandC.globalProductName)

        // 5. Query corporate aggregation by Global Product ID
        val aggregatedItems = catalogService.listMenuItemsByGlobalProduct(gp.id)
        assertEquals(2, aggregatedItems.size, "Must aggregate all brand variations for the core concept")
        val brandIds = aggregatedItems.map { it.brandId }.toSet()
        assertTrue(brandIds.contains("brand-shabu"))
        assertTrue(brandIds.contains("brand-izakaya"))
    }
}
