package com.sunpos.backend.domain.inventory

import com.sunpos.backend.common.TestFixtureFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional

/**
 * A branch has one operational main warehouse (Spec 0033). The rule lives in the database as
 * uk_warehouses_branch_main_role; this test pins the explanation the operator sees, because a raw
 * constraint violation tells them nothing about what to do next.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class WarehouseRoleAssignmentTest {

    @Autowired private lateinit var inventoryService: InventoryService
    @Autowired private lateinit var warehouseRepository: WarehouseRepository
    @Autowired private lateinit var testFixtureFactory: TestFixtureFactory

    @Test
    fun `a second active main warehouse in the same branch is refused with an explanation`() {
        testFixtureFactory.ensureWarehouse(
            id = "wh-first-main", branchId = "branch-role-dup", name = "Main One", code = "WH-M1",
            warehouseRole = WarehouseRole.MAIN
        )

        val failure = assertThrows(IllegalArgumentException::class.java) {
            inventoryService.createWarehouse(
                Warehouse(
                    id = "wh-second-main", branchId = "branch-role-dup", name = "Main Two", code = "WH-M2",
                    warehouseRole = WarehouseRole.MAIN
                )
            )
        }

        assertTrue(
            failure.message?.contains("Main One") == true,
            "ข้อความต้องบอกว่าคลังไหนกันอยู่: ${failure.message}"
        )
        assertEquals(
            listOf("wh-first-main"),
            warehouseRepository.findByBranchId("branch-role-dup").filter { it.isActive && it.warehouseRole == WarehouseRole.MAIN }.map { it.id },
            "คลังใหญ่แห่งเดิมต้องยังเป็นคลังเดียวที่ใช้งานอยู่"
        )
    }

    @Test
    fun `several waste or destroy warehouses are allowed because only MAIN is exclusive`() {
        val branchId = "branch-role-many-special"
        testFixtureFactory.ensureWarehouse(id = "wh-m1", branchId = branchId, name = "Main", code = "WH-M", warehouseRole = WarehouseRole.MAIN)
        testFixtureFactory.ensureWarehouse(id = "wh-w1", branchId = branchId, name = "Waste 1", code = "WH-W1", warehouseRole = WarehouseRole.WASTE)
        testFixtureFactory.ensureWarehouse(id = "wh-w2", branchId = branchId, name = "Waste 2", code = "WH-W2", warehouseRole = WarehouseRole.WASTE)
        testFixtureFactory.ensureWarehouse(id = "wh-d1", branchId = branchId, name = "Destroy", code = "WH-D1", warehouseRole = WarehouseRole.DESTROY)

        assertEquals(4, warehouseRepository.findByBranchId(branchId).size)
    }

    @Test
    fun `promoting a retired main warehouse again is refused while the current one is active`() {
        val branchId = "branch-role-retired"
        testFixtureFactory.ensureWarehouse(id = "wh-current", branchId = branchId, name = "Current Main", code = "WH-C", warehouseRole = WarehouseRole.MAIN)
        testFixtureFactory.ensureWarehouse(id = "wh-old", branchId = branchId, name = "Old Main", code = "WH-O", warehouseRole = WarehouseRole.WASTE)

        val failure = assertThrows(IllegalArgumentException::class.java) {
            inventoryService.updateWarehouse(
                "wh-old",
                Warehouse(id = "wh-old", branchId = branchId, name = "Old Main", code = "WH-O", warehouseRole = WarehouseRole.MAIN)
            )
        }

        assertTrue(failure.message?.contains("Current Main") == true, failure.message)
        assertEquals(
            WarehouseRole.WASTE,
            warehouseRepository.findById("wh-old").orElseThrow().warehouseRole,
            "การแก้ที่ถูกปฏิเสธต้องไม่ทิ้งข้อมูลไว้ครึ่งทาง"
        )
    }
}
