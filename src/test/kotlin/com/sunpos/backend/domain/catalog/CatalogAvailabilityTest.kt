package com.sunpos.backend.domain.catalog

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal

class CatalogAvailabilityTest {

    private lateinit var jdbcTemplate: JdbcTemplate
    private lateinit var itemRepository: MenuItemRepository
    private lateinit var categoryRepository: MenuCategoryRepository
    private lateinit var menuItemBranchRepository: MenuItemBranchRepository
    private lateinit var modifierGroupRepository: ModifierGroupRepository
    private lateinit var modifierRepository: ModifierRepository
    private lateinit var menuItemModifierGroupRepository: MenuItemModifierGroupRepository
    private lateinit var comboDefinitionRepository: ComboDefinitionRepository
    private lateinit var comboGroupRepository: ComboGroupRepository
    private lateinit var comboChoiceRepository: ComboChoiceRepository
    private lateinit var catalogService: CatalogService

    @BeforeEach
    fun setUp() {
        jdbcTemplate = mock(JdbcTemplate::class.java)
        itemRepository = MenuItemRepository(jdbcTemplate)
        categoryRepository = MenuCategoryRepository(jdbcTemplate)
        menuItemBranchRepository = MenuItemBranchRepository(jdbcTemplate)
        modifierGroupRepository = ModifierGroupRepository(jdbcTemplate)
        modifierRepository = ModifierRepository(jdbcTemplate)
        menuItemModifierGroupRepository = MenuItemModifierGroupRepository(jdbcTemplate)
        comboDefinitionRepository = ComboDefinitionRepository(jdbcTemplate)
        comboGroupRepository = ComboGroupRepository(jdbcTemplate)
        comboChoiceRepository = ComboChoiceRepository(jdbcTemplate)

        catalogService = CatalogService(
            categoryRepository = categoryRepository,
            itemRepository = itemRepository,
            menuItemBranchRepository = menuItemBranchRepository,
            modifierGroupRepository = modifierGroupRepository,
            modifierRepository = modifierRepository,
            menuItemModifierGroupRepository = menuItemModifierGroupRepository,
            comboDefinitionRepository = comboDefinitionRepository,
            comboGroupRepository = comboGroupRepository,
            comboChoiceRepository = comboChoiceRepository,
            jdbcTemplate = jdbcTemplate
        )

        itemRepository.deleteAll()
    }

    @Test
    fun `test updateAvailability changes status from AVAILABLE to SOLD_OUT and back`() {
        val created = catalogService.createMenuItem(
            MenuItemCreateDto(
                branchId = "branch-001",
                categoryId = "cat-001",
                name = "Kurobuta Pork Slices",
                basePrice = BigDecimal("150.00"),
                availability = "AVAILABLE"
            )
        )
        assertEquals("AVAILABLE", created.availability)

        // Change to SOLD_OUT
        val soldOutResult = catalogService.updateAvailability(created.id, "SOLD_OUT")
        assertEquals("SOLD_OUT", soldOutResult.availability)

        // Verify in repo
        val itemInRepo = itemRepository.findById(created.id).get()
        assertEquals("SOLD_OUT", itemInRepo.availability)

        // Toggle back to AVAILABLE
        val availableResult = catalogService.updateAvailability(created.id, "AVAILABLE")
        assertEquals("AVAILABLE", availableResult.availability)
    }

    @Test
    fun `test updateAvailability with invalid status throws IllegalArgumentException`() {
        val created = catalogService.createMenuItem(
            MenuItemCreateDto(
                branchId = "branch-001",
                categoryId = "cat-001",
                name = "Salmon Sashimi",
                basePrice = BigDecimal("250.00")
            )
        )

        val ex = assertThrows(IllegalArgumentException::class.java) {
            catalogService.updateAvailability(created.id, "UNKNOWN_STATUS")
        }
        assertTrue(ex.message!!.contains("Invalid availability status"))
    }
}
