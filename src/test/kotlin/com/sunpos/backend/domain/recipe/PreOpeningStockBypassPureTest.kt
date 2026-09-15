package com.sunpos.backend.domain.recipe

import com.sunpos.backend.domain.inventory.*
import com.sunpos.backend.domain.order.*
import com.sunpos.backend.domain.organization.Branch
import com.sunpos.backend.domain.organization.BranchRepository
import com.sunpos.backend.domain.promotion.OrderPromotionAllocationRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.time.Instant
import java.util.*

class PreOpeningStockBypassPureTest {

    class FakeInventoryCloseBatchRepository : InventoryCloseBatchRepository(mock(JdbcTemplate::class.java)) {
        val batches = mutableMapOf<String, InventoryCloseBatch>()
        override fun save(entity: InventoryCloseBatch): InventoryCloseBatch {
            batches[entity.id] = entity
            return entity
        }
        override fun findByBusinessDayIdAndWarehouseId(businessDayId: String, warehouseId: String): List<InventoryCloseBatch> =
            batches.values.filter { it.businessDayId == businessDayId && it.warehouseId == warehouseId }
        override fun delete(entity: InventoryCloseBatch) {
            batches.remove(entity.id)
        }
    }

    class FakeRecipeRepository : RecipeRepository(mock(JdbcTemplate::class.java)) {
        val recipes = mutableMapOf<String, Recipe>()
        override fun findById(id: Any): Optional<Recipe> = Optional.ofNullable(recipes[id.toString()])
        override fun findByMenuItemIdAndIsActiveTrue(menuItemId: String): Optional<Recipe> =
            Optional.ofNullable(recipes.values.firstOrNull { it.menuItemId == menuItemId && it.isActive })
    }

    class FakeRecipeIngredientRepository : RecipeIngredientRepository(mock(JdbcTemplate::class.java)) {
        val ingredients = mutableListOf<RecipeIngredient>()
        override fun findByRecipeId(recipeId: String): List<RecipeIngredient> =
            ingredients.filter { it.recipeId == recipeId }
    }

    class FakeInventoryStockRepository : InventoryStockRepository(mock(JdbcTemplate::class.java)) {
        val stocks = mutableMapOf<String, InventoryStock>()
        override fun save(entity: InventoryStock): InventoryStock {
            val key = "${entity.warehouseId}:::${entity.inventoryItemId}"
            stocks[key] = entity
            return entity
        }
        override fun findByWarehouseIdAndInventoryItemId(warehouseId: String, inventoryItemId: String): Optional<InventoryStock> =
            Optional.ofNullable(stocks["${warehouseId}:::${inventoryItemId}"])
    }

    class FakeInventoryItemRepository : InventoryItemRepository(mock(JdbcTemplate::class.java)) {
        val items = mutableMapOf<String, InventoryItem>()
        override fun findById(id: Any): Optional<InventoryItem> = Optional.ofNullable(items[id.toString()])
    }

    class FakeStockMovementRepository : StockMovementRepository(mock(JdbcTemplate::class.java)) {
        val movements = mutableListOf<StockMovement>()
        override fun save(entity: StockMovement): StockMovement {
            movements.add(entity)
            return entity
        }
        override fun findByWarehouseId(warehouseId: String): List<StockMovement> =
            movements.filter { it.warehouseId == warehouseId }
        override fun deleteAll(entities: Iterable<StockMovement>) {
            movements.removeAll(entities.toSet())
        }
    }

    class FakeOrderRepository : OrderRepository(mock(JdbcTemplate::class.java)) {
        val orders = mutableMapOf<String, Order>()
        override fun findByBranchId(branchId: String): List<Order> =
            orders.values.filter { it.branchId == branchId }
    }

    class FakeOrderItemRepository : OrderItemRepository(mock(JdbcTemplate::class.java)) {
        val items = mutableListOf<OrderItem>()
        override fun findByOrderId(orderId: String): List<OrderItem> =
            items.filter { it.orderId == orderId }
    }

    class FakeWarehouseRepository : WarehouseRepository(mock(JdbcTemplate::class.java)) {
        val warehouses = mutableMapOf<String, Warehouse>()
        override fun findById(id: Any): Optional<Warehouse> = Optional.ofNullable(warehouses[id.toString()])
    }

    class FakeOrderRecipeSnapshotRepository : OrderRecipeSnapshotRepository(mock(JdbcTemplate::class.java))

    class FakeBuffetSessionRepository : BuffetSessionRepository(mock(JdbcTemplate::class.java))

    class FakeBuffetPackageRecipeRepository : BuffetPackageRecipeRepository(mock(JdbcTemplate::class.java))

    class FakeBranchRepository : BranchRepository(mock(JdbcTemplate::class.java)) {
        val branches = mutableMapOf<String, Branch>()
        override fun save(entity: Branch): Branch {
            branches[entity.id] = entity
            return entity
        }
        override fun findById(id: Any): Optional<Branch> = Optional.ofNullable(branches[id.toString()])
    }

    private lateinit var batchRepo: FakeInventoryCloseBatchRepository
    private lateinit var recipeRepo: FakeRecipeRepository
    private lateinit var ingRepo: FakeRecipeIngredientRepository
    private lateinit var stockRepo: FakeInventoryStockRepository
    private lateinit var itemRepo: FakeInventoryItemRepository
    private lateinit var moveRepo: FakeStockMovementRepository
    private lateinit var orderRepo: FakeOrderRepository
    private lateinit var orderItemRepo: FakeOrderItemRepository
    private lateinit var warehouseRepo: FakeWarehouseRepository
    private lateinit var branchRepo: FakeBranchRepository
    private lateinit var configService: InventoryConfigService
    private lateinit var eodService: InventoryEodConsumptionService

    @BeforeEach
    fun setUp() {
        batchRepo = FakeInventoryCloseBatchRepository()
        recipeRepo = FakeRecipeRepository()
        ingRepo = FakeRecipeIngredientRepository()
        stockRepo = FakeInventoryStockRepository()
        itemRepo = FakeInventoryItemRepository()
        moveRepo = FakeStockMovementRepository()
        orderRepo = FakeOrderRepository()
        orderItemRepo = FakeOrderItemRepository()
        warehouseRepo = FakeWarehouseRepository()
        branchRepo = FakeBranchRepository()

        configService = mock(InventoryConfigService::class.java)
        org.mockito.Mockito.`when`(configService.getConfigForBranch(org.mockito.ArgumentMatchers.anyString()))
            .thenReturn(
                InventoryBranchConfig(
                    branchId = "branch-1",
                    stockDeductionMode = StockDeductionMode.EOD,
                    allowNegativeStock = true
                )
            )

        eodService = InventoryEodConsumptionService(
            batchRepository = batchRepo,
            recipeRepository = recipeRepo,
            recipeIngredientRepository = ingRepo,
            substituteRepository = null,
            stockRepository = stockRepo,
            itemRepository = itemRepo,
            movementRepository = moveRepo,
            orderRepository = orderRepo,
            orderItemRepository = orderItemRepo,
            orderComboSnapshotRepository = mock(OrderComboSnapshotRepository::class.java),
            orderPromotionAllocationRepository = mock(OrderPromotionAllocationRepository::class.java),
            snapshotRepository = FakeOrderRecipeSnapshotRepository(),
            buffetSessionRepository = FakeBuffetSessionRepository(),
            buffetPackageRecipeRepository = FakeBuffetPackageRecipeRepository(),
            inventoryConfigService = configService,
            warehouseRepository = warehouseRepo,
            branchRepository = branchRepo
        )

        // Setup warehouse and raw item
        val wh = Warehouse(id = "wh-main", branchId = "branch-real", name = "Main Kitchen Storage", code = "MAIN")
        warehouseRepo.warehouses[wh.id] = wh

        val beef = InventoryItem(id = "item-beef", sku = "RAW-BEEF-01", name = "Wagyu Beef", unit = "kg", baseUnit = "kg")
        itemRepo.items[beef.id] = beef

        // Initial physical stock: 50.0 kg from Goods Receipt
        stockRepo.save(InventoryStock(warehouseId = wh.id, inventoryItemId = beef.id, quantity = BigDecimal("50.0000")))

        // Recipe: 1 dish = 0.25 kg beef
        val recipe = Recipe(id = "rec-steak", menuItemId = "menu-steak", name = "Wagyu Steak", isActive = true)
        recipeRepo.recipes[recipe.id] = recipe
        ingRepo.ingredients.add(
            RecipeIngredient(
                id = "ing-1",
                recipeId = recipe.id,
                inventoryItemId = beef.id,
                quantity = BigDecimal("0.2500"),
                wastePercentage = BigDecimal.ZERO
            )
        )
    }

    @Test
    fun `test pre-opening branch bypasses recipe stock deduction on sales orders`() {
        // Branch in PRE_OPENING
        val branch = Branch(id = "branch-real", name = "Central Pinklao", status = "PRE_OPENING", isTestBranch = false)
        branchRepo.save(branch)

        // Completed sales order of 10 Wagyu Steaks (should consume 2.5 kg if deducted)
        val order = Order(
            id = "order-test-1",
            branchId = branch.id,
            businessDayId = "bday-001",
            status = OrderStatus.COMPLETED
        )
        orderRepo.orders[order.id] = order
        orderItemRepo.items.add(
            OrderItem(id = "item-1", orderId = order.id, menuItemId = "menu-steak", quantity = BigDecimal("10.0"))
        )

        // Run EOD consumption
        eodService.consumeBusinessDaySales(businessDayId = "bday-001", branchId = branch.id, warehouseId = "wh-main")

        // Assert zero stock movements and stock remains untouched at 50.0 kg!
        val movements = moveRepo.findByWarehouseId("wh-main")
        assertTrue(movements.isEmpty(), "Pre-opening should generate 0 stock consumption movements")

        val currentStock = stockRepo.findByWarehouseIdAndInventoryItemId("wh-main", "item-beef").get()
        assertEquals(BigDecimal("50.0000"), currentStock.quantity)
    }

    @Test
    fun `test active branch performs full recipe stock deduction`() {
        // Branch in ACTIVE status
        val branch = Branch(id = "branch-active", name = "Central World", status = "ACTIVE", isTestBranch = false)
        branchRepo.save(branch)

        val wh = Warehouse(id = "wh-active", branchId = branch.id, name = "Main Storage", code = "MAIN")
        warehouseRepo.warehouses[wh.id] = wh
        stockRepo.save(InventoryStock(warehouseId = wh.id, inventoryItemId = "item-beef", quantity = BigDecimal("50.0000")))

        val order = Order(
            id = "order-active-1",
            branchId = branch.id,
            businessDayId = "bday-002",
            status = OrderStatus.COMPLETED
        )
        orderRepo.orders[order.id] = order
        orderItemRepo.items.add(
            OrderItem(id = "item-act-1", orderId = order.id, menuItemId = "menu-steak", quantity = BigDecimal("10.0"))
        )

        // Run EOD consumption
        eodService.consumeBusinessDaySales(businessDayId = "bday-002", branchId = branch.id, warehouseId = wh.id)

        // Assert recipe stock deduction occurred: 50.0 - 2.5 = 47.5 kg
        val movements = moveRepo.findByWarehouseId(wh.id)
        assertFalse(movements.isEmpty(), "Active branch must generate stock movements")

        val currentStock = stockRepo.findByWarehouseIdAndInventoryItemId(wh.id, "item-beef").get()
        assertEquals(BigDecimal("47.5000"), currentStock.quantity)
    }

    @Test
    fun `test permanent brand test branch performs full recipe stock deduction even in pre-opening status`() {
        // Dedicated Test Branch with isTestBranch = true
        val testBranch = Branch(id = "branch-qa", name = "QA Brand Test Lab", status = "PRE_OPENING", isTestBranch = true)
        branchRepo.save(testBranch)

        val wh = Warehouse(id = "wh-qa", branchId = testBranch.id, name = "QA Storage", code = "MAIN")
        warehouseRepo.warehouses[wh.id] = wh
        stockRepo.save(InventoryStock(warehouseId = wh.id, inventoryItemId = "item-beef", quantity = BigDecimal("10.0000")))

        val order = Order(
            id = "order-qa-1",
            branchId = testBranch.id,
            businessDayId = "bday-003",
            status = OrderStatus.COMPLETED
        )
        orderRepo.orders[order.id] = order
        orderItemRepo.items.add(
            OrderItem(id = "item-qa-1", orderId = order.id, menuItemId = "menu-steak", quantity = BigDecimal("4.0"))
        )

        // Run EOD consumption on test branch
        eodService.consumeBusinessDaySales(businessDayId = "bday-003", branchId = testBranch.id, warehouseId = wh.id)

        // Test branch must deduct stock: 10.0 - (4 * 0.25) = 9.0 kg
        val currentStock = stockRepo.findByWarehouseIdAndInventoryItemId(wh.id, "item-beef").get()
        assertEquals(BigDecimal("9.0000"), currentStock.quantity)
    }
}
