package com.sunpos.backend.domain.recipe

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
class RecipeServiceTest {

    @Autowired
    private lateinit var recipeService: RecipeService

    @Test
    fun `test recipe versioning and BOM creation`() {
        val dto1 = CreateRecipeDto(
            menuItemId = "menu-test-1",
            name = "Kra Pao v1.0",
            version = "v1.0",
            ingredients = listOf(RecipeIngredientDto(inventoryItemId = "raw-001", quantity = BigDecimal("0.1"), unit = "kg"))
        )
        val r1 = recipeService.createRecipe(dto1)
        assertEquals("v1.0", r1.version)
        assertTrue(r1.isActive)

        // Version 2 should deactivate Version 1
        val dto2 = CreateRecipeDto(
            menuItemId = "menu-test-1",
            name = "Kra Pao v2.0 Extra Pork",
            version = "v2.0",
            ingredients = listOf(RecipeIngredientDto(inventoryItemId = "raw-001", quantity = BigDecimal("0.15"), unit = "kg"))
        )
        val r2 = recipeService.createRecipe(dto2)
        assertEquals("v2.0", r2.version)
        assertTrue(r2.isActive)

        val active = recipeService.getActiveRecipe("menu-test-1")
        assertTrue(active.isPresent)
        assertEquals(r2.id, active.get().id)
    }
}
