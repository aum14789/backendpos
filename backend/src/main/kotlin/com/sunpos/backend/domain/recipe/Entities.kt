package com.sunpos.backend.domain.recipe

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

enum class ProductionStatus {
    DRAFT,
    APPROVED,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}

class Recipe(
    val id: String = UUID.randomUUID().toString(),
    var menuItemId: String = "",
    var name: String = "",
    var version: String = "v1.0",
    var yieldQuantity: BigDecimal = BigDecimal("1.0000"),
    var yieldUnit: String = "portion",
    var isActive: Boolean = true,
    var startDate: Instant? = null,
    var endDate: Instant? = null,
    var notes: String? = null,
    val createdAt: Instant = Instant.now()
)

class RecipeIngredient(
    val id: String = UUID.randomUUID().toString(),
    var recipeId: String = "",
    var inventoryItemId: String = "",
    var quantity: BigDecimal = BigDecimal.ZERO,
    var unit: String = "",
    var wastePercentage: BigDecimal = BigDecimal.ZERO
)

class RecipeIngredientSubstitute(
    val id: String = UUID.randomUUID().toString(),
    var recipeIngredientId: String = "",
    var priority: Int = 1,
    var inventoryItemId: String = "",
    var quantity: BigDecimal = BigDecimal.ZERO,
    var unit: String = "",
    var wastePercentage: BigDecimal = BigDecimal.ZERO
)

class Bom(
    val id: String = UUID.randomUUID().toString(),
    var finishedInventoryItemId: String = "",
    var name: String = "",
    var version: String = "v1.0",
    var plannedOutputQuantity: BigDecimal = BigDecimal.ZERO,
    var outputUnit: String = "",
    var isActive: Boolean = true,
    var notes: String? = null,
    val createdAt: Instant = Instant.now()
)

class BomItem(
    val id: String = UUID.randomUUID().toString(),
    var bomId: String = "",
    var rawInventoryItemId: String = "",
    var quantity: BigDecimal = BigDecimal.ZERO,
    var unit: String = ""
)

class ProductionOrder(
    val id: String = UUID.randomUUID().toString(),
    var warehouseId: String = "",
    var bomId: String = "",
    var productionNumber: String = "",
    var status: ProductionStatus = ProductionStatus.APPROVED,
    var plannedQuantity: BigDecimal = BigDecimal.ZERO,
    var actualQuantity: BigDecimal = BigDecimal.ZERO,
    var yieldPercentage: BigDecimal = BigDecimal("100.00"),
    var unit: String = "",
    var totalMaterialCost: BigDecimal = BigDecimal.ZERO,
    var laborCost: BigDecimal = BigDecimal.ZERO,
    var packagingCost: BigDecimal = BigDecimal.ZERO,
    var overheadCost: BigDecimal = BigDecimal.ZERO,
    var startedAt: Instant? = null,
    var completedAt: Instant? = null,
    var createdBy: String? = null,
    val createdAt: Instant = Instant.now()
)

class ProductionOrderItem(
    val id: String = UUID.randomUUID().toString(),
    var productionOrderId: String = "",
    var rawInventoryItemId: String = "",
    var plannedQty: BigDecimal = BigDecimal.ZERO,
    var actualQty: BigDecimal = BigDecimal.ZERO,
    var unit: String = "",
    var unitCost: BigDecimal = BigDecimal.ZERO,
    var totalCost: BigDecimal = BigDecimal.ZERO
)

class OrderRecipeSnapshot(
    val id: String = UUID.randomUUID().toString(),
    var orderId: String = "",
    var menuItemId: String = "",
    var recipeId: String = "",
    var recipeVersion: String = "",
    val createdAt: Instant = Instant.now()
)

// DTOs
data class CreateRecipeDto(
    val menuItemId: String = "",
    val name: String = "",
    val version: String = "v1.0",
    val yieldQuantity: BigDecimal = BigDecimal.ONE,
    val yieldUnit: String = "portion",
    val startDate: Instant? = null,
    val endDate: Instant? = null,
    val ingredients: List<RecipeIngredientDto> = emptyList()
)

data class RecipeIngredientDto(
    val inventoryItemId: String = "",
    val quantity: BigDecimal = BigDecimal.ZERO,
    val unit: String = "",
    val wastePercentage: BigDecimal = BigDecimal.ZERO,
    val substitutes: List<RecipeIngredientSubstituteDto> = emptyList()
)

data class RecipeIngredientSubstituteDto(
    val priority: Int = 1,
    val inventoryItemId: String = "",
    val quantity: BigDecimal = BigDecimal.ZERO,
    val unit: String = "",
    val wastePercentage: BigDecimal = BigDecimal.ZERO
)

data class RecipeDetailedDto(
    val id: String = "",
    val menuItemId: String = "",
    val menuItemName: String = "",
    val menuItemSku: String = "",
    val name: String = "",
    val version: String = "v1.0",
    val yieldQuantity: BigDecimal = BigDecimal.ONE,
    val yieldUnit: String = "portion",
    val isActive: Boolean = true,
    val startDate: Instant? = null,
    val endDate: Instant? = null,
    val notes: String? = null,
    val ingredients: List<RecipeIngredientWithItemDto> = emptyList(),
    val totalCostEstimate: BigDecimal = BigDecimal.ZERO,
    val createdAt: Instant = Instant.now()
)

data class RecipeIngredientWithItemDto(
    val id: String = "",
    val recipeId: String = "",
    val inventoryItemId: String = "",
    val inventoryItemName: String = "",
    val inventoryItemSku: String = "",
    val quantity: BigDecimal = BigDecimal.ZERO,
    val unit: String = "",
    val wastePercentage: BigDecimal = BigDecimal.ZERO,
    val substitutes: List<RecipeIngredientSubstituteWithItemDto> = emptyList()
)

data class RecipeIngredientSubstituteWithItemDto(
    val id: String = "",
    val priority: Int = 1,
    val inventoryItemId: String = "",
    val inventoryItemName: String = "",
    val inventoryItemSku: String = "",
    val quantity: BigDecimal = BigDecimal.ZERO,
    val unit: String = "",
    val wastePercentage: BigDecimal = BigDecimal.ZERO
)

data class CreateBomDto(
    val finishedInventoryItemId: String = "",
    val name: String = "",
    val version: String = "v1.0",
    val plannedOutputQuantity: BigDecimal = BigDecimal.ZERO,
    val outputUnit: String = "",
    val items: List<BomItemDto> = emptyList()
)

data class BomItemDto(
    val rawInventoryItemId: String = "",
    val quantity: BigDecimal = BigDecimal.ZERO,
    val unit: String = ""
)

data class CreateProductionOrderDto(
    val warehouseId: String = "",
    val bomId: String = "",
    val plannedQuantity: BigDecimal = BigDecimal.ZERO,
    val unit: String = "",
    val createdBy: String? = null
)

data class CompleteProductionOrderDto(
    val actualQuantity: BigDecimal = BigDecimal.ZERO,
    val laborCost: BigDecimal = BigDecimal.ZERO,
    val packagingCost: BigDecimal = BigDecimal.ZERO,
    val overheadCost: BigDecimal = BigDecimal.ZERO
)

data class StockTraceRecordDto(
    val movementId: String = "",
    val timestamp: Instant = Instant.now(),
    val movementType: String = "",
    val warehouseName: String = "",
    val itemName: String = "",
    val quantity: BigDecimal = BigDecimal.ZERO,
    val unit: String = "",
    val unitCost: BigDecimal = BigDecimal.ZERO,
    val totalCost: BigDecimal = BigDecimal.ZERO,
    val referenceType: String? = null,
    val referenceId: String? = null
)
