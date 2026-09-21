package com.sunpos.backend.domain.organization

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import org.springframework.jdbc.core.JdbcTemplate
import java.util.*

class BranchDataSeedingServicePureTest {

    class FakeBranchRepository : BranchRepository(mock(JdbcTemplate::class.java)) {
        val branches = mutableMapOf<String, Branch>()
        override fun save(entity: Branch): Branch {
            branches[entity.id] = entity
            return entity
        }
        override fun findById(id: Any): Optional<Branch> = Optional.ofNullable(branches[id.toString()])
    }

    private lateinit var branchRepository: FakeBranchRepository
    private lateinit var jdbcTemplate: JdbcTemplate
    private lateinit var seedingService: BranchDataSeedingService

    @BeforeEach
    fun setUp() {
        branchRepository = FakeBranchRepository()
        jdbcTemplate = mock(JdbcTemplate::class.java)
        seedingService = BranchDataSeedingService(branchRepository, jdbcTemplate)

        val brandId = "brand-sushiya"
        val sourceBranch = Branch(
            id = "branch-source",
            name = "Sushiya Asoke",
            brandId = brandId,
            status = "ACTIVE"
        )
        val targetBranch = Branch(
            id = "branch-target",
            name = "Sushiya Thonglor",
            brandId = brandId,
            status = "PRE_OPENING"
        )
        branchRepository.save(sourceBranch)
        branchRepository.save(targetBranch)

        // Default count of menu items in target branch = 0
        `when`(jdbcTemplate.queryForObject(contains("FROM menu_items WHERE branch_id = ?"), eq(Int::class.java), eq("branch-target")))
            .thenReturn(0)
    }

    @Test
    fun `should reject if target branch not found`() {
        val req = CloneBranchMasterDataRequest(sourceBranchId = "branch-source")
        val ex = assertThrows<IllegalArgumentException> {
            seedingService.cloneBranchMasterData("invalid-target", req)
        }
        assertTrue(ex.message!!.contains("Target branch not found"))
    }

    @Test
    fun `should reject if source branch not found`() {
        val req = CloneBranchMasterDataRequest(sourceBranchId = "invalid-source")
        val ex = assertThrows<IllegalArgumentException> {
            seedingService.cloneBranchMasterData("branch-target", req)
        }
        assertTrue(ex.message!!.contains("Source branch not found"))
    }

    @Test
    fun `should reject if source and target are the same branch`() {
        val req = CloneBranchMasterDataRequest(sourceBranchId = "branch-target")
        val ex = assertThrows<IllegalArgumentException> {
            seedingService.cloneBranchMasterData("branch-target", req)
        }
        assertTrue(ex.message!!.contains("Cannot clone branch data into itself"))
    }

    @Test
    fun `should reject if source and target belong to different brands`() {
        val otherBrandBranch = Branch(
            id = "branch-other-brand",
            name = "Ramen Ari",
            brandId = "brand-ramen",
            status = "ACTIVE"
        )
        branchRepository.save(otherBrandBranch)

        val req = CloneBranchMasterDataRequest(sourceBranchId = "branch-other-brand")
        val ex = assertThrows<IllegalStateException> {
            seedingService.cloneBranchMasterData("branch-target", req)
        }
        assertTrue(ex.message!!.contains("same brand"))
    }

    @Test
    fun `should reject if target branch is not in PRE_OPENING status`() {
        val activeTarget = Branch(
            id = "branch-active-target",
            name = "Sushiya Silom",
            brandId = "brand-sushiya",
            status = "ACTIVE"
        )
        branchRepository.save(activeTarget)

        val req = CloneBranchMasterDataRequest(sourceBranchId = "branch-source")
        val ex = assertThrows<IllegalStateException> {
            seedingService.cloneBranchMasterData("branch-active-target", req)
        }
        assertTrue(ex.message!!.contains("PRE_OPENING"))
    }

    @Test
    fun `should reject if target branch already has menu items`() {
        `when`(jdbcTemplate.queryForObject(contains("FROM menu_items WHERE branch_id = ?"), eq(Int::class.java), eq("branch-target")))
            .thenReturn(5)

        val req = CloneBranchMasterDataRequest(sourceBranchId = "branch-source")
        val ex = assertThrows<IllegalStateException> {
            seedingService.cloneBranchMasterData("branch-target", req)
        }
        assertTrue(ex.message!!.contains("Fresh Branch violation"))
    }

    @Test
    fun `should pass validation and return summary skeleton when all preconditions are satisfied`() {
        val req = CloneBranchMasterDataRequest(
            sourceBranchId = "branch-source",
            cloneCatalog = false,
            cloneRecipes = false,
            cloneTables = false,
            clonePrinters = false,
            cloneStaffAccess = false
        )
        val summary = seedingService.cloneBranchMasterData("branch-target", req, operatorUserId = "superadmin-1")

        assertEquals("branch-target", summary.targetBranchId)
        assertEquals("branch-source", summary.sourceBranchId)
        assertEquals(0, summary.menuItemsCloned)
    }

    @Test
    fun `should clone catalog items and remap foreign keys`() {
        // Mock query for categories
        val catRow = mapOf(
            "id" to "cat-old-1",
            "name" to "Nigiri",
            "description" to "Fresh nigiri sushi",
            "prefix" to "NG",
            "sort_order" to 1,
            "qr_sort_order" to 1,
            "is_active" to true
        )
        `when`(jdbcTemplate.queryForList(contains("FROM menu_categories WHERE branch_id = ?"), eq("branch-source")))
            .thenReturn(listOf(catRow))

        // Mock query for menu items
        val itemRow = mapOf(
            "id" to "item-old-1",
            "category_id" to "cat-old-1",
            "name" to "Salmon Nigiri",
            "description" to "Norwegian salmon",
            "sku" to "NG-001",
            "base_price" to java.math.BigDecimal("120.00"),
            "cost_price" to java.math.BigDecimal("60.00"),
            "availability" to "AVAILABLE",
            "image_url" to null,
            "sort_order" to 1,
            "is_active" to true,
            "item_type" to "FG",
            "special_type" to null,
            "effective_date" to null,
            "expiry_date" to null,
            "is_vat_inclusive" to true,
            "vat_rate" to java.math.BigDecimal("7.00"),
            "allow_decimal_qty" to false,
            "barcode" to null,
            "kitchen_station" to "SUSHI_BAR",
            "global_product_id" to null
        )
        `when`(jdbcTemplate.queryForList(contains("FROM menu_items WHERE branch_id = ?"), eq("branch-source")))
            .thenReturn(listOf(itemRow))

        val req = CloneBranchMasterDataRequest(
            sourceBranchId = "branch-source",
            cloneCatalog = true,
            cloneRecipes = false,
            cloneTables = false,
            clonePrinters = false,
            cloneStaffAccess = false
        )

        val summary = seedingService.cloneBranchMasterData("branch-target", req)

        assertEquals(1, summary.categoriesCloned)
        assertEquals(1, summary.menuItemsCloned)
        verify(jdbcTemplate, atLeastOnce()).queryForList(contains("FROM menu_categories WHERE branch_id = ?"), eq("branch-source"))
        verify(jdbcTemplate, atLeastOnce()).queryForList(contains("FROM menu_items WHERE branch_id = ?"), eq("branch-source"))
    }

    @Test
    fun `should auto-provision warehouse and clone recipes linked to new menu items`() {
        // Mock warehouse check: none exists yet
        `when`(jdbcTemplate.queryForObject(contains("FROM warehouses WHERE branch_id = ?"), eq(Int::class.java), eq("branch-target")))
            .thenReturn(0)

        // Mock catalog
        val itemRow = mapOf(
            "id" to "item-old-1",
            "category_id" to "cat-1",
            "name" to "Salmon Nigiri",
            "base_price" to java.math.BigDecimal("120.00"),
            "cost_price" to java.math.BigDecimal("60.00"),
            "availability" to "AVAILABLE",
            "is_active" to true,
            "item_type" to "FG",
            "is_vat_inclusive" to true,
            "vat_rate" to java.math.BigDecimal("7.00")
        )
        `when`(jdbcTemplate.queryForList(contains("FROM menu_items WHERE branch_id = ?"), eq("branch-source")))
            .thenReturn(listOf(itemRow))

        // Mock recipe
        val recipeRow = mapOf(
            "id" to "recipe-old-1",
            "menu_item_id" to "item-old-1",
            "name" to "Salmon Nigiri Recipe",
            "version" to "v1.0",
            "yield_quantity" to java.math.BigDecimal("1.0000"),
            "yield_unit" to "portion",
            "is_active" to true,
            "start_date" to null,
            "end_date" to null,
            "notes" to "Use fresh salmon",
            "created_at" to java.time.Instant.now()
        )
        `when`(jdbcTemplate.queryForList(contains("FROM recipes WHERE menu_item_id IN")))
            .thenReturn(listOf(recipeRow))

        // Mock recipe ingredients
        val ingRow = mapOf(
            "id" to "ing-old-1",
            "recipe_id" to "recipe-old-1",
            "inventory_item_id" to "inv-salmon-raw",
            "quantity" to java.math.BigDecimal("0.0500"),
            "unit" to "kg",
            "waste_percentage" to java.math.BigDecimal("5.00")
        )
        `when`(jdbcTemplate.queryForList(contains("FROM recipe_ingredients WHERE recipe_id = ?"), eq("recipe-old-1")))
            .thenReturn(listOf(ingRow))

        val req = CloneBranchMasterDataRequest(
            sourceBranchId = "branch-source",
            cloneCatalog = true,
            cloneRecipes = true,
            cloneTables = false,
            clonePrinters = false,
            cloneStaffAccess = false
        )

        val summary = seedingService.cloneBranchMasterData("branch-target", req)

        assertTrue(summary.warehouseCreated)
        assertEquals(1, summary.recipesCloned)
        verify(jdbcTemplate).queryForObject(contains("FROM warehouses WHERE branch_id = ?"), eq(Int::class.java), eq("branch-target"))
        // The provisioned warehouse must carry its role (Spec 0033): a branch that is cloned without
        // one could not close its day.
        val roleCaptor = ArgumentCaptor.forClass(Any::class.java)
        verify(jdbcTemplate).update(
            contains("INSERT INTO warehouses"),
            any(), any(), any(), any(), roleCaptor.capture(), any(), any()
        )
        assertEquals("MAIN", roleCaptor.value)
        verify(jdbcTemplate).queryForList(contains("FROM recipe_ingredients WHERE recipe_id = ?"), eq("recipe-old-1"))
    }

    @Test
    fun `should clone tables and printers with status AVAILABLE`() {
        val zoneRow = mapOf(
            "id" to "zone-old-1",
            "name" to "Indoor",
            "zone_type" to "DINE_IN",
            "sort_order" to 1,
            "is_active" to true
        )
        `when`(jdbcTemplate.queryForList(contains("FROM zones WHERE branch_id = ?"), eq("branch-source")))
            .thenReturn(listOf(zoneRow))

        val tableRow = mapOf(
            "id" to "tbl-old-1",
            "zone_id" to "zone-old-1",
            "table_type_id" to null,
            "name_number" to "T01",
            "capacity" to 4,
            "status" to "OCCUPIED", // old branch was occupied
            "is_active" to true
        )
        `when`(jdbcTemplate.queryForList(contains("FROM tables WHERE branch_id = ?"), eq("branch-source")))
            .thenReturn(listOf(tableRow))

        val printerRow = mapOf(
            "id" to "printer-old-1",
            "name" to "Kitchen Thermal",
            "ip_address" to "192.168.1.200",
            "port" to 9100,
            "is_document_printer" to false,
            "is_active" to true
        )
        `when`(jdbcTemplate.queryForList(contains("FROM printers WHERE branch_id = ?"), eq("branch-source")))
            .thenReturn(listOf(printerRow))

        val req = CloneBranchMasterDataRequest(
            sourceBranchId = "branch-source",
            cloneCatalog = false,
            cloneRecipes = false,
            cloneTables = true,
            clonePrinters = true,
            cloneStaffAccess = false
        )

        val summary = seedingService.cloneBranchMasterData("branch-target", req)

        assertEquals(1, summary.zonesCloned)
        assertEquals(1, summary.tablesCloned)
        assertEquals(1, summary.printersCloned)
        verify(jdbcTemplate).queryForList(contains("FROM zones WHERE branch_id = ?"), eq("branch-source"))
        verify(jdbcTemplate).queryForList(contains("FROM tables WHERE branch_id = ?"), eq("branch-source"))
        verify(jdbcTemplate).queryForList(contains("FROM printers WHERE branch_id = ?"), eq("branch-source"))
    }
}


