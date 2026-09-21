package com.sunpos.backend.domain.recipe

import com.sunpos.backend.common.TestFixtureFactory
import com.sunpos.backend.domain.businessday.BusinessDayService
import com.sunpos.backend.domain.businessday.BusinessDayStatus
import com.sunpos.backend.domain.catalog.CatalogService
import com.sunpos.backend.domain.catalog.MenuItemCreateDto
import com.sunpos.backend.domain.inventory.InventoryItem
import com.sunpos.backend.domain.inventory.InventoryService
import com.sunpos.backend.domain.inventory.MovementType
import com.sunpos.backend.domain.inventory.PurchaseReceiveDto
import com.sunpos.backend.domain.inventory.WarehouseRole
import com.sunpos.backend.domain.order.CreateOrderRequest
import com.sunpos.backend.domain.order.OrderItemRequest
import com.sunpos.backend.domain.order.OrderService
import com.sunpos.backend.domain.order.OrderStatus
import com.sunpos.backend.domain.organization.BranchRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * Cascading ingredient consumption: when main ingredient stock is insufficient at EOD,
 * available substitute ingredients are consumed in priority order (including partial stock),
 * and the remaining deficit falls back to the main ingredient (ADR 0032 / Spec 0034, Ticket 02).
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CascadingConsumptionTest {

    @Autowired private lateinit var catalogService: CatalogService
    @Autowired private lateinit var inventoryService: InventoryService
    @Autowired private lateinit var recipeService: RecipeService
    @Autowired private lateinit var orderService: OrderService
    @Autowired private lateinit var businessDayService: BusinessDayService
    @Autowired private lateinit var testFixtureFactory: TestFixtureFactory
    @Autowired private lateinit var branchRepository: BranchRepository

    private val branchId = "branch-cascading-eod"
    private val warehouseId = "wh-cascading-eod"

    @BeforeEach
    fun setUp() {
        testFixtureFactory.ensureBranch(branchId)
        branchRepository.findById(branchId).ifPresent { branch ->
            branch.status = "OPEN"
            branchRepository.save(branch)
        }
        businessDayService.getOrCreateOpenBusinessDay(branchId)
        testFixtureFactory.ensureMenuCategory("cat-cascading-eod", branchId, "Cascading Category")
        testFixtureFactory.ensureWarehouse(
            id = warehouseId,
            branchId = branchId,
            name = "Cascading Warehouse",
            code = "WH-CASCADE-EOD",
            warehouseRole = WarehouseRole.MAIN
        )
    }

    private fun receiveStock(item: InventoryItem, qty: String, unitCost: String = "100.00") {
        inventoryService.processPurchaseReceive(
            PurchaseReceiveDto(
                warehouseId = warehouseId,
                inventoryItemId = item.id,
                quantity = BigDecimal(qty),
                unit = item.unit,
                unitCost = BigDecimal(unitCost)
            )
        )
    }

    private fun createDishWithSubstitutes(
        mainItem: InventoryItem,
        perPortionMain: String,
        substitutes: List<Pair<InventoryItem, String>> = emptyList() // Pair(subItem, perPortion)
    ): String {
        val menuItem = catalogService.createMenuItem(
            MenuItemCreateDto(
                branchId = branchId,
                categoryId = "cat-cascading-eod",
                name = "Cascading Dish ${mainItem.sku}",
                sku = "SKU-${mainItem.sku}",
                basePrice = BigDecimal("120.00")
            )
        )

        val subDtos = substitutes.mapIndexed { index, (subItem, subPortion) ->
            RecipeIngredientSubstituteDto(
                priority = index + 1,
                inventoryItemId = subItem.id,
                quantity = BigDecimal(subPortion),
                unit = subItem.unit
            )
        }

        recipeService.createRecipe(
            CreateRecipeDto(
                menuItemId = menuItem.id,
                name = "Recipe ${mainItem.sku}",
                version = "v1.0",
                ingredients = listOf(
                    RecipeIngredientDto(
                        inventoryItemId = mainItem.id,
                        quantity = BigDecimal(perPortionMain),
                        unit = mainItem.unit,
                        substitutes = subDtos
                    )
                )
            )
        )
        return menuItem.id
    }

    private fun sellDish(menuItemId: String, portions: String) {
        val order = orderService.createOrder(
            CreateOrderRequest(
                branchId = branchId,
                items = listOf(
                    OrderItemRequest(
                        menuItemId = menuItemId,
                        nameSnapshot = "Cascading Dish",
                        unitPriceSnapshot = BigDecimal("120.00"),
                        quantity = BigDecimal(portions)
                    )
                )
            )
        )
        listOf(
            OrderStatus.CONFIRMED,
            OrderStatus.IN_KITCHEN,
            OrderStatus.READY,
            OrderStatus.SERVED,
            OrderStatus.COMPLETED
        ).forEach { orderService.transitionOrderStatus(order.id, it) }
    }

    @Test
    fun `when main ingredient is sufficient it consumes only from main and leaves substitute untouched`() {
        // Main has 10, needed = 5, Sub has 4
        val mainItem = inventoryService.createInventoryItem(
            InventoryItem(sku = "RAW-MAIN-01", name = "Pork Tenderloin", unit = "kg", baseUnit = "g")
        )
        val subItem = inventoryService.createInventoryItem(
            InventoryItem(sku = "RAW-SUB-01", name = "Pork Shoulder", unit = "kg", baseUnit = "g")
        )
        receiveStock(mainItem, qty = "10.0")
        receiveStock(subItem, qty = "4.0")

        val menuItemId = createDishWithSubstitutes(
            mainItem = mainItem,
            perPortionMain = "1.0",
            substitutes = listOf(Pair(subItem, "1.0"))
        )
        sellDish(menuItemId, portions = "5.0")

        val closed = businessDayService.closeBusinessDayEod(branchId, "mgr-01")
        assertEquals(BusinessDayStatus.CLOSED, closed.status)

        val mainStock = inventoryService.getStockOnHand(warehouseId).first { it.inventoryItemId == mainItem.id }
        val subStock = inventoryService.getStockOnHand(warehouseId).first { it.inventoryItemId == subItem.id }

        assertEquals(BigDecimal("5.0000"), mainStock.quantity, "หลักมีพอ ต้องตัดจากหลัก 5 เหลือ 5")
        assertEquals(BigDecimal("4.0000"), subStock.quantity, "รองต้องไม่ถูกแตะเลย")

        val movements = inventoryService.listMovements(warehouseId)
            .filter { it.movementType == MovementType.SALE_CONSUMPTION }
        assertEquals(1, movements.size, "ต้องมีการเคลื่อนไหวเพียง 1 รายการสำหรับของหลัก")
        assertEquals(mainItem.id, movements.first().inventoryItemId)
        assertEquals(BigDecimal("-5.0000"), movements.first().quantity)
    }

    @Test
    fun `when main ingredient is insufficient partial substitute is consumed first then remainder goes negative on main`() {
        // Spec 0034 Example 3: Main has 5, needed = 10, Sub has 4
        // -> Sub cut 4 (ends at 0), Main cut 6 (ends at 5 - 6 = -1)
        val mainItem = inventoryService.createInventoryItem(
            InventoryItem(sku = "RAW-MAIN-02", name = "Ribeye Beef", unit = "kg", baseUnit = "g")
        )
        val subItem = inventoryService.createInventoryItem(
            InventoryItem(sku = "RAW-SUB-02", name = "Sirloin Beef", unit = "kg", baseUnit = "g")
        )
        receiveStock(mainItem, qty = "5.0")
        receiveStock(subItem, qty = "4.0")

        val menuItemId = createDishWithSubstitutes(
            mainItem = mainItem,
            perPortionMain = "1.0",
            substitutes = listOf(Pair(subItem, "1.0"))
        )
        sellDish(menuItemId, portions = "10.0")

        val closed = businessDayService.closeBusinessDayEod(branchId, "mgr-01")
        assertEquals(BusinessDayStatus.CLOSED, closed.status)

        val mainStock = inventoryService.getStockOnHand(warehouseId).first { it.inventoryItemId == mainItem.id }
        val subStock = inventoryService.getStockOnHand(warehouseId).first { it.inventoryItemId == subItem.id }

        assertEquals(BigDecimal("0.0000"), subStock.quantity, "รองถูกตัด 4 จนหมดเกลี้ยงเหลือ 0")
        assertEquals(BigDecimal("-1.0000"), mainStock.quantity, "หลักมี 5 ถูกตัดส่วนที่เหลือ 6 เหลือติดลบ -1")

        val movements = inventoryService.listMovements(warehouseId)
            .filter { it.movementType == MovementType.SALE_CONSUMPTION }
        assertEquals(2, movements.size, "ต้องมี 2 การเคลื่อนไหวแยกตามแหล่งวัตถุดิบ")

        val subMovement = movements.first { it.inventoryItemId == subItem.id }
        val mainMovement = movements.first { it.inventoryItemId == mainItem.id }
        assertEquals(BigDecimal("-4.0000"), subMovement.quantity, "ยอดตัดของรองต้องเป็น -4")
        assertEquals(BigDecimal("-6.0000"), mainMovement.quantity, "ยอดตัดของหลักต้องเป็น -6")
    }

    @Test
    fun `multiple substitutes are consumed in priority order and remainder cuts from main`() {
        // Spec 0034 Example 4: Sub 1 (priority 1) has 2, Sub 2 (priority 2) has 3, Main has 0, needed = 10
        // -> Sub 1 cut 2 (ends at 0), Sub 2 cut 3 (ends at 0), Main cut 5 (ends at -5)
        val mainItem = inventoryService.createInventoryItem(
            InventoryItem(sku = "RAW-MAIN-03", name = "Salmon Fillet", unit = "kg", baseUnit = "g")
        )
        val sub1Item = inventoryService.createInventoryItem(
            InventoryItem(sku = "RAW-SUB-03A", name = "Trout Fillet", unit = "kg", baseUnit = "g")
        )
        val sub2Item = inventoryService.createInventoryItem(
            InventoryItem(sku = "RAW-SUB-03B", name = "Sea Bass Fillet", unit = "kg", baseUnit = "g")
        )
        // Main has 0 stock
        receiveStock(sub1Item, qty = "2.0")
        receiveStock(sub2Item, qty = "3.0")

        val menuItemId = createDishWithSubstitutes(
            mainItem = mainItem,
            perPortionMain = "1.0",
            substitutes = listOf(
                Pair(sub1Item, "1.0"),
                Pair(sub2Item, "1.0")
            )
        )
        sellDish(menuItemId, portions = "10.0")

        val closed = businessDayService.closeBusinessDayEod(branchId, "mgr-01")
        assertEquals(BusinessDayStatus.CLOSED, closed.status)

        val mainStock = inventoryService.getStockOnHand(warehouseId).first { it.inventoryItemId == mainItem.id }
        val sub1Stock = inventoryService.getStockOnHand(warehouseId).first { it.inventoryItemId == sub1Item.id }
        val sub2Stock = inventoryService.getStockOnHand(warehouseId).first { it.inventoryItemId == sub2Item.id }

        assertEquals(BigDecimal("0.0000"), sub1Stock.quantity, "รองอันดับ 1 มี 2 ถูกใช้หมดเหลือ 0")
        assertEquals(BigDecimal("0.0000"), sub2Stock.quantity, "รองอันดับ 2 มี 3 ถูกใช้หมดเหลือ 0")
        assertEquals(BigDecimal("-5.0000"), mainStock.quantity, "ส่วนที่เหลือ 5 ถูกตัดจากหลักที่มี 0 จนเหลือ -5")

        val movements = inventoryService.listMovements(warehouseId)
            .filter { it.movementType == MovementType.SALE_CONSUMPTION }
        assertEquals(3, movements.size, "ต้องมี 3 การเคลื่อนไหวแยกตาม 3 แหล่ง")

        val mSub1 = movements.first { it.inventoryItemId == sub1Item.id }
        val mSub2 = movements.first { it.inventoryItemId == sub2Item.id }
        val mMain = movements.first { it.inventoryItemId == mainItem.id }
        assertEquals(BigDecimal("-2.0000"), mSub1.quantity)
        assertEquals(BigDecimal("-3.0000"), mSub2.quantity)
        assertEquals(BigDecimal("-5.0000"), mMain.quantity)
    }

    @Test
    fun `when substitute has sufficient stock it satisfies the need without cutting main`() {
        // Main has 0, needed = 5, Sub has 8
        // -> Sub cut 5 (ends at 3), Main untouched (ends at 0)
        val mainItem = inventoryService.createInventoryItem(
            InventoryItem(sku = "RAW-MAIN-04", name = "Shrimp Fresh", unit = "kg", baseUnit = "g")
        )
        val subItem = inventoryService.createInventoryItem(
            InventoryItem(sku = "RAW-SUB-04", name = "Shrimp Frozen", unit = "kg", baseUnit = "g")
        )
        receiveStock(subItem, qty = "8.0")

        val menuItemId = createDishWithSubstitutes(
            mainItem = mainItem,
            perPortionMain = "1.0",
            substitutes = listOf(Pair(subItem, "1.0"))
        )
        sellDish(menuItemId, portions = "5.0")

        val closed = businessDayService.closeBusinessDayEod(branchId, "mgr-01")
        assertEquals(BusinessDayStatus.CLOSED, closed.status)

        val subStock = inventoryService.getStockOnHand(warehouseId).first { it.inventoryItemId == subItem.id }
        assertEquals(BigDecimal("3.0000"), subStock.quantity, "รองมี 8 ถูกตัด 5 เหลือ 3")

        val mainStockOpt = inventoryService.getStockOnHand(warehouseId).find { it.inventoryItemId == mainItem.id }
        val mainQty = mainStockOpt?.quantity ?: BigDecimal.ZERO
        assertEquals(BigDecimal.ZERO.setScale(4), mainQty.setScale(4), "ของหลักต้องไม่ถูกตัด")

        val movements = inventoryService.listMovements(warehouseId)
            .filter { it.movementType == MovementType.SALE_CONSUMPTION }
        assertEquals(1, movements.size, "มี 1 การเคลื่อนไหวจากของรอง")
        assertEquals(subItem.id, movements.first().inventoryItemId)
        assertEquals(BigDecimal("-5.0000"), movements.first().quantity)
    }

    @Test
    fun `eod consumption is idempotent and does not duplicate deductions when re-run`() {
        val mainItem = inventoryService.createInventoryItem(
            InventoryItem(sku = "RAW-MAIN-05", name = "Pork Belly", unit = "kg", baseUnit = "g")
        )
        val subItem = inventoryService.createInventoryItem(
            InventoryItem(sku = "RAW-SUB-05", name = "Pork Loin", unit = "kg", baseUnit = "g")
        )
        receiveStock(mainItem, qty = "5.0")
        receiveStock(subItem, qty = "4.0")

        val menuItemId = createDishWithSubstitutes(
            mainItem = mainItem,
            perPortionMain = "1.0",
            substitutes = listOf(Pair(subItem, "1.0"))
        )
        sellDish(menuItemId, portions = "10.0")

        val day = businessDayService.getOrCreateOpenBusinessDay(branchId)
        val eodService = org.springframework.test.util.TestSocketUtils::class.java // dummy trigger or autowired
        // Close first time
        businessDayService.closeBusinessDayEod(branchId, "mgr-01")

        val movementsFirstRun = inventoryService.listMovements(warehouseId)
            .filter { it.movementType == MovementType.SALE_CONSUMPTION }
        assertEquals(2, movementsFirstRun.size)

        // Try calling consumption service directly for the same business day
        val consumptionService = org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor()
        // Run closeBusinessDayEod again (already closed)
        businessDayService.closeBusinessDayEod(branchId, "mgr-01")

        val movementsSecondRun = inventoryService.listMovements(warehouseId)
            .filter { it.movementType == MovementType.SALE_CONSUMPTION }
        assertEquals(2, movementsSecondRun.size, "ต้องไม่ตัดซ้ำหรือบันทึกการเคลื่อนไหวเบิ้ล")

        val mainStock = inventoryService.getStockOnHand(warehouseId).first { it.inventoryItemId == mainItem.id }
        val subStock = inventoryService.getStockOnHand(warehouseId).first { it.inventoryItemId == subItem.id }
        assertEquals(BigDecimal("-1.0000"), mainStock.quantity)
        assertEquals(BigDecimal("0.0000"), subStock.quantity)
    }
}
