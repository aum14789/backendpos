package com.sunpos.backend.domain.catalog

import com.sunpos.backend.common.ApiResponse
import com.sunpos.backend.common.JdbcRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.time.Instant

@Repository
class MenuCategoryRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<MenuCategory>(jdbcTemplate, "menu_categories", MenuCategory::class.java) {
    fun findByBranchIdOrderBySortOrderAsc(branchId: String): List<MenuCategory> =
        findByField("branchId", branchId).sortedBy { it.sortOrder }
}

@Repository
class MenuItemRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<MenuItem>(jdbcTemplate, "menu_items", MenuItem::class.java) {
    fun findByBranchId(branchId: String): List<MenuItem> = findByField("branchId", branchId)
    fun findByCategoryId(categoryId: String): List<MenuItem> = findByField("categoryId", categoryId)
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
    fun findByMenuItemId(menuItemId: String): java.util.Optional<ComboDefinition> = findOneByField("menuItemId", menuItemId)
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
    private val comboChoiceRepository: ComboChoiceRepository
) {
    fun listCategories(branchId: String? = null): List<MenuCategory> {
        return if (!branchId.isNullOrBlank()) {
            categoryRepository.findByBranchIdOrderBySortOrderAsc(branchId)
        } else {
            categoryRepository.findAll().sortedBy { it.sortOrder }
        }
    }

    fun createCategory(category: MenuCategory): MenuCategory = categoryRepository.save(category)

    @Transactional
    fun createMenuItem(dto: MenuItemCreateDto): MenuItemResponseDto {
        val item = MenuItem(
            branchId = dto.branchId,
            categoryId = dto.categoryId,
            name = dto.name,
            description = dto.description,
            sku = dto.sku,
            basePrice = dto.basePrice,
            availability = dto.availability
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

        return getMenuItemDetails(saved.id)
    }

    fun getMenuItemDetails(itemId: String): MenuItemResponseDto {
        val item = itemRepository.findById(itemId).orElseThrow { IllegalArgumentException("Item not found") }
        val modifierGroupMappings = menuItemModifierGroupRepository.findByIdMenuItemId(itemId)

        val groupDtos = modifierGroupMappings.mapNotNull { mapping ->
            modifierGroupRepository.findById(mapping.modifierGroupId).map { group ->
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

        val comboDefOpt = comboDefinitionRepository.findByMenuItemId(itemId)
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

        return MenuItemResponseDto(
            id = item.id,
            branchId = item.branchId,
            categoryId = item.categoryId,
            name = item.name,
            description = item.description,
            sku = item.sku,
            basePrice = item.basePrice,
            availability = item.availability,
            modifierGroups = groupDtos,
            comboDefinition = comboDefDto
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

    @GetMapping("/items/{id}", "/menu-items/{id}")
    fun getMenuItem(@PathVariable id: String): ApiResponse<MenuItemResponseDto> {
        return ApiResponse.success(catalogService.getMenuItemDetails(id))
    }

    @PostMapping("/items", "/menu-items")
    @PreAuthorize("hasAuthority('MENU_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun createMenuItem(@RequestBody dto: MenuItemCreateDto): ApiResponse<MenuItemResponseDto> {
        return ApiResponse.success(catalogService.createMenuItem(dto), "Menu item created successfully")
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
}
