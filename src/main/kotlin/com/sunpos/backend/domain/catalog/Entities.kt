package com.sunpos.backend.domain.catalog

import java.io.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class MenuCategory(
    val id: String = UUID.randomUUID().toString(),
    var branchId: String = "",
    var name: String = "",
    var description: String? = null,
    var sortOrder: Int = 0,
    var isActive: Boolean = true,
    val createdAt: Instant = Instant.now()
)

class MenuItem(
    val id: String = UUID.randomUUID().toString(),
    var branchId: String = "",
    var categoryId: String = "",
    var name: String = "",
    var description: String? = null,
    var sku: String? = null,
    var basePrice: BigDecimal = BigDecimal.ZERO,
    var availability: String = "AVAILABLE", // AVAILABLE, SOLD_OUT, DISABLED
    var imageUrl: String? = null,
    var sortOrder: Int = 0,
    var isActive: Boolean = true,
    val createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now(),
    var version: Long = 0
)

class ModifierGroup(
    val id: String = UUID.randomUUID().toString(),
    var branchId: String = "",
    var name: String = "",
    var minSelection: Int = 0,
    var maxSelection: Int = 1,
    var isRequired: Boolean = false,
    val createdAt: Instant = Instant.now()
)

class Modifier(
    val id: String = UUID.randomUUID().toString(),
    var modifierGroupId: String = "",
    var name: String = "",
    var price: BigDecimal = BigDecimal.ZERO,
    var isActive: Boolean = true,
    val createdAt: Instant = Instant.now()
)

data class MenuItemModifierGroupId(
    var menuItemId: String = "",
    var modifierGroupId: String = ""
) : Serializable

class MenuItemModifierGroup(
    val id: String = UUID.randomUUID().toString(),
    var menuItemId: String = "",
    var modifierGroupId: String = ""
) {
    constructor(menuItemId: String, modifierGroupId: String) : this(
        id = "${menuItemId}_$modifierGroupId",
        menuItemId = menuItemId,
        modifierGroupId = modifierGroupId
    )
}

class ComboDefinition(
    val id: String = UUID.randomUUID().toString(),
    var menuItemId: String = "",
    var name: String = "",
    var isActive: Boolean = true,
    val createdAt: Instant = Instant.now()
)

class ComboGroup(
    val id: String = UUID.randomUUID().toString(),
    var comboDefinitionId: String = "",
    var name: String = "",
    var minSelection: Int = 1,
    var maxSelection: Int = 1,
    var sortOrder: Int = 0
)

class ComboChoice(
    val id: String = UUID.randomUUID().toString(),
    var comboGroupId: String = "",
    var menuItemId: String = "",
    var priceOverride: BigDecimal? = null,
    var surcharge: BigDecimal = BigDecimal.ZERO,
    var isFree: Boolean = false,
    var sortOrder: Int = 0
)

// DTOs
data class MenuItemCreateDto(
    val branchId: String = "",
    val categoryId: String = "",
    val name: String = "",
    val description: String? = null,
    val sku: String? = null,
    val basePrice: BigDecimal = BigDecimal.ZERO,
    val availability: String = "AVAILABLE",
    val modifierGroupIds: List<String> = emptyList(),
    val isCombo: Boolean = false,
    val comboGroups: List<ComboGroupCreateDto> = emptyList()
)

data class ComboGroupCreateDto(
    val name: String = "",
    val minSelection: Int = 1,
    val maxSelection: Int = 1,
    val choices: List<ComboChoiceCreateDto> = emptyList()
)

data class ComboChoiceCreateDto(
    val menuItemId: String = "",
    val priceOverride: BigDecimal? = null,
    val surcharge: BigDecimal = BigDecimal.ZERO,
    val isFree: Boolean = false
)

data class MenuItemResponseDto(
    val id: String = "",
    val branchId: String = "",
    val categoryId: String = "",
    val name: String = "",
    val description: String? = null,
    val sku: String? = null,
    val basePrice: BigDecimal = BigDecimal.ZERO,
    val availability: String = "AVAILABLE",
    val modifierGroups: List<ModifierGroupResponseDto> = emptyList(),
    val comboDefinition: ComboDefinitionResponseDto? = null
)

data class ModifierGroupResponseDto(
    val id: String = "",
    val name: String = "",
    val minSelection: Int = 0,
    val maxSelection: Int = 1,
    val isRequired: Boolean = false,
    val modifiers: List<Modifier> = emptyList()
)

data class ComboDefinitionResponseDto(
    val id: String = "",
    val name: String = "",
    val groups: List<ComboGroupResponseDto> = emptyList()
)

data class ComboGroupResponseDto(
    val id: String = "",
    val name: String = "",
    val minSelection: Int = 1,
    val maxSelection: Int = 1,
    val choices: List<ComboChoiceResponseDto> = emptyList()
)

data class ComboChoiceResponseDto(
    val id: String = "",
    val menuItemId: String = "",
    val itemName: String = "",
    val priceOverride: BigDecimal? = null,
    val surcharge: BigDecimal = BigDecimal.ZERO,
    val isFree: Boolean = false
)
