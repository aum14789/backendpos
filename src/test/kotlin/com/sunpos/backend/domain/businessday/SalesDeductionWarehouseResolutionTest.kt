package com.sunpos.backend.domain.businessday

import com.sunpos.backend.common.TestFixtureFactory
import com.sunpos.backend.domain.inventory.WarehouseRepository
import com.sunpos.backend.domain.inventory.WarehouseRole
import com.sunpos.backend.domain.organization.ActivationCodeRepository
import com.sunpos.backend.domain.organization.BranchCreateDto
import com.sunpos.backend.domain.organization.BranchRepository
import com.sunpos.backend.domain.organization.BrandRepository
import com.sunpos.backend.domain.organization.CompanyRepository
import com.sunpos.backend.domain.organization.DeviceRepository
import com.sunpos.backend.domain.organization.OrganizationController
import com.sunpos.backend.domain.recipe.InventoryCloseBatchRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles

/**
 * Closing a business day deducts stock from the branch's main warehouse, and fails loudly when the
 * branch has none (Spec 0033 / ticket 04).
 *
 * Deliberately not `@Transactional`: the failure path is only observable when the close really
 * rolls back, and a test that shares the transaction would keep the partial writes. The branches
 * this test creates are removed again in [cleanUp].
 */
@SpringBootTest
@ActiveProfiles("test")
class SalesDeductionWarehouseResolutionTest {

    @Autowired private lateinit var businessDayService: BusinessDayService
    @Autowired private lateinit var testFixtureFactory: TestFixtureFactory
    @Autowired private lateinit var businessDayRepository: BusinessDayRepository
    @Autowired private lateinit var warehouseRepository: WarehouseRepository
    @Autowired private lateinit var batchRepository: InventoryCloseBatchRepository
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired private lateinit var companyRepository: CompanyRepository
    @Autowired private lateinit var brandRepository: BrandRepository
    @Autowired private lateinit var branchRepository: BranchRepository
    @Autowired private lateinit var deviceRepository: DeviceRepository
    @Autowired private lateinit var activationCodeRepository: ActivationCodeRepository

    private val createdBranches = mutableListOf<String>()

    private fun createBranch(id: String): String {
        testFixtureFactory.ensureBranch(id)
        createdBranches.add(id)
        return id
    }

    @AfterEach
    fun cleanUp() {
        // Closing a day also auto-provisions this branch's inventory configuration, so the cleanup
        // walks the dependants before the branch itself.
        createdBranches.forEach { branchId ->
            jdbcTemplate.update("DELETE FROM inventory_close_batches WHERE branch_id = ?", branchId)
            jdbcTemplate.update("DELETE FROM stock_movements WHERE warehouse_id IN (SELECT id FROM warehouses WHERE branch_id = ?)", branchId)
            jdbcTemplate.update("DELETE FROM business_days WHERE branch_id = ?", branchId)
            jdbcTemplate.update("DELETE FROM inventory_branch_configs WHERE branch_id = ?", branchId)
            jdbcTemplate.update("DELETE FROM warehouses WHERE branch_id = ?", branchId)
            jdbcTemplate.update("DELETE FROM branches WHERE id = ?", branchId)
        }
        createdBranches.clear()
    }

    @Test
    fun `closing a day fails loudly when the branch has no main warehouse`() {
        val branchId = createBranch("branch-eod-no-main")
        testFixtureFactory.ensureWarehouse(
            id = "wh-loss", branchId = branchId, name = "Storage Room 1", code = "WH-1", warehouseRole = WarehouseRole.WASTE
        )
        val day = businessDayService.getOrCreateOpenBusinessDay(branchId)
        val movementsBefore = movementCount()

        val failure = assertThrows(IllegalStateException::class.java) {
            businessDayService.closeBusinessDayEod(branchId, "mgr-01")
        }

        assertTrue(
            failure.message?.contains(branchId) == true,
            "ข้อความต้องระบุสาขาที่มีปัญหาให้ผู้ดูแลหาเจอ: ${failure.message}"
        )
        assertEquals(
            BusinessDayStatus.OPEN,
            businessDayRepository.findById(day.id).orElseThrow().status,
            "ปิดวันไม่สำเร็จแล้วต้องไม่เหลือวันขายค้างในสถานะ PROCESSING"
        )
        assertTrue(batchRepository.findByBusinessDayIdAndWarehouseId(day.id, "wh-loss").isEmpty())
        assertEquals(
            movementsBefore,
            movementCount(),
            "ปิดวันล้มเหลวแล้วต้องไม่มีการเคลื่อนไหวสต็อกถูกเขียนเลย"
        )
    }

    private fun movementCount(): Int = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM stock_movements", Int::class.java)!!

    @Test
    fun `closing a day deducts from the role main warehouse even when another warehouse is named like one`() {
        val branchId = createBranch("branch-eod-role")
        // Named exactly like the warehouse the old rule preferred, but its role says loss stock.
        testFixtureFactory.ensureWarehouse(
            id = "wh-loss-b01", branchId = branchId, name = "คลังประจำสาขา สำรอง", code = "WH-B01", warehouseRole = WarehouseRole.WASTE
        )
        testFixtureFactory.ensureWarehouse(
            id = "wh-main-role", branchId = branchId, name = "Back Room", code = "WH-BACK", warehouseRole = WarehouseRole.MAIN
        )
        val day = businessDayService.getOrCreateOpenBusinessDay(branchId)

        val closed = businessDayService.closeBusinessDayEod(branchId, "mgr-01")

        assertEquals(BusinessDayStatus.CLOSED, closed.status)
        assertTrue(
            batchRepository.findByBusinessDayIdAndWarehouseId(day.id, "wh-main-role").isNotEmpty(),
            "ต้องตัดสต็อกจากคลังบทบาท MAIN"
        )
        assertTrue(batchRepository.findByBusinessDayIdAndWarehouseId(day.id, "wh-loss-b01").isEmpty())
    }

    @Test
    fun `a branch created through the API can close its day without manual warehouse setup`() {
        testFixtureFactory.ensureBrand("brand-001", "comp-001")
        val controller = OrganizationController(
            companyRepository, brandRepository, branchRepository, deviceRepository, activationCodeRepository,
            warehouseRepository, org.springframework.test.context.TestContextManager(this.javaClass)
                .testContext.applicationContext.getBean(com.sunpos.backend.domain.organization.BranchLifecycleService::class.java)
        )

        val branch = controller.createBranch(
            BranchCreateDto(companyId = "comp-001", brandId = "brand-001", name = "EOD API Branch", code = "EOD-API-1")
        ).data!!
        createdBranches.add(branch.id)

        val warehouses = warehouseRepository.findByBranchId(branch.id)
        assertEquals(1, warehouses.size, "สาขาใหม่ต้องได้คลังใหญ่มาหนึ่งแห่ง")
        assertEquals(WarehouseRole.MAIN, warehouses.first().warehouseRole)

        val closed = businessDayService.closeBusinessDayEod(branch.id, "mgr-01")
        assertEquals(BusinessDayStatus.CLOSED, closed.status)
    }
}
