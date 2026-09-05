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
    var brandId: String? = null,
    var categoryId: String = "",
    var name: String = "",
    var description: String? = null,
    var sku: String? = null,
    var barcode: String? = null,
    var basePrice: BigDecimal = BigDecimal.ZERO,
    var costPrice: BigDecimal = BigDecimal.ZERO,
    var availability: String = "AVAILABLE", // AVAILABLE, SOLD_OUT, DISABLED
    var imageUrl: String? = null,
    var sortOrder: Int = 0,
    var isActive: Boolean = true,
    var itemType: String = "FG", // FG, RM, SEMI
    var specialType: String? = null, // 'S' (SET), null (NORMAL)
    var effectiveDate: java.time.LocalDate? = null,
    var expiryDate: java.time.LocalDate? = null,
    var isVatInclusive: Boolean = true,
    var vatRate: BigDecimal = BigDecimal("7.00"),
    var allowDecimalQty: Boolean = false,
    var kitchenStation: String? = "MAIN_KITCHEN",
    val createdAt: Instant = Instant.now(),
    var updatedAt: Instant = Instant.now(),
    var version: Long = 0
)

class MenuItemBranch(
    val id: String = UUID.randomUUID().toString(),
    var menuItemId: String = "",
    var brandId: String = "",
    var branchId: String = "",
    var isActive: Boolean = true,
    var priceOverride: BigDecimal? = null,
    val createdAt: Instant = Instant.now()
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

data class BranchAssignmentDto(
    val brandId: String = "",
    val branchId: String = "",
    val branchName: String = "",
    val isActive: Boolean = true,
    val priceOverride: BigDecimal? = null
)

data class MenuItemDeleteEligibilityDto(
    val canDelete: Boolean,
    val hasTransactions: Boolean,
    val transactionCount: Long = 0,
    val linkedBranches: List<String> = emptyList(),
    val linkedBuffetPackages: List<String> = emptyList(),
    val reasons: List<String> = emptyList()
)

// DTOs
data class ComboChoiceCreateDto(
    val menuItemId: String = "",
    val priceOverride: BigDecimal? = null,
    val surcharge: BigDecimal = BigDecimal.ZERO,
    val isFree: Boolean = false
)

data class ComboGroupCreateDto(
    val name: String = "",
    val minSelection: Int = 1,
    val maxSelection: Int = 1,
    val choices: List<ComboChoiceCreateDto> = emptyList()
)

data class MenuItemCreateDto(
    val id: String? = null,
    val branchId: String = "",
    val brandId: String? = null,
    val categoryId: String = "",
    val name: String = "",
    val description: String? = null,
    val sku: String? = null,
    val barcode: String? = null,
    val basePrice: BigDecimal = BigDecimal.ZERO,
    val costPrice: BigDecimal? = null,
    val availability: String = "AVAILABLE",
    val itemType: String = "FG",
    val specialType: String? = null,
    val effectiveDate: java.time.LocalDate? = null,
    val expiryDate: java.time.LocalDate? = null,
    val isVatInclusive: Boolean = true,
    val vatRate: BigDecimal = BigDecimal("7.00"),
    val allowDecimalQty: Boolean = false,
    val kitchenStation: String? = "MAIN_KITCHEN",
    val imageUrl: String? = null,
    val modifierGroupIds: List<String> = emptyList(),
    val isCombo: Boolean = false,
    val comboGroups: List<ComboGroupCreateDto> = emptyList(),
    val branchAssignments: List<BranchAssignmentDto> = emptyList()
)

data class MenuItemResponseDto(
    val id: String = "",
    val branchId: String = "",
    val brandId: String? = null,
    val categoryId: String = "",
    val categoryName: String = "",
    val name: String = "",
    val description: String? = null,
    val sku: String? = null,
    val barcode: String? = null,
    val basePrice: BigDecimal = BigDecimal.ZERO,
    val costPrice: BigDecimal = BigDecimal.ZERO,
    val availability: String = "AVAILABLE",
    val itemType: String = "FG",
    val specialType: String? = null,
    val effectiveDate: java.time.LocalDate? = null,
    val expiryDate: java.time.LocalDate? = null,
    val isVatInclusive: Boolean = true,
    val vatRate: BigDecimal = BigDecimal("7.00"),
    val allowDecimalQty: Boolean = false,
    val kitchenStation: String? = "MAIN_KITCHEN",
    val imageUrl: String? = null,
    val isActive: Boolean = true,
    val modifierGroups: List<ModifierGroupResponseDto> = emptyList(),
    val comboDefinition: ComboDefinitionResponseDto? = null,
    val branchAssignments: List<BranchAssignmentDto> = emptyList()
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

data class ItemBranchAllocationEntry(
    val menuItemId: String = "",
    val brandId: String = "",
    val branchIds: List<String> = emptyList()
)

data class BatchBranchAllocationRequest(
    val branchIdsScope: List<String> = emptyList(),
    val allocations: List<ItemBranchAllocationEntry> = emptyList(),
    val brandAllocations: Map<String, List<String>> = emptyMap() // itemId -> list of brandIds
)

data class BranchAllocationsResponse(
    val allocations: Map<String, List<String>> = emptyMap(),
    val brandAllocations: Map<String, List<String>> = emptyMap()
)
