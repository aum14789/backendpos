package com.sunpos.backend

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import java.lang.reflect.Modifier

class SchemaIntegrityTest {

    private fun toSnakeCase(camelCase: String): String {
        return camelCase.replace(Regex("([a-z])([A-Z])")) { "${it.groupValues[1]}_${it.groupValues[2]}" }
            .lowercase()
    }

    @Test
    fun `verify all JdbcRepository entity fields exist in V1 baseline schema DDL`() {
        val resource = ClassPathResource("db/migration/V1__baseline_schema.sql")
        val sqlContent = resource.inputStream.bufferedReader().use { it.readText() }

        // Parse tables and their columns from SQL
        val sqlTables = mutableMapOf<String, MutableSet<String>>()
        val lines = sqlContent.split("\n")
        var currentTable: String? = null

        for (line in lines) {
            val trimmed = line.trim()
            val createMatch = Regex("^CREATE TABLE\\s+(?:IF NOT EXISTS\\s+)?([a-zA-Z0-9_]+)\\s*\\(", RegexOption.IGNORE_CASE).find(trimmed)
            if (createMatch != null) {
                currentTable = createMatch.groupValues[1].lowercase()
                sqlTables[currentTable] = mutableSetOf()
                continue
            }
            if (currentTable != null) {
                if (trimmed.startsWith(");")) {
                    currentTable = null
                    continue
                }
                if (trimmed.startsWith("--") || trimmed.isBlank()) continue
                if (Regex("^(CONSTRAINT|PRIMARY KEY|FOREIGN KEY|UNIQUE|CHECK)", RegexOption.IGNORE_CASE).containsMatchIn(trimmed)) continue
                val colMatch = Regex("^([a-zA-Z0-9_]+)\\s+").find(trimmed)
                if (colMatch != null) {
                    sqlTables[currentTable]?.add(colMatch.groupValues[1].lowercase())
                }
            }
        }

        assertTrue(sqlTables.isNotEmpty(), "Baseline schema should contain tables")

        val entityClasses: List<Pair<Class<*>, String>> = listOf(
            com.sunpos.backend.domain.identity.User::class.java to "users",
            com.sunpos.backend.domain.identity.Role::class.java to "roles",
            com.sunpos.backend.domain.identity.Permission::class.java to "permissions",
            com.sunpos.backend.domain.organization.Company::class.java to "companies",
            com.sunpos.backend.domain.organization.Brand::class.java to "brands",
            com.sunpos.backend.domain.organization.Branch::class.java to "branches",
            com.sunpos.backend.domain.organization.Device::class.java to "devices",
            com.sunpos.backend.domain.organization.ActivationCode::class.java to "activation_codes",
            com.sunpos.backend.domain.organization.NavigationSetting::class.java to "navigation_settings",
            com.sunpos.backend.domain.table.Zone::class.java to "zones",
            com.sunpos.backend.domain.table.TableType::class.java to "table_types",
            com.sunpos.backend.domain.table.RestaurantTable::class.java to "tables",
            com.sunpos.backend.domain.table.TableSession::class.java to "table_sessions",
            com.sunpos.backend.domain.catalog.MenuCategory::class.java to "menu_categories",
            com.sunpos.backend.domain.catalog.MenuItem::class.java to "menu_items",
            com.sunpos.backend.domain.catalog.MenuItemModifierGroup::class.java to "menu_item_modifier_groups",
            com.sunpos.backend.domain.inventory.UnitOfMeasure::class.java to "units_of_measure",
            com.sunpos.backend.domain.inventory.InventoryItem::class.java to "inventory_items",
            com.sunpos.backend.domain.order.Order::class.java to "orders",
            com.sunpos.backend.domain.order.OrderItem::class.java to "order_items",
            com.sunpos.backend.domain.promotion.Promotion::class.java to "promotions",
            com.sunpos.backend.domain.promotion.Coupon::class.java to "coupons",
            com.sunpos.backend.domain.recipe.Recipe::class.java to "recipes",
            com.sunpos.backend.domain.recipe.RecipeIngredientSubstitute::class.java to "recipe_ingredient_substitutes",
            com.sunpos.backend.domain.kitchen.KitchenStation::class.java to "kitchen_stations",
            com.sunpos.backend.domain.shift.CashierShift::class.java to "cashier_shifts"
        )

        val missing = mutableListOf<String>()

        for (item in entityClasses) {
            val entityClass = item.first
            val tableName = item.second
            val tableCols = sqlTables[tableName]
            if (tableCols == null) {
                missing.add("Table '$tableName' for entity ${entityClass.simpleName} not found in SQL!")
                continue
            }

            val fields = entityClass.declaredFields.toList()
                .filter { !Modifier.isStatic(it.modifiers) && !it.name.startsWith("$") }

            for (f in fields) {
                val colName = toSnakeCase(f.name)
                if (!tableCols.contains(colName)) {
                    missing.add("Column '$colName' in table '$tableName' (Entity: ${entityClass.simpleName}) missing in SQL DDL!")
                }
            }
        }

        assertTrue(
            missing.isEmpty(),
            "Schema drift detected:\n" + missing.joinToString("\n")
        )
    }
}
