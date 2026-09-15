package com.sunpos.backend.domain.recipe

import com.sunpos.backend.domain.inventory.*
import com.sunpos.backend.domain.order.*
import com.sunpos.backend.domain.promotion.OrderPromotionAllocationRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.time.Instant
import java.util.*

class AutoProvisionedRawStockPureTest {

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
            val set = entities.toSet()
            movements.removeAll(set)
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
        override fun findByBranchId(branchId: String): List<Warehouse> =
            warehouses.values.filter { it.branchId == branchId }
    }

    class FakeOrderRecipeSnapshotRepository : OrderRecipeSnapshotRepository(mock(JdbcTemplate::class.java)) {
        val snapshots = mutableListOf<OrderRecipeSnapshot>()
        override fun save(entity: OrderRecipeSnapshot): OrderRecipeSnapshot {
            snapshots.add(entity)
            return entity
        }
        override fun findByOrderId(orderId: String): List<OrderRecipeSnapshot> =
            snapshots.filter { it.orderId == orderId }
    }

    @Test
    fun testAutoProvisionRawItemAndNegativeStock() {
        val batchRepo = FakeInventoryCloseBatchRepository()
        val recipeRepo = FakeRecipeRepository()
        val recipeIngRepo = FakeRecipeIngredientRepository()
        val stockRepo = FakeInventoryStockRepository()
        val itemRepo = FakeInventoryItemRepository()
        val movementRepo = FakeStockMovementRepository()
        val orderRepo = FakeOrderRepository()
        val orderItemRepo = FakeOrderItemRepository()
        val warehouseRepo = FakeWarehouseRepository()
        val snapshotRepo = FakeOrderRecipeSnapshotRepository()

        // Setup Main Warehouse
        warehouseRepo.warehouses["wh-main"] = Warehouse(
            id = "wh-main",
            branchId = "branch-01",
            name = "คลังใหญ่ สยามสแควร์",
            code = "WH-B01",
            isCentral = false,
            isActive = true
        )

        // Setup Master Raw Item
        itemRepo.items["raw-pork"] = InventoryItem(
            id = "raw-pork",
            sku = "RAW-PORK-01",
            name = "หมูสามชั้นสไลซ์",
            unit = "kg",
            standardCost = BigDecimal("150.0000"),
            itemType = "RAW"
        )

        // Setup Finished Good Recipe
        val recipe = Recipe(
            id = "rec-pork-rice",
            menuItemId = "menu-pork-rice",
            name = "สูตรข้าวหน้าหมูสามชั้น"
        )
        recipeRepo.recipes[recipe.id] = recipe

        recipeIngRepo.ingredients.add(
            RecipeIngredient(
                id = "ing-01",
                recipeId = recipe.id,
                inventoryItemId = "raw-pork",
                quantity = BigDecimal("0.2000"), // 200g per portion
                unit = "kg",
                wastePercentage = BigDecimal.ZERO
            )
        )

        // Setup Order: 10 portions sold (requires 2.0 kg of raw-pork)
        val order = Order(
            id = "ord-01",
            branchId = "branch-01",
            businessDayId = "bday-01",
            status = OrderStatus.COMPLETED
        )
        orderRepo.orders[order.id] = order

        orderItemRepo.items.add(
            OrderItem(
                id = "item-01",
                orderId = order.id,
                menuItemId = "menu-pork-rice",
                quantity = BigDecimal("10.0000")
            )
        )

        // Branch config allowing negative stock
        val invConfigService = mock(InventoryConfigService::class.java)
        org.mockito.Mockito.`when`(invConfigService.getConfigForBranch("branch-01")).thenReturn(
            InventoryBranchConfig(
                branchId = "branch-01",
                stockDeductionMode = StockDeductionMode.EOD,
                allowNegativeStock = true,
                autoCreateStockOnSale = true
            )
        )

        val service = InventoryEodConsumptionService(
            batchRepository = batchRepo,
            recipeRepository = recipeRepo,
            recipeIngredientRepository = recipeIngRepo,
            substituteRepository = null,
            stockRepository = stockRepo,
            itemRepository = itemRepo,
            movementRepository = movementRepo,
            orderRepository = orderRepo,
            orderItemRepository = orderItemRepo,
            orderComboSnapshotRepository = mock(OrderComboSnapshotRepository::class.java),
            orderPromotionAllocationRepository = mock(OrderPromotionAllocationRepository::class.java),
            snapshotRepository = snapshotRepo,
            buffetSessionRepository = mock(BuffetSessionRepository::class.java),
            buffetPackageRecipeRepository = mock(BuffetPackageRecipeRepository::class.java),
            inventoryConfigService = invConfigService,
            warehouseRepository = warehouseRepo
        )

        // Execute EOD Consumption for wh-main
        service.consumeBusinessDaySales("bday-01", "branch-01", "wh-main")

        // 1. Verify Auto-Provisioned Stock record was created with negative balance
        val stockOpt = stockRepo.findByWarehouseIdAndInventoryItemId("wh-main", "raw-pork")
        assertTrue(stockOpt.isPresent, "Inventory stock record must be auto-provisioned")
        val stock = stockOpt.get()
        assertTrue(stock.isAutoProvisioned, "isAutoProvisioned flag must be true")
        assertEquals(BigDecimal("-2.0000"), stock.quantity.setScale(4), "Quantity should be negative -2.0000 kg")
        assertEquals(BigDecimal("150.0000"), stock.weightedAverageCost.setScale(4), "Should inherit standard cost 150.0000")

        // 2. Verify Stock Movement recorded
        val movements = movementRepo.findByWarehouseId("wh-main")
        assertEquals(1, movements.size, "Should record 1 StockMovement")
        val mov = movements.first()
        assertEquals(MovementType.SALE_CONSUMPTION, mov.movementType)
        assertEquals(BigDecimal("-2.0000"), mov.quantity.setScale(4))
        assertEquals(BigDecimal("150.0000"), mov.unitCost.setScale(4))
        assertEquals(BigDecimal("300.0000"), mov.totalCost.setScale(4), "2kg * 150 = 300 total cost")
    }

    @Test
    fun testSemiItemDirectDeductionWithoutSubBomExplosion() {
        val batchRepo = FakeInventoryCloseBatchRepository()
        val recipeRepo = FakeRecipeRepository()
        val recipeIngRepo = FakeRecipeIngredientRepository()
        val stockRepo = FakeInventoryStockRepository()
        val itemRepo = FakeInventoryItemRepository()
        val movementRepo = FakeStockMovementRepository()
        val orderRepo = FakeOrderRepository()
        val orderItemRepo = FakeOrderItemRepository()
        val warehouseRepo = FakeWarehouseRepository()
        val snapshotRepo = FakeOrderRecipeSnapshotRepository()

        warehouseRepo.warehouses["wh-main"] = Warehouse(
            id = "wh-main",
            branchId = "branch-01",
            name = "คลังใหญ่ สยามสแควร์",
            code = "WH-B01",
            isCentral = false,
            isActive = true
        )

        // Master Semi Item produced by Central Kitchen
        itemRepo.items["semi-soup"] = InventoryItem(
            id = "semi-soup",
            sku = "SEMI-SOUP-01",
            name = "น้ำซุปกระดูกหมูเข้มข้น",
            unit = "l",
            standardCost = BigDecimal("80.0000"),
            itemType = "SEMI"
        )

        val recipe = Recipe(
            id = "rec-ramen",
            menuItemId = "menu-ramen",
            name = "สูตรราเมงซุปกระดูกหมู"
        )
        recipeRepo.recipes[recipe.id] = recipe

        recipeIngRepo.ingredients.add(
            RecipeIngredient(
                id = "ing-soup",
                recipeId = recipe.id,
                inventoryItemId = "semi-soup",
                quantity = BigDecimal("0.5000"), // 500 ml per bowl
                unit = "l"
            )
        )

        val order = Order(
            id = "ord-02",
            branchId = "branch-01",
            businessDayId = "bday-02",
            status = OrderStatus.COMPLETED
        )
        orderRepo.orders[order.id] = order

        orderItemRepo.items.add(
            OrderItem(
                id = "item-02",
                orderId = order.id,
                menuItemId = "menu-ramen",
                quantity = BigDecimal("4.0000") // 4 bowls = 2 liters
            )
        )

        val invConfigService = mock(InventoryConfigService::class.java)
        org.mockito.Mockito.`when`(invConfigService.getConfigForBranch("branch-01")).thenReturn(
            InventoryBranchConfig(
                branchId = "branch-01",
                stockDeductionMode = StockDeductionMode.EOD,
                allowNegativeStock = true
            )
        )

        val service = InventoryEodConsumptionService(
            batchRepository = batchRepo,
            recipeRepository = recipeRepo,
            recipeIngredientRepository = recipeIngRepo,
            substituteRepository = null,
            stockRepository = stockRepo,
            itemRepository = itemRepo,
            movementRepository = movementRepo,
            orderRepository = orderRepo,
            orderItemRepository = orderItemRepo,
            orderComboSnapshotRepository = mock(OrderComboSnapshotRepository::class.java),
            orderPromotionAllocationRepository = mock(OrderPromotionAllocationRepository::class.java),
            snapshotRepository = snapshotRepo,
            buffetSessionRepository = mock(BuffetSessionRepository::class.java),
            buffetPackageRecipeRepository = mock(BuffetPackageRecipeRepository::class.java),
            inventoryConfigService = invConfigService,
            warehouseRepository = warehouseRepo
        )

        service.consumeBusinessDaySales("bday-02", "branch-01", "wh-main")

        val stockOpt = stockRepo.findByWarehouseIdAndInventoryItemId("wh-main", "semi-soup")
        assertTrue(stockOpt.isPresent)
        val stock = stockOpt.get()
        assertEquals(BigDecimal("-2.0000"), stock.quantity.setScale(4), "Semi-soup directly deducted to -2.0000 liters")
        assertEquals(BigDecimal("80.0000"), stock.weightedAverageCost.setScale(4), "Inherits standard cost 80.0000")
    }

    @Test
    fun testWasteAndDestroyWarehousesAreBypassed() {
        val batchRepo = FakeInventoryCloseBatchRepository()
        val recipeRepo = FakeRecipeRepository()
        val recipeIngRepo = FakeRecipeIngredientRepository()
        val stockRepo = FakeInventoryStockRepository()
        val itemRepo = FakeInventoryItemRepository()
        val movementRepo = FakeStockMovementRepository()
        val orderRepo = FakeOrderRepository()
        val orderItemRepo = FakeOrderItemRepository()
        val warehouseRepo = FakeWarehouseRepository()

        // Setup Waste warehouse
        warehouseRepo.warehouses["wh-waste"] = Warehouse(
            id = "wh-waste",
            branchId = "branch-01",
            name = "คลังของเสีย (Waste Warehouse)",
            code = "WH-WASTE-01",
            isCentral = false,
            isActive = true
        )

        val invConfigService = mock(InventoryConfigService::class.java)
        org.mockito.Mockito.`when`(invConfigService.getConfigForBranch("branch-01")).thenReturn(
            InventoryBranchConfig(
                branchId = "branch-01",
                stockDeductionMode = StockDeductionMode.EOD,
                allowNegativeStock = true
            )
        )

        val service = InventoryEodConsumptionService(
            batchRepository = batchRepo,
            recipeRepository = recipeRepo,
            recipeIngredientRepository = recipeIngRepo,
            substituteRepository = null,
            stockRepository = stockRepo,
            itemRepository = itemRepo,
            movementRepository = movementRepo,
            orderRepository = orderRepo,
            orderItemRepository = orderItemRepo,
            orderComboSnapshotRepository = mock(OrderComboSnapshotRepository::class.java),
            orderPromotionAllocationRepository = mock(OrderPromotionAllocationRepository::class.java),
            snapshotRepository = mock(OrderRecipeSnapshotRepository::class.java),
            buffetSessionRepository = mock(BuffetSessionRepository::class.java),
            buffetPackageRecipeRepository = mock(BuffetPackageRecipeRepository::class.java),
            inventoryConfigService = invConfigService,
            warehouseRepository = warehouseRepo
        )

        service.consumeBusinessDaySales("bday-03", "branch-01", "wh-waste")

        // Must be bypassed completely: no batch, no stock, no movement
        assertTrue(batchRepo.batches.isEmpty(), "Waste warehouse should have no consumption batch created")
        assertTrue(stockRepo.stocks.isEmpty(), "Waste warehouse should have no stocks deducted")
        assertTrue(movementRepo.movements.isEmpty(), "Waste warehouse should have no movements")
    }
}
