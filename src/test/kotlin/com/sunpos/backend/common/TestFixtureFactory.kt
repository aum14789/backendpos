package com.sunpos.backend.common

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.util.UUID

@Component
class TestFixtureFactory(private val jdbcTemplate: JdbcTemplate) {

    fun ensureCompany(id: String = "comp-001", name: String = "Test Company", taxId: String = "0105560000001"): String {
        jdbcTemplate.update(
            """
            INSERT INTO companies (id, name, tax_id)
            VALUES (?, ?, ?)
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
            id, name, taxId
        )
        return id
    }

    fun ensureBrand(id: String = "brand-001", companyId: String = "comp-001", name: String = "Test Brand", code: String? = null): String {
        ensureCompany(companyId)
        val brandCode = code ?: if (id == "brand-001") "SHABU" else "BRD-${id.takeLast(8)}"
        jdbcTemplate.update(
            """
            INSERT INTO brands (id, company_id, name, code, is_active)
            VALUES (?, ?, ?, ?, true)
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
            id, companyId, name, brandCode
        )
        return id
    }

    fun ensureBranch(
        id: String = "branch-001",
        companyId: String = "comp-001",
        brandId: String = "brand-001",
        name: String = "Test Branch",
        code: String? = null
    ): String {
        ensureCompany(companyId)
        ensureBrand(brandId, companyId)
        val branchCode = code ?: if (id == "branch-001") "HQ-01" else "BR-${id.takeLast(8)}"
        jdbcTemplate.update(
            """
            INSERT INTO branches (id, company_id, brand_id, name, code, status, is_active)
            VALUES (?, ?, ?, ?, ?, 'OPEN', true)
            ON CONFLICT (id) DO UPDATE SET status = 'OPEN', is_active = true
            """.trimIndent(),
            id, companyId, brandId, name, branchCode
        )
        return id
    }

    fun ensureWarehouse(
        id: String = "wh-branch-001",
        branchId: String = "branch-001",
        name: String = "Test Main Warehouse",
        code: String? = null,
        isCentral: Boolean = false
    ): String {
        ensureBranch(branchId)
        val whCode = code ?: if (id == "wh-branch-001") "WH-B01" else "WH-${id.takeLast(8)}"
        jdbcTemplate.update(
            """
            INSERT INTO warehouses (id, branch_id, name, code, is_central, is_active)
            VALUES (?, ?, ?, ?, ?, true)
            ON CONFLICT (id) DO UPDATE SET is_active = true
            """.trimIndent(),
            id, branchId, name, whCode, isCentral
        )
        return id
    }

    fun ensureUser(
        id: String = "usr-001",
        companyId: String = "comp-001",
        username: String = "testuser",
        branchId: String? = null
    ): String {
        ensureCompany(companyId)
        if (branchId != null) {
            ensureBranch(branchId, companyId)
        }
        jdbcTemplate.update(
            """
            INSERT INTO users (id, company_id, username, password_hash, pin_code, is_active)
            VALUES (?, ?, ?, 'hash', '1234', true)
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
            id, companyId, username
        )
        return id
    }

    fun ensureCustomer(
        id: String = "cust-001",
        companyId: String = "comp-001",
        name: String = "Test Customer",
        phone: String = "0812345678"
    ): String {
        ensureCompany(companyId)
        jdbcTemplate.update(
            """
            INSERT INTO customers (id, company_id, first_name, display_name, status, is_active)
            VALUES (?, ?, ?, ?, 'ACTIVE', true)
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
            id, companyId, name, name
        )
        return id
    }

    fun ensureZone(
        id: String = "zone-01",
        branchId: String = "branch-001",
        name: String = "Dine In Zone"
    ): String {
        ensureBranch(branchId)
        jdbcTemplate.update(
            """
            INSERT INTO zones (id, branch_id, name, is_active)
            VALUES (?, ?, ?, true)
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
            id, branchId, name
        )
        return id
    }

    fun ensureTableType(
        id: String = "type-std",
        branchId: String = "branch-001",
        name: String = "Standard Table"
    ): String {
        ensureBranch(branchId)
        jdbcTemplate.update(
            """
            INSERT INTO table_types (id, branch_id, name, code, is_default)
            VALUES (?, ?, ?, 'STD', true)
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
            id, branchId, name
        )
        return id
    }

    fun ensureTable(
        id: String = "tbl-a01",
        branchId: String = "branch-001",
        nameNumber: String = "A01",
        zoneId: String = "zone-01",
        tableTypeId: String = "type-std"
    ): String {
        ensureBranch(branchId)
        ensureZone(zoneId, branchId)
        ensureTableType(tableTypeId, branchId)
        jdbcTemplate.update(
            """
            INSERT INTO tables (id, branch_id, zone_id, table_type_id, name_number, capacity, status, is_active)
            VALUES (?, ?, ?, ?, ?, 4, 'AVAILABLE', true)
            ON CONFLICT (id) DO UPDATE SET is_active = true
            """.trimIndent(),
            id, branchId, zoneId, tableTypeId, nameNumber
        )
        return id
    }

    fun ensureMenuCategory(
        id: String = "cat-001",
        branchId: String = "branch-001",
        name: String = "Main Dishes"
    ): String {
        ensureBranch(branchId)
        jdbcTemplate.update(
            """
            INSERT INTO menu_categories (id, branch_id, name, is_active)
            VALUES (?, ?, ?, true)
            ON CONFLICT (id) DO UPDATE SET is_active = true
            """.trimIndent(),
            id, branchId, name
        )
        return id
    }

    fun ensureMenuItem(
        id: String = "menu-001",
        branchId: String = "branch-001",
        categoryId: String = "cat-001",
        name: String = "Test Menu Item",
        basePrice: BigDecimal = BigDecimal("100.0000")
    ): String {
        ensureBranch(branchId)
        ensureMenuCategory(categoryId, branchId)
        jdbcTemplate.update(
            """
            INSERT INTO menu_items (id, branch_id, category_id, name, base_price, is_active, availability)
            VALUES (?, ?, ?, ?, ?, true, 'AVAILABLE')
            ON CONFLICT (id) DO UPDATE SET is_active = true
            """.trimIndent(),
            id, branchId, categoryId, name, basePrice
        )
        return id
    }

    fun ensureInventoryItem(
        id: String = "inv-001",
        sku: String = "SKU-001",
        name: String = "Test Raw Material",
        unit: String = "kg",
        baseUnit: String = "g"
    ): String {
        jdbcTemplate.update(
            """
            INSERT INTO inventory_items (id, sku, name, unit, base_unit, is_active)
            VALUES (?, ?, ?, ?, ?, true)
            ON CONFLICT (id) DO UPDATE SET is_active = true
            """.trimIndent(),
            id, sku, name, unit, baseUnit
        )
        return id
    }

    fun ensureSupplier(
        id: String = "sup-001",
        name: String = "Test Supplier",
        code: String = "SUP-01"
    ): String {
        jdbcTemplate.update(
            """
            INSERT INTO suppliers (id, code, name, is_active)
            VALUES (?, ?, ?, true)
            ON CONFLICT (id) DO UPDATE SET is_active = true
            """.trimIndent(),
            id, code, name
        )
        return id
    }

    fun ensureOrder(
        id: String = "ord-001",
        branchId: String = "branch-001",
        orderNumber: String? = null
    ): String {
        ensureBranch(branchId)
        val num = orderNumber ?: "ORD-${id.takeLast(8)}"
        jdbcTemplate.update(
            """
            INSERT INTO orders (id, branch_id, order_number, order_type, channel, status, total_amount)
            VALUES (?, ?, ?, 'DINE_IN', 'POS', 'COMPLETED', 0.0)
            ON CONFLICT (id) DO NOTHING
            """.trimIndent(),
            id, branchId, num
        )
        return id
    }
}
