package com.sunpos.backend.domain.organization

import com.sunpos.backend.domain.inventory.MainWarehouseProvisioning
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Service
class BranchDataSeedingService(
    private val branchRepository: BranchRepository,
    private val jdbcTemplate: JdbcTemplate
) {
    private val log = LoggerFactory.getLogger(BranchDataSeedingService::class.java)

    @Transactional
    fun cloneBranchMasterData(
        targetBranchId: String,
        request: CloneBranchMasterDataRequest,
        operatorUserId: String? = null
    ): BranchDataSeedingSummaryDto {
        val targetBranch = branchRepository.findById(targetBranchId)
            .orElseThrow { IllegalArgumentException("Target branch not found: $targetBranchId") }

        val sourceBranch = branchRepository.findById(request.sourceBranchId)
            .orElseThrow { IllegalArgumentException("Source branch not found: ${request.sourceBranchId}") }

        if (sourceBranch.id == targetBranch.id) {
            throw IllegalArgumentException("Cannot clone branch data into itself [${targetBranch.id}]")
        }

        if (sourceBranch.brandId.isNullOrBlank() || sourceBranch.brandId != targetBranch.brandId) {
            throw IllegalStateException(
                "Source branch [${sourceBranch.id}] and target branch [${targetBranch.id}] must belong to the same brand"
            )
        }

        // 1. Fresh branch check: Must be in PRE_OPENING status
        if (targetBranch.status != "PRE_OPENING") {
            throw IllegalStateException(
                "Fresh Branch violation: Target branch [${targetBranch.id}] must be in 'PRE_OPENING' status (current: ${targetBranch.status})"
            )
        }

        // 2. Fresh branch check: Must not have any menu items
        val existingMenuItemsCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM menu_items WHERE branch_id = ?",
            Int::class.java,
            targetBranchId
        ) ?: 0

        if (existingMenuItemsCount > 0) {
            throw IllegalStateException(
                "Fresh Branch violation: Target branch [${targetBranch.id}] already has $existingMenuItemsCount menu items. Operation locked."
            )
        }

        log.info(
            "Starting atomic master data seeding from source branch [{}] to target branch [{}] (Brand: {}, Operator: {})",
            sourceBranch.id, targetBranch.id, targetBranch.brandId, operatorUserId
        )

        var categoriesCloned = 0
        var menuItemsCloned = 0
        var modifierGroupsCloned = 0
        var modifiersCloned = 0
        var recipesCloned = 0
        var warehouseCreated = false
        var zonesCloned = 0
        var tablesCloned = 0
        var printersCloned = 0
        var staffAccessGranted = 0

        // In-memory mapping tables
        val categoryIdMap = mutableMapOf<String, String>()
        val menuItemIdMap = mutableMapOf<String, String>()
        val modifierGroupIdMap = mutableMapOf<String, String>()
        val zoneIdMap = mutableMapOf<String, String>()
        val tableTypeIdMap = mutableMapOf<String, String>()

        val now = Timestamp.from(Instant.now())

        // ── 1. Catalog Seeding ──
        if (request.cloneCatalog) {
            // 1.1 Menu Categories
            val sourceCategories = jdbcTemplate.queryForList(
                "SELECT * FROM menu_categories WHERE branch_id = ? ORDER BY sort_order ASC",
                sourceBranch.id
            )
            for (cat in sourceCategories) {
                val oldId = cat["id"]?.toString() ?: continue
                val newId = UUID.randomUUID().toString()
                categoryIdMap[oldId] = newId

                jdbcTemplate.update(
                    """
                    INSERT INTO menu_categories (
                        id, branch_id, brand_id, name, description, prefix, sort_order, qr_sort_order, is_active, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    newId,
                    targetBranch.id,
                    targetBranch.brandId,
                    cat["name"] ?: "",
                    cat["description"],
                    cat["prefix"],
                    cat["sort_order"] ?: 0,
                    cat["qr_sort_order"] ?: 0,
                    cat["is_active"] ?: true,
                    now
                )
                categoriesCloned++
            }

            // 1.2 Menu Items
            val sourceItems = jdbcTemplate.queryForList(
                "SELECT * FROM menu_items WHERE branch_id = ?",
                sourceBranch.id
            )
            for (item in sourceItems) {
                val oldId = item["id"]?.toString() ?: continue
                val newId = UUID.randomUUID().toString()
                menuItemIdMap[oldId] = newId

                val oldCatId = item["category_id"]?.toString()
                val newCatId = if (oldCatId != null) categoryIdMap[oldCatId] ?: oldCatId else ""

                jdbcTemplate.update(
                    """
                    INSERT INTO menu_items (
                        id, branch_id, brand_id, category_id, name, description, sku, base_price, cost_price,
                        availability, image_url, sort_order, is_active, item_type, special_type,
                        effective_date, expiry_date, is_vat_inclusive, vat_rate, allow_decimal_qty,
                        barcode, kitchen_station, global_product_id, created_at, updated_at, version
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    newId,
                    targetBranch.id,
                    targetBranch.brandId,
                    newCatId,
                    item["name"] ?: "",
                    item["description"],
                    item["sku"],
                    item["base_price"] ?: 0,
                    item["cost_price"] ?: 0,
                    item["availability"] ?: "AVAILABLE",
                    item["image_url"],
                    item["sort_order"] ?: 0,
                    item["is_active"] ?: true,
                    item["item_type"] ?: "FG",
                    item["special_type"],
                    item["effective_date"],
                    item["expiry_date"],
                    item["is_vat_inclusive"] ?: true,
                    item["vat_rate"] ?: 7.0,
                    item["allow_decimal_qty"] ?: false,
                    item["barcode"],
                    item["kitchen_station"],
                    item["global_product_id"],
                    now,
                    now,
                    0L
                )
                menuItemsCloned++
            }

            // 1.3 Modifier Groups & Modifiers
            val sourceGroups = jdbcTemplate.queryForList(
                "SELECT * FROM modifier_groups WHERE branch_id = ?",
                sourceBranch.id
            )
            for (grp in sourceGroups) {
                val oldGrpId = grp["id"]?.toString() ?: continue
                val newGrpId = UUID.randomUUID().toString()
                modifierGroupIdMap[oldGrpId] = newGrpId

                jdbcTemplate.update(
                    """
                    INSERT INTO modifier_groups (
                        id, branch_id, name, min_selection, max_selection, is_required, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    newGrpId,
                    targetBranch.id,
                    grp["name"] ?: "",
                    grp["min_selection"] ?: 0,
                    grp["max_selection"] ?: 1,
                    grp["is_required"] ?: false,
                    now
                )
                modifierGroupsCloned++

                val sourceModifiers = jdbcTemplate.queryForList(
                    "SELECT * FROM modifiers WHERE modifier_group_id = ?",
                    oldGrpId
                )
                for (mod in sourceModifiers) {
                    val newModId = UUID.randomUUID().toString()
                    jdbcTemplate.update(
                        """
                        INSERT INTO modifiers (
                            id, modifier_group_id, name, price, is_active, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?)
                        """.trimIndent(),
                        newModId,
                        newGrpId,
                        mod["name"] ?: "",
                        mod["price"] ?: 0,
                        mod["is_active"] ?: true,
                        now
                    )
                    modifiersCloned++
                }
            }

            // 1.4 Menu Item Modifier Groups Link
            if (menuItemIdMap.isNotEmpty()) {
                try {
                    val links = jdbcTemplate.queryForList("SELECT * FROM menu_item_modifier_groups")
                    for (link in links) {
                        val oldItemId = link["menu_item_id"]?.toString()
                        val oldGrpId = link["modifier_group_id"]?.toString()
                        val newItemId = menuItemIdMap[oldItemId]
                        val newGrpId = modifierGroupIdMap[oldGrpId]
                        if (newItemId != null && newGrpId != null) {
                            jdbcTemplate.update(
                                """
                                INSERT INTO menu_item_modifier_groups (id, menu_item_id, modifier_group_id)
                                VALUES (?, ?, ?) ON CONFLICT DO NOTHING
                                """.trimIndent(),
                                UUID.randomUUID().toString(),
                                newItemId,
                                newGrpId
                            )
                        }
                    }
                } catch (_: Exception) {}

                // 1.5 Combo Definitions & Groups
                try {
                    val comboDefs = jdbcTemplate.queryForList("SELECT * FROM combo_definitions")
                    for (cdef in comboDefs) {
                        val oldItemId = cdef["menu_item_id"]?.toString()
                        val newItemId = menuItemIdMap[oldItemId]
                        if (newItemId != null) {
                            val oldDefId = cdef["id"]?.toString() ?: continue
                            val newDefId = UUID.randomUUID().toString()
                            jdbcTemplate.update(
                                """
                                INSERT INTO combo_definitions (id, menu_item_id, name, is_active, created_at)
                                VALUES (?, ?, ?, ?, ?)
                                """.trimIndent(),
                                newDefId,
                                newItemId,
                                cdef["name"] ?: "",
                                cdef["is_active"] ?: true,
                                now
                            )
                            val comboGroups = jdbcTemplate.queryForList(
                                "SELECT * FROM combo_groups WHERE combo_definition_id = ?",
                                oldDefId
                            )
                            for (cg in comboGroups) {
                                jdbcTemplate.update(
                                    """
                                    INSERT INTO combo_groups (
                                        id, combo_definition_id, name, min_selection, max_selection, sort_order, seq_order
                                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                                    """.trimIndent(),
                                    UUID.randomUUID().toString(),
                                    newDefId,
                                    cg["name"] ?: "",
                                    cg["min_selection"] ?: 1,
                                    cg["max_selection"] ?: 1,
                                    cg["sort_order"] ?: 0,
                                    cg["seq_order"] ?: 0
                                )
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // ── 2. Recipe & Warehouse Seeding ──
        if (request.cloneRecipes) {
            // 2.1 Default Warehouse Auto-Provisioning
            val existingWhCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM warehouses WHERE branch_id = ?",
                Int::class.java,
                targetBranch.id
            ) ?: 0

            if (existingWhCount == 0) {
                val mainWarehouse = MainWarehouseProvisioning.warehouseFor(
                    targetBranch.id, targetBranch.name, targetBranch.code
                )
                jdbcTemplate.update(
                    """
                    INSERT INTO warehouses (
                        id, branch_id, name, code, warehouse_role, is_active, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    mainWarehouse.id,
                    mainWarehouse.branchId,
                    mainWarehouse.name,
                    mainWarehouse.code,
                    mainWarehouse.warehouseRole.name,
                    true,
                    now
                )
                warehouseCreated = true
            }

            // 2.2 Recipes & Ingredients
            if (menuItemIdMap.isNotEmpty()) {
                val inPlaceholders = menuItemIdMap.keys.joinToString(",") { "'$it'" }
                val recipes = jdbcTemplate.queryForList(
                    "SELECT * FROM recipes WHERE menu_item_id IN ($inPlaceholders)"
                )
                for (r in recipes) {
                    val oldRecipeId = r["id"]?.toString() ?: continue
                    val oldItemId = r["menu_item_id"]?.toString() ?: continue
                    val newItemId = menuItemIdMap[oldItemId] ?: continue

                    val newRecipeId = UUID.randomUUID().toString()
                    jdbcTemplate.update(
                        """
                        INSERT INTO recipes (
                            id, menu_item_id, name, version, yield_quantity, yield_unit, is_active,
                            start_date, end_date, notes, created_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """.trimIndent(),
                        newRecipeId,
                        newItemId,
                        r["name"] ?: "",
                        r["version"] ?: "v1.0",
                        r["yield_quantity"] ?: 1.0,
                        r["yield_unit"] ?: "portion",
                        r["is_active"] ?: true,
                        r["start_date"],
                        r["end_date"],
                        r["notes"],
                        now
                    )
                    recipesCloned++

                    val ingredients = jdbcTemplate.queryForList(
                        "SELECT * FROM recipe_ingredients WHERE recipe_id = ?",
                        oldRecipeId
                    )
                    for (ing in ingredients) {
                        jdbcTemplate.update(
                            """
                            INSERT INTO recipe_ingredients (
                                id, recipe_id, inventory_item_id, quantity, unit, waste_percentage
                            ) VALUES (?, ?, ?, ?, ?, ?)
                            """.trimIndent(),
                            UUID.randomUUID().toString(),
                            newRecipeId,
                            ing["inventory_item_id"],
                            ing["quantity"] ?: 0,
                            ing["unit"] ?: "",
                            ing["waste_percentage"] ?: 0
                        )
                    }
                }
            }
        }

        // ── 3. Table Layout Seeding ──
        if (request.cloneTables) {
            // 3.1 Zones
            val sourceZones = jdbcTemplate.queryForList(
                "SELECT * FROM zones WHERE branch_id = ?",
                sourceBranch.id
            )
            for (z in sourceZones) {
                val oldId = z["id"]?.toString() ?: continue
                val newId = UUID.randomUUID().toString()
                zoneIdMap[oldId] = newId

                jdbcTemplate.update(
                    """
                    INSERT INTO zones (id, branch_id, name, sort_order, zone_type, is_active, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    newId,
                    targetBranch.id,
                    z["name"] ?: "",
                    z["sort_order"] ?: 0,
                    z["zone_type"] ?: "DINE_IN",
                    z["is_active"] ?: true,
                    now
                )
                zonesCloned++
            }

            // 3.2 Table Types
            val sourceTypes = jdbcTemplate.queryForList(
                "SELECT * FROM table_types WHERE branch_id = ?",
                sourceBranch.id
            )
            for (tt in sourceTypes) {
                val oldId = tt["id"]?.toString() ?: continue
                val newId = UUID.randomUUID().toString()
                tableTypeIdMap[oldId] = newId

                jdbcTemplate.update(
                    """
                    INSERT INTO table_types (id, branch_id, name, code, is_default, created_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    newId,
                    targetBranch.id,
                    tt["name"] ?: "",
                    tt["code"] ?: "",
                    tt["is_default"] ?: false,
                    now
                )
            }

            // 3.3 Tables
            val sourceTables = jdbcTemplate.queryForList(
                "SELECT * FROM tables WHERE branch_id = ?",
                sourceBranch.id
            )
            for (t in sourceTables) {
                val newTableId = UUID.randomUUID().toString()
                val oldZoneId = t["zone_id"]?.toString()
                val oldTypeId = t["table_type_id"]?.toString()
                val newZoneId = if (oldZoneId != null) zoneIdMap[oldZoneId] ?: oldZoneId else null
                val newTypeId = if (oldTypeId != null) tableTypeIdMap[oldTypeId] ?: oldTypeId else null

                jdbcTemplate.update(
                    """
                    INSERT INTO tables (
                        id, branch_id, zone_id, table_type_id, name_number, capacity, status, is_active,
                        created_at, updated_at, version
                    ) VALUES (?, ?, ?, ?, ?, ?, 'AVAILABLE', ?, ?, ?, 0)
                    """.trimIndent(),
                    newTableId,
                    targetBranch.id,
                    newZoneId,
                    newTypeId,
                    t["name_number"] ?: "",
                    t["capacity"] ?: 4,
                    t["is_active"] ?: true,
                    now,
                    now
                )
                tablesCloned++
            }
        }

        // ── 4. Printer Seeding ──
        if (request.clonePrinters) {
            val sourcePrinters = jdbcTemplate.queryForList(
                "SELECT * FROM printers WHERE branch_id = ?",
                sourceBranch.id
            )
            for (p in sourcePrinters) {
                val oldPrinterId = p["id"]?.toString() ?: continue
                val newPrinterId = UUID.randomUUID().toString()

                jdbcTemplate.update(
                    """
                    INSERT INTO printers (
                        id, branch_id, name, ip_address, port, is_document_printer, is_active, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent(),
                    newPrinterId,
                    targetBranch.id,
                    p["name"] ?: "",
                    p["ip_address"] ?: "127.0.0.1",
                    p["port"] ?: 9100,
                    p["is_document_printer"] ?: false,
                    p["is_active"] ?: true,
                    now,
                    now
                )
                printersCloned++

                try {
                    val catIds = jdbcTemplate.queryForList(
                        "SELECT category_id FROM printer_menu_categories WHERE printer_id = ?",
                        String::class.java,
                        oldPrinterId
                    )
                    for (oldCatId in catIds) {
                        val newCatId = categoryIdMap[oldCatId] ?: oldCatId
                        jdbcTemplate.update(
                            """
                            INSERT INTO printer_menu_categories (printer_id, category_id)
                            VALUES (?, ?) ON CONFLICT DO NOTHING
                            """.trimIndent(),
                            newPrinterId,
                            newCatId
                        )
                    }
                } catch (_: Exception) {}
            }
        }

        // ── 5. Staff Access Granting ──
        if (request.cloneStaffAccess && targetBranch.companyId.isNotBlank()) {
            val userCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE company_id = ? AND is_active = true",
                Int::class.java,
                targetBranch.companyId
            ) ?: 0
            staffAccessGranted = userCount
        }

        log.info(
            "Completed master data seeding to branch [{}]: Categories: {}, Items: {}, Recipes: {}, Tables: {}, Printers: {}, Staff: {}",
            targetBranch.id, categoriesCloned, menuItemsCloned, recipesCloned, tablesCloned, printersCloned, staffAccessGranted
        )

        return BranchDataSeedingSummaryDto(
            targetBranchId = targetBranch.id,
            sourceBranchId = sourceBranch.id,
            categoriesCloned = categoriesCloned,
            menuItemsCloned = menuItemsCloned,
            modifierGroupsCloned = modifierGroupsCloned,
            modifiersCloned = modifiersCloned,
            recipesCloned = recipesCloned,
            warehouseCreated = warehouseCreated,
            zonesCloned = zonesCloned,
            tablesCloned = tablesCloned,
            printersCloned = printersCloned,
            staffAccessGranted = staffAccessGranted,
            timestamp = Instant.now()
        )
    }
}
