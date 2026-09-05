package com.sunpos.backend.domain.catalog

import com.sunpos.backend.common.ApiResponse
import com.sunpos.backend.common.JdbcRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.Instant
import java.util.Optional
import java.util.UUID

@Repository
class MenuCategoryRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<MenuCategory>(jdbcTemplate, "menu_categories", MenuCategory::class.java) {
    fun findByBranchIdOrderBySortOrderAsc(branchId: String): List<MenuCategory> =
        findByField("branchId", branchId).sortedBy { it.sortOrder }
}

@Repository
class MenuItemRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<MenuItem>(jdbcTemplate, "menu_items", MenuItem::class.java) {
    fun findByBranchId(branchId: String): List<MenuItem> = findByField("branchId", branchId)
    fun findByCategoryId(categoryId: String): List<MenuItem> = findByField("categoryId", categoryId)
    fun findByName(name: String): Optional<MenuItem> = findOneByField("name", name)
}

@Repository
class MenuItemBranchRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<MenuItemBranch>(jdbcTemplate, "menu_item_branches", MenuItemBranch::class.java) {
    fun findByMenuItemId(menuItemId: String): List<MenuItemBranch> = findByField("menuItemId", menuItemId)
    fun findByBranchId(branchId: String): List<MenuItemBranch> = findByField("branchId", branchId)
    fun deleteByMenuItemId(menuItemId: String) {
        val list = findByMenuItemId(menuItemId)
        list.forEach { deleteById(it.id) }
    }
}

@Repository
class ModifierGroupRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<ModifierGroup>(jdbcTemplate, "modifier_groups", ModifierGroup::class.java) {
    fun findByBranchId(branchId: String): List<ModifierGroup> = findByField("branchId", branchId)
}

@Repository
class ModifierRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<Modifier>(jdbcTemplate, "modifiers", Modifier::class.java) {
    fun findByModifierGroupId(modifierGroupId: String): List<Modifier> = findByField("modifierGroupId", modifierGroupId)
}

@Repository
class MenuItemModifierGroupRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<MenuItemModifierGroup>(jdbcTemplate, "menu_item_modifier_groups", MenuItemModifierGroup::class.java) {
    fun findByIdMenuItemId(menuItemId: String): List<MenuItemModifierGroup> = findByField("menuItemId", menuItemId)
}

@Repository
class ComboDefinitionRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<ComboDefinition>(jdbcTemplate, "combo_definitions", ComboDefinition::class.java) {
    fun findByMenuItemId(menuItemId: String): Optional<ComboDefinition> = findOneByField("menuItemId", menuItemId)
}

@Repository
class ComboGroupRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<ComboGroup>(jdbcTemplate, "combo_groups", ComboGroup::class.java) {
    fun findByComboDefinitionId(comboDefinitionId: String): List<ComboGroup> = findByField("comboDefinitionId", comboDefinitionId)
}

@Repository
class ComboChoiceRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<ComboChoice>(jdbcTemplate, "combo_choices", ComboChoice::class.java) {
    fun findByComboGroupId(comboGroupId: String): List<ComboChoice> = findByField("comboGroupId", comboGroupId)
}

@Service
class CatalogService(
    private val categoryRepository: MenuCategoryRepository,
    private val itemRepository: MenuItemRepository,
    private val modifierGroupRepository: ModifierGroupRepository,
    private val modifierRepository: ModifierRepository,
    private val menuItemModifierGroupRepository: MenuItemModifierGroupRepository,
    private val comboDefinitionRepository: ComboDefinitionRepository,
    private val comboGroupRepository: ComboGroupRepository,
    private val comboChoiceRepository: ComboChoiceRepository,
    private val menuItemBranchRepository: MenuItemBranchRepository,
    private val jdbcTemplate: JdbcTemplate
) {
    fun listCategories(branchId: String? = null): List<MenuCategory> {
        return if (!branchId.isNullOrBlank()) {
            categoryRepository.findByBranchIdOrderBySortOrderAsc(branchId)
        } else {
            categoryRepository.findAll().sortedBy { it.sortOrder }
        }
    }

    fun createCategory(category: MenuCategory): MenuCategory = categoryRepository.save(category)

    fun checkNameUnique(name: String, excludeId: String? = null) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val all = itemRepository.findAll()
        val existing = all.firstOrNull { it.name.trim().equals(trimmed, ignoreCase = true) && it.id != excludeId }
        if (existing != null) {
            throw IllegalArgumentException("ชื่อรายการอาหาร '$trimmed' มีอยู่ในระบบแล้ว กรุณาใช้ชื่ออื่น")
        }
    }

    fun checkIdUnique(id: String, excludeId: String? = null) {
        val trimmed = id.trim()
        if (trimmed.isBlank()) return
        if (trimmed.equals(excludeId?.trim(), ignoreCase = true)) return
        if (itemRepository.existsById(trimmed)) {
            throw IllegalArgumentException("รหัสสินค้า (ID) '$trimmed' มีอยู่ในระบบแล้ว ห้ามใช้ ID ซ้ำกันโดยเด็ดขาด")
        }
    }

    fun isNameUnique(name: String, excludeId: String? = null): Boolean {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return true
        val all = itemRepository.findAll()
        return all.none { it.name.trim().equals(trimmed, ignoreCase = true) && it.id != excludeId }
    }

    fun isIdUnique(id: String, excludeId: String? = null): Boolean {
        val trimmed = id.trim()
        if (trimmed.isBlank()) return true
        if (trimmed.equals(excludeId?.trim(), ignoreCase = true)) return true
        return !itemRepository.existsById(trimmed)
    }

    @Transactional
    fun createMenuItem(dto: MenuItemCreateDto): MenuItemResponseDto {
        checkNameUnique(dto.name)
        if (!dto.id.isNullOrBlank()) {
            checkIdUnique(dto.id)
        }

        val assignedId = dto.id?.trim()?.ifBlank { null } ?: UUID.randomUUID().toString()
        if (itemRepository.existsById(assignedId)) {
            throw IllegalArgumentException("รหัสสินค้า (ID) '$assignedId' มีอยู่ในระบบแล้ว ห้ามใช้ ID ซ้ำกันโดยเด็ดขาด")
        }

        val item = MenuItem(
            id = assignedId,
            branchId = dto.branchId,
            brandId = dto.brandId,
            categoryId = dto.categoryId,
            name = dto.name.trim(),
            description = dto.description,
            sku = dto.sku,
            barcode = dto.barcode,
            basePrice = dto.basePrice,
            costPrice = dto.costPrice ?: BigDecimal.ZERO,
            availability = dto.availability,
            itemType = dto.itemType.ifBlank { "FG" },
            specialType = dto.specialType,
            effectiveDate = dto.effectiveDate,
            expiryDate = dto.expiryDate,
            isVatInclusive = dto.isVatInclusive,
            vatRate = dto.vatRate,
            allowDecimalQty = dto.allowDecimalQty,
            kitchenStation = dto.kitchenStation ?: "MAIN_KITCHEN",
            imageUrl = dto.imageUrl
        )
        val saved = itemRepository.save(item)

        for (mgId in dto.modifierGroupIds) {
            menuItemModifierGroupRepository.save(MenuItemModifierGroup(menuItemId = saved.id, modifierGroupId = mgId))
        }

        if (dto.isCombo) {
            val comboDef = comboDefinitionRepository.save(
                ComboDefinition(menuItemId = saved.id, name = dto.name)
            )
            dto.comboGroups.forEachIndexed { groupIndex, groupDto ->
                val group = comboGroupRepository.save(
                    ComboGroup(
                        comboDefinitionId = comboDef.id,
                        name = groupDto.name,
                        minSelection = groupDto.minSelection,
                        maxSelection = groupDto.maxSelection,
                        sortOrder = groupIndex
                    )
                )
                groupDto.choices.forEach { choiceDto ->
                    comboChoiceRepository.save(
                        ComboChoice(
                            comboGroupId = group.id,
                            menuItemId = choiceDto.menuItemId,
                            priceOverride = choiceDto.priceOverride,
                            surcharge = choiceDto.surcharge,
                            isFree = choiceDto.isFree
                        )
                    )
                }
            }
        }

        // Save branch assignments
        if (dto.branchAssignments.isNotEmpty()) {
            for (assignment in dto.branchAssignments) {
                menuItemBranchRepository.save(
                    MenuItemBranch(
                        menuItemId = saved.id,
                        brandId = assignment.brandId,
                        branchId = assignment.branchId,
                        isActive = assignment.isActive,
                        priceOverride = assignment.priceOverride
                    )
                )
            }
        } else if (dto.branchId.isNotBlank()) {
            menuItemBranchRepository.save(
                MenuItemBranch(
                    menuItemId = saved.id,
                    brandId = dto.brandId ?: "",
                    branchId = dto.branchId,
                    isActive = true
                )
            )
        }

        return getMenuItemDetails(saved.id)
    }

    @Transactional
    fun updateMenuItem(id: String, dto: MenuItemCreateDto): MenuItemResponseDto {
        val item = itemRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Menu item not found: $id") }

        checkNameUnique(dto.name, excludeId = id)

        if (dto.branchId.isNotBlank()) item.branchId = dto.branchId
        if (dto.brandId != null) item.brandId = dto.brandId
        if (dto.categoryId.isNotBlank()) item.categoryId = dto.categoryId
        item.name = dto.name.trim()
        item.description = dto.description
        item.sku = dto.sku
        item.barcode = dto.barcode
        item.basePrice = dto.basePrice
        if (dto.costPrice != null) item.costPrice = dto.costPrice
        item.availability = dto.availability
        item.itemType = dto.itemType.ifBlank { "FG" }
        item.specialType = dto.specialType
        item.effectiveDate = dto.effectiveDate
        item.expiryDate = dto.expiryDate
        item.isVatInclusive = dto.isVatInclusive
        item.vatRate = dto.vatRate
        item.allowDecimalQty = dto.allowDecimalQty
        if (dto.kitchenStation != null) item.kitchenStation = dto.kitchenStation
        if (dto.imageUrl != null) item.imageUrl = dto.imageUrl
        item.updatedAt = Instant.now()
        itemRepository.save(item)

        // Update branch assignments if provided
        if (dto.branchAssignments.isNotEmpty()) {
            menuItemBranchRepository.deleteByMenuItemId(id)
            for (assignment in dto.branchAssignments) {
                menuItemBranchRepository.save(
                    MenuItemBranch(
                        menuItemId = id,
                        brandId = assignment.brandId,
                        branchId = assignment.branchId,
                        isActive = assignment.isActive,
                        priceOverride = assignment.priceOverride
                    )
                )
            }
        }

        return getMenuItemDetails(id)
    }

    fun checkDeleteEligibility(id: String): MenuItemDeleteEligibilityDto {
        val item = itemRepository.findById(id).orElse(null)
            ?: return MenuItemDeleteEligibilityDto(
                canDelete = false,
                hasTransactions = false,
                reasons = listOf("ไม่พบรายการอาหารในระบบ ($id)")
            )

        val reasons = mutableListOf<String>()

        // 1. Check order transactions in order_items
        val txCount = try {
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_items WHERE menu_item_id = ?",
                Long::class.java,
                id
            ) ?: 0L
        } catch (_: Exception) { 0L }

        val hasTx = txCount > 0
        if (hasTx) {
            reasons.add("มีประวัติการขายในระบบแล้ว ($txCount รายการ) เพื่อรักษาความถูกต้องของข้อมูลทางบัญชีและประวัติการสั่งซื้อ ระบบไม่อนุญาตให้ลบรายการนี้ออกจากฐานข้อมูลโดยเด็ดขาด (สามารถเลือก 'ปิดการใช้งาน' แทนได้)")
        }

        // 2. Check linked buffet packages
        val buffetPackages = try {
            jdbcTemplate.query(
                """
                SELECT DISTINCT bp.name 
                FROM buffet_promotions bp 
                JOIN buffet_promotion_menu_items bpmi ON bp.id = bpmi.promotion_id 
                WHERE bpmi.menu_item_id = ?
                """.trimIndent(),
                { rs, _ -> rs.getString("name") },
                id
            )
        } catch (_: Exception) { emptyList<String>() }

        if (buffetPackages.isNotEmpty()) {
            reasons.add("ยังผูกอยู่ในแพ็กเกจบุฟเฟ่ต์: ${buffetPackages.joinToString(", ")} (กรุณาถอดออกจากแพ็กเกจบุฟเฟ่ต์ก่อนทำการลบ)")
        }

        // 3. Check linked branches
        val linkedBranches = try {
            jdbcTemplate.query(
                """
                SELECT DISTINCT b.name 
                FROM branches b 
                JOIN menu_item_branches mib ON b.id = mib.branch_id 
                WHERE mib.menu_item_id = ?
                """.trimIndent(),
                { rs, _ -> rs.getString("name") },
                id
            )
        } catch (_: Exception) { emptyList<String>() }

        if (linkedBranches.isNotEmpty()) {
            reasons.add("ยังผูกอยู่กับสาขา: ${linkedBranches.joinToString(", ")} (กรุณาถอดออกจากสาขาก่อนทำการลบ)")
        }

        val canDelete = !hasTx && buffetPackages.isEmpty() && linkedBranches.isEmpty()

        return MenuItemDeleteEligibilityDto(
            canDelete = canDelete,
            hasTransactions = hasTx,
            transactionCount = txCount,
            linkedBranches = linkedBranches,
            linkedBuffetPackages = buffetPackages,
            reasons = reasons
        )
    }

    @Transactional
    fun deleteMenuItem(id: String) {
        val eligibility = checkDeleteEligibility(id)
        if (!eligibility.canDelete) {
            throw IllegalStateException(eligibility.reasons.joinToString(" | "))
        }
        menuItemModifierGroupRepository.findByIdMenuItemId(id).forEach {
            menuItemModifierGroupRepository.deleteById(it.id)
        }
        menuItemBranchRepository.deleteByMenuItemId(id)
        itemRepository.deleteById(id)
    }

    fun getMenuItemDetails(id: String): MenuItemResponseDto {
        val item = itemRepository.findById(id)
            .orElseThrow { NoSuchElementException("MenuItem $id not found") }

        val category = categoryRepository.findById(item.categoryId).orElse(null)

        val modifierGroupLinks = menuItemModifierGroupRepository.findByIdMenuItemId(id)
        val groupDtos = modifierGroupLinks.mapNotNull { link ->
            modifierGroupRepository.findById(link.modifierGroupId).map { group ->
                val modifiers = modifierRepository.findByModifierGroupId(group.id)
                ModifierGroupResponseDto(
                    id = group.id,
                    name = group.name,
                    minSelection = group.minSelection,
                    maxSelection = group.maxSelection,
                    isRequired = group.isRequired,
                    modifiers = modifiers
                )
            }.orElse(null)
        }

        val comboDefOpt = comboDefinitionRepository.findByMenuItemId(id)
        val comboDefDto = if (comboDefOpt.isPresent) {
            val comboDef = comboDefOpt.get()
            val groups = comboGroupRepository.findByComboDefinitionId(comboDef.id)
            val groupDtos = groups.map { g ->
                val choices = comboChoiceRepository.findByComboGroupId(g.id)
                val choiceDtos = choices.map { c ->
                    val choiceItem = itemRepository.findById(c.menuItemId).orElse(null)
                    ComboChoiceResponseDto(
                        id = c.id,
                        menuItemId = c.menuItemId,
                        itemName = choiceItem?.name ?: "Unknown",
                        priceOverride = c.priceOverride,
                        surcharge = c.surcharge,
                        isFree = c.isFree
                    )
                }
                ComboGroupResponseDto(
                    id = g.id,
                    name = g.name,
                    minSelection = g.minSelection,
                    maxSelection = g.maxSelection,
                    choices = choiceDtos
                )
            }
            ComboDefinitionResponseDto(
                id = comboDef.id,
                name = comboDef.name,
                groups = groupDtos
            )
        } else null

        val branches = menuItemBranchRepository.findByMenuItemId(item.id)
        val branchAssignments = branches.map { b ->
            BranchAssignmentDto(
                brandId = b.brandId,
                branchId = b.branchId,
                isActive = b.isActive,
                priceOverride = b.priceOverride
            )
        }

        return MenuItemResponseDto(
            id = item.id,
            branchId = item.branchId,
            brandId = item.brandId,
            categoryId = item.categoryId,
            categoryName = category?.name ?: "",
            name = item.name,
            description = item.description,
            sku = item.sku,
            barcode = item.barcode,
            basePrice = item.basePrice,
            costPrice = item.costPrice,
            availability = item.availability,
            itemType = item.itemType,
            specialType = item.specialType,
            effectiveDate = item.effectiveDate,
            expiryDate = item.expiryDate,
            isVatInclusive = item.isVatInclusive,
            vatRate = item.vatRate,
            allowDecimalQty = item.allowDecimalQty,
            kitchenStation = item.kitchenStation,
            imageUrl = item.imageUrl,
            isActive = item.isActive,
            modifierGroups = groupDtos,
            comboDefinition = comboDefDto,
            branchAssignments = branchAssignments
        )
    }

    fun listMenuItems(branchId: String? = null, categoryId: String? = null): List<MenuItemResponseDto> {
        val items = when {
            !branchId.isNullOrBlank() && !categoryId.isNullOrBlank() ->
                itemRepository.findByBranchId(branchId).filter { it.categoryId == categoryId }
            !branchId.isNullOrBlank() ->
                itemRepository.findByBranchId(branchId)
            !categoryId.isNullOrBlank() ->
                itemRepository.findByCategoryId(categoryId)
            else ->
                itemRepository.findAll()
        }
        return items.map { getMenuItemDetails(it.id) }
    }

    fun createModifierGroup(group: ModifierGroup): ModifierGroup = modifierGroupRepository.save(group)

    fun createModifier(modifier: Modifier): Modifier = modifierRepository.save(modifier)

    fun getBranchAllocations(): BranchAllocationsResponse {
        val allBranches = menuItemBranchRepository.findAll()
        val itemBranchMap = mutableMapOf<String, MutableList<String>>()
        val itemBrandMap = mutableMapOf<String, MutableSet<String>>()

        for (b in allBranches) {
            if (b.isActive && b.branchId.isNotBlank()) {
                itemBranchMap.computeIfAbsent(b.menuItemId) { mutableListOf() }.add(b.branchId)
            }
            if (b.brandId.isNotBlank()) {
                itemBrandMap.computeIfAbsent(b.menuItemId) { mutableSetOf() }.add(b.brandId)
            }
        }
        return BranchAllocationsResponse(
            allocations = itemBranchMap,
            brandAllocations = itemBrandMap.mapValues { it.value.toList() }
        )
    }

    @Transactional
    fun saveBatchBranchAllocations(request: BatchBranchAllocationRequest): BranchAllocationsResponse {
        val branchScope = if (request.branchIdsScope.isNotEmpty()) {
            request.branchIdsScope.toSet()
        } else {
            request.allocations.flatMap { it.branchIds }.toSet()
        }

        for (entry in request.allocations) {
            val itemId = entry.menuItemId
            val brandId = entry.brandId
            val activeBranchSet = entry.branchIds.toSet()

            for (branchId in branchScope) {
                val isActive = activeBranchSet.contains(branchId)
                jdbcTemplate.update(
                    """
                    INSERT INTO menu_item_branches (id, menu_item_id, brand_id, branch_id, is_active, created_at)
                    VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    ON CONFLICT (menu_item_id, branch_id) DO UPDATE 
                    SET is_active = EXCLUDED.is_active, 
                        brand_id = CASE WHEN EXCLUDED.brand_id != '' THEN EXCLUDED.brand_id ELSE menu_item_branches.brand_id END
                    """.trimIndent(),
                    "mib-${itemId}-${branchId}",
                    itemId,
                    brandId,
                    branchId,
                    isActive
                )
            }
        }

        // Save brand allocations if provided
        if (request.brandAllocations.isNotEmpty()) {
            for ((itemId, brandIds) in request.brandAllocations) {
                if (brandIds.isNotEmpty()) {
                    jdbcTemplate.update("UPDATE menu_items SET brand_id = ? WHERE id = ?", brandIds.first(), itemId)
                }
            }
        }

        return getBranchAllocations()
    }
}

@RestController
@RequestMapping("/api/v1/catalog")
class CatalogController(
    private val catalogService: CatalogService
) {
    @GetMapping("/categories")
    fun getCategories(@RequestParam(required = false) branchId: String?): ApiResponse<List<MenuCategory>> {
        return ApiResponse.success(catalogService.listCategories(branchId))
    }

    @PostMapping("/categories")
    @PreAuthorize("hasAuthority('MENU_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun createCategory(@RequestBody category: MenuCategory): ApiResponse<MenuCategory> {
        return ApiResponse.success(catalogService.createCategory(category), "Category created successfully")
    }

    @GetMapping("/items", "/menu-items")
    fun getMenuItems(
        @RequestParam(required = false) branchId: String?,
        @RequestParam(required = false) categoryId: String?
    ): ApiResponse<List<MenuItemResponseDto>> {
        return ApiResponse.success(catalogService.listMenuItems(branchId, categoryId))
    }

    @GetMapping("/items/check-name", "/menu-items/check-name")
    fun checkName(
        @RequestParam name: String,
        @RequestParam(required = false) excludeId: String?
    ): ApiResponse<Map<String, Boolean>> {
        val isUnique = catalogService.isNameUnique(name, excludeId)
        return ApiResponse.success(mapOf("isUnique" to isUnique))
    }

    @GetMapping("/items/check-id", "/menu-items/check-id")
    fun checkId(
        @RequestParam id: String,
        @RequestParam(required = false) excludeId: String?
    ): ApiResponse<Map<String, Boolean>> {
        val isUnique = catalogService.isIdUnique(id, excludeId)
        return ApiResponse.success(mapOf("isUnique" to isUnique))
    }

    @GetMapping("/items/{id}", "/menu-items/{id}")
    fun getMenuItem(@PathVariable id: String): ApiResponse<MenuItemResponseDto> {
        return ApiResponse.success(catalogService.getMenuItemDetails(id))
    }

    @PostMapping("/items", "/menu-items")
    @PreAuthorize("hasAuthority('MENU_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun createMenuItem(@RequestBody dto: MenuItemCreateDto): ApiResponse<MenuItemResponseDto> {
        return ApiResponse.success(catalogService.createMenuItem(dto), "Menu item created successfully")
    }

    @PutMapping("/items/{id}", "/menu-items/{id}")
    @PreAuthorize("hasAuthority('MENU_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun updateMenuItem(
        @PathVariable id: String,
        @RequestBody dto: MenuItemCreateDto
    ): ApiResponse<MenuItemResponseDto> {
        return ApiResponse.success(catalogService.updateMenuItem(id, dto), "Menu item updated successfully")
    }

    @GetMapping("/items/{id}/delete-eligibility", "/menu-items/{id}/delete-eligibility")
    fun checkDeleteEligibility(@PathVariable id: String): ApiResponse<MenuItemDeleteEligibilityDto> {
        return ApiResponse.success(catalogService.checkDeleteEligibility(id))
    }

    @DeleteMapping("/items/{id}", "/menu-items/{id}")
    @PreAuthorize("hasAuthority('MENU_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun deleteMenuItem(@PathVariable id: String): ApiResponse<Unit> {
        catalogService.deleteMenuItem(id)
        return ApiResponse.success(Unit, "Menu item deleted successfully")
    }

    @PostMapping("/modifier-groups")
    @PreAuthorize("hasAuthority('MENU_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun createModifierGroup(@RequestBody group: ModifierGroup): ApiResponse<ModifierGroup> {
        return ApiResponse.success(catalogService.createModifierGroup(group), "Modifier group created successfully")
    }

    @PostMapping("/modifiers")
    @PreAuthorize("hasAuthority('MENU_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun createModifier(@RequestBody modifier: Modifier): ApiResponse<Modifier> {
        return ApiResponse.success(catalogService.createModifier(modifier), "Modifier created successfully")
    }

    @GetMapping("/branch-allocations")
    fun getBranchAllocations(): ApiResponse<BranchAllocationsResponse> {
        return ApiResponse.success(catalogService.getBranchAllocations())
    }

    @PostMapping("/branch-allocations/batch")
    @PreAuthorize("hasAuthority('MENU_MANAGE') or hasAuthority('CATALOG_DISTRIBUTE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun saveBatchBranchAllocations(@RequestBody request: BatchBranchAllocationRequest): ApiResponse<BranchAllocationsResponse> {
        return ApiResponse.success(catalogService.saveBatchBranchAllocations(request), "Branch allocations saved successfully")
    }
}
