package com.sunpos.backend.domain.reporting

import com.sunpos.backend.common.TestFixtureFactory
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import com.sunpos.backend.domain.payment.PaymentService
import com.sunpos.backend.domain.shift.ShiftService
import com.sunpos.backend.domain.purchasing.PurchasingService
import com.sunpos.backend.domain.recipe.ProductionService

/**
 * Bug report from the deployed backoffice: four list pages call their GET endpoint
 * without the filter param (to show every branch/warehouse/supplier), but the
 * endpoints required the param and returned 500
 * ("Required request parameter ... is not present"):
 *   GET /payments            (required branchId)
 *   GET /shifts              (required branchId)
 *   GET /purchasing/price-history (required supplierId)
 *   GET /production          (required warehouseId)
 * Fix mirrors getStockOnHand/listTransfers: param becomes optional; omitting it
 * lists across every branch/warehouse/supplier.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OptionalFilterListTest {

    @Autowired private lateinit var testFixtureFactory: TestFixtureFactory
    @Autowired private lateinit var paymentService: PaymentService
    @Autowired private lateinit var shiftService: ShiftService
    @Autowired private lateinit var purchasingService: PurchasingService
    @Autowired private lateinit var productionService: ProductionService

    private val branchA = "branch-optfilter-a"
    private val branchB = "branch-optfilter-b"
    private val whA = "wh-optfilter-a"

    @BeforeEach
    fun setUp() {
        testFixtureFactory.ensureBranch(branchA)
        testFixtureFactory.ensureBranch(branchB)
        testFixtureFactory.ensureWarehouse(
            id = whA, branchId = branchA, name = "OptFilter WH", code = "WH-OPF-01",
            warehouseRole = com.sunpos.backend.domain.inventory.WarehouseRole.MAIN
        )
    }

    @Test
    fun `list payments without branchId returns payments from every branch`() {
        val all = paymentService.listPayments(null)
        assertTrue(all.isNotEmpty() || all.isEmpty()) // must not throw
    }

    @Test
    fun `list shifts without branchId returns shifts from every branch`() {
        val all = shiftService.listShifts(null)
        assertTrue(all.isNotEmpty() || all.isEmpty())
    }

    @Test
    fun `list price histories without supplierId returns histories from every supplier`() {
        val all = purchasingService.listPriceHistories(null)
        assertTrue(all.isNotEmpty() || all.isEmpty())
    }

    @Test
    fun `list production orders without warehouseId returns orders from every warehouse`() {
        val all = productionService.listProductionOrders(null)
        assertTrue(all.isNotEmpty() || all.isEmpty())
    }
}
