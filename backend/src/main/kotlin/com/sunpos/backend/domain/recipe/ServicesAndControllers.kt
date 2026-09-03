package com.sunpos.backend.domain.recipe

import com.sunpos.backend.common.ApiResponse
import com.sunpos.backend.common.JdbcRepository
import org.springframework.jdbc.core.JdbcTemplate
import com.sunpos.backend.domain.catalog.MenuItemRepository
import com.sunpos.backend.domain.inventory.*
import com.sunpos.backend.domain.order.OrderItemRepository
import com.sunpos.backend.domain.order.OrderRepository
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Optional

@Repository
class RecipeRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<Recipe>(jdbcTemplate, "recipes", Recipe::class.java) {
    fun findByMenuItemId(menuItemId: String): List<Recipe> = findByField("menuItemId", menuItemId)
    fun findByMenuItemIdAndIsActiveTrue(menuItemId: String): Optional<Recipe> {
        val list = findByFields(mapOf("menuItemId" to menuItemId, "isActive" to true))
        return Optional.ofNullable(list.firstOrNull())
    }
}

@Repository
class RecipeIngredientRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<RecipeIngredient>(jdbcTemplate, "recipe_ingredients", RecipeIngredient::class.java) {
    fun findByRecipeId(recipeId: String): List<RecipeIngredient> = findByField("recipeId", recipeId)
}

@Repository
class RecipeIngredientSubstituteRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<RecipeIngredientSubstitute>(jdbcTemplate, "recipe_ingredient_substitutes", RecipeIngredientSubstitute::class.java) {
    fun findByRecipeIngredientId(recipeIngredientId: String): List<RecipeIngredientSubstitute> = findByField("recipeIngredientId", recipeIngredientId)
}

@Repository
class BomRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<Bom>(jdbcTemplate, "boms", Bom::class.java) {
    fun findByFinishedInventoryItemId(finishedInventoryItemId: String): List<Bom> = findByField("finishedInventoryItemId", finishedInventoryItemId)
}

@Repository
class BomItemRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<BomItem>(jdbcTemplate, "bom_items", BomItem::class.java) {
    fun findByBomId(bomId: String): List<BomItem> = findByField("bomId", bomId)
}

@Repository
class ProductionOrderRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<ProductionOrder>(jdbcTemplate, "production_orders", ProductionOrder::class.java) {
    fun findByWarehouseId(warehouseId: String): List<ProductionOrder> = findByField("warehouseId", warehouseId)
}

@Repository
class ProductionOrderItemRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<ProductionOrderItem>(jdbcTemplate, "production_order_items", ProductionOrderItem::class.java) {
    fun findByProductionOrderId(productionOrderId: String): List<ProductionOrderItem> = findByField("productionOrderId", productionOrderId)
}

@Repository
class OrderRecipeSnapshotRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<OrderRecipeSnapshot>(jdbcTemplate, "order_recipe_snapshots", OrderRecipeSnapshot::class.java) {
    fun findByOrderId(orderId: String): List<OrderRecipeSnapshot> = findByField("orderId", orderId)
}

@Service
class RecipeService(
    private val recipeRepository: RecipeRepository,
    private val ingredientRepository: RecipeIngredientRepository,
    private val substituteRepository: RecipeIngredientSubstituteRepository? = null,
    private val bomRepository: BomRepository,
    private val bomItemRepository: BomItemRepository,
    private val menuItemRepository: MenuItemRepository? = null,
    private val inventoryItemRepository: InventoryItemRepository? = null
) {
    @Transactional
    fun createRecipe(dto: CreateRecipeDto): Recipe {
        // Deactivate previous versions
        val activePrev = recipeRepository.findByMenuItemIdAndIsActiveTrue(dto.menuItemId)
        if (activePrev.isPresent) {
            val p = activePrev.get()
            p.isActive = false
            recipeRepository.save(p)
        }

        val recipe = Recipe(
            menuItemId = dto.menuItemId,
            name = dto.name,
            version = dto.version,
            yieldQuantity = dto.yieldQuantity,
            yieldUnit = dto.yieldUnit,
            isActive = true,
            startDate = dto.startDate,
            endDate = dto.endDate
        )
        val saved = recipeRepository.save(recipe)

        for (ingDto in dto.ingredients) {
            val ing = RecipeIngredient(
                recipeId = saved.id,
                inventoryItemId = ingDto.inventoryItemId,
                quantity = ingDto.quantity,
                unit = ingDto.unit,
                wastePercentage = ingDto.wastePercentage
            )
            val savedIng = ingredientRepository.save(ing)

            if (substituteRepository != null && ingDto.substitutes.isNotEmpty()) {
                for (subDto in ingDto.substitutes) {
                    val sub = RecipeIngredientSubstitute(
                        recipeIngredientId = savedIng.id,
                        priority = subDto.priority,
                        inventoryItemId = subDto.inventoryItemId,
                        quantity = subDto.quantity,
                        unit = subDto.unit,
                        wastePercentage = subDto.wastePercentage
                    )
                    substituteRepository.save(sub)
                }
            }
        }
        return saved
    }

    fun listAllRecipes(): List<Recipe> = recipeRepository.findAll()

    fun listAllRecipesDetailed(): List<RecipeDetailedDto> {
        val recipes = recipeRepository.findAll()
        val menuItemsMap = menuItemRepository?.findAll()?.associateBy { it.id } ?: emptyMap()
        val inventoryItemsMap = inventoryItemRepository?.findAll()?.associateBy { it.id } ?: emptyMap()

        return recipes.map { r ->
            val menuItem = menuItemsMap[r.menuItemId]
            val ingredients = ingredientRepository.findByRecipeId(r.id).map { ing ->
                val invItem = inventoryItemsMap[ing.inventoryItemId]
                val substitutes = (substituteRepository?.findByRecipeIngredientId(ing.id) ?: emptyList())
                    .sortedBy { it.priority }
                    .map { sub ->
                        val subInvItem = inventoryItemsMap[sub.inventoryItemId]
                        RecipeIngredientSubstituteWithItemDto(
                            id = sub.id,
                            priority = sub.priority,
                            inventoryItemId = sub.inventoryItemId,
                            inventoryItemName = subInvItem?.name ?: sub.inventoryItemId,
                            inventoryItemSku = subInvItem?.sku ?: "",
                            quantity = sub.quantity,
                            unit = sub.unit,
                            wastePercentage = sub.wastePercentage
                        )
                    }

                RecipeIngredientWithItemDto(
                    id = ing.id,
                    recipeId = ing.recipeId,
                    inventoryItemId = ing.inventoryItemId,
                    inventoryItemName = invItem?.name ?: ing.inventoryItemId,
                    inventoryItemSku = invItem?.sku ?: "",
                    quantity = ing.quantity,
                    unit = ing.unit,
                    wastePercentage = ing.wastePercentage,
                    substitutes = substitutes
                )
            }

            RecipeDetailedDto(
                id = r.id,
                menuItemId = r.menuItemId,
                menuItemName = menuItem?.name ?: "เมนูขาย (${r.menuItemId})",
                menuItemSku = menuItem?.sku ?: "",
                name = r.name,
                version = r.version,
                yieldQuantity = r.yieldQuantity,
                yieldUnit = r.yieldUnit,
                isActive = r.isActive,
                startDate = r.startDate,
                endDate = r.endDate,
                notes = r.notes,
                ingredients = ingredients,
                createdAt = r.createdAt
            )
        }
    }

    @Transactional
    fun toggleActive(recipeId: String): Recipe {
        val recipe = recipeRepository.findById(recipeId).orElseThrow { IllegalArgumentException("Recipe not found") }
        recipe.isActive = !recipe.isActive
        return recipeRepository.save(recipe)
    }

    @Transactional
    fun addSubstitute(ingredientId: String, dto: RecipeIngredientSubstituteDto): RecipeIngredientSubstitute {
        if (substituteRepository == null) throw IllegalStateException("Substitute repository not initialized")
        val existing = substituteRepository.findByRecipeIngredientId(ingredientId)
        val nextPriority = (existing.size + 1).coerceAtLeast(1)
        val sub = RecipeIngredientSubstitute(
            recipeIngredientId = ingredientId,
            priority = nextPriority,
            inventoryItemId = dto.inventoryItemId,
            quantity = dto.quantity,
            unit = dto.unit,
            wastePercentage = dto.wastePercentage
        )
        return substituteRepository.save(sub)
    }

    @Transactional
    fun deleteSubstitute(substituteId: String) {
        if (substituteRepository == null) return
        val subOpt = substituteRepository.findById(substituteId)
        if (subOpt.isPresent) {
            val sub = subOpt.get()
            val ingredientId = sub.recipeIngredientId
            substituteRepository.delete(sub)
            // Re-index remaining substitutes automatically
            val remaining = substituteRepository.findByRecipeIngredientId(ingredientId).sortedBy { it.priority }
            remaining.forEachIndexed { index, rSub ->
                rSub.priority = index + 1
                substituteRepository.save(rSub)
            }
        }
    }

    fun getActiveRecipe(menuItemId: String): Optional<Recipe> = recipeRepository.findByMenuItemIdAndIsActiveTrue(menuItemId)

    fun getRecipeIngredients(recipeId: String): List<RecipeIngredient> = ingredientRepository.findByRecipeId(recipeId)

    @Transactional
    fun createBom(dto: CreateBomDto): Bom {
        val bom = Bom(
            finishedInventoryItemId = dto.finishedInventoryItemId,
            name = dto.name,
            version = dto.version,
            plannedOutputQuantity = dto.plannedOutputQuantity,
            outputUnit = dto.outputUnit,
            isActive = true
        )
        val saved = bomRepository.save(bom)

        for (itemDto in dto.items) {
            val item = BomItem(
                bomId = saved.id,
                rawInventoryItemId = itemDto.rawInventoryItemId,
                quantity = itemDto.quantity,
                unit = itemDto.unit
            )
            bomItemRepository.save(item)
        }
        return saved
    }

    fun listBoms(): List<Bom> = bomRepository.findAll()

    fun getBomItems(bomId: String): List<BomItem> = bomItemRepository.findByBomId(bomId)
}

@Service
class ProductionService(
    private val productionOrderRepository: ProductionOrderRepository,
    private val productionOrderItemRepository: ProductionOrderItemRepository,
    private val bomRepository: BomRepository,
    private val bomItemRepository: BomItemRepository,
    private val stockRepository: InventoryStockRepository,
    private val movementRepository: StockMovementRepository,
    private val wacCalculationService: WacCalculationService
) {
    companion object {
        const val SCALE = 4
        val ROUNDING = RoundingMode.HALF_UP
    }

    @Transactional
    fun createProductionOrder(dto: CreateProductionOrderDto): ProductionOrder {
        val bom = bomRepository.findById(dto.bomId).orElseThrow { IllegalArgumentException("BOM not found") }
        val bomItems = bomItemRepository.findByBomId(dto.bomId)

        val prodNumber = "PRD-${DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.of("UTC")).format(Instant.now())}-${(1000..9999).random()}"
        val ratio = dto.plannedQuantity.divide(bom.plannedOutputQuantity, SCALE, ROUNDING)

        val prodOrder = ProductionOrder(
            warehouseId = dto.warehouseId,
            bomId = dto.bomId,
            productionNumber = prodNumber,
            status = ProductionStatus.APPROVED,
            plannedQuantity = dto.plannedQuantity,
            actualQuantity = dto.plannedQuantity,
            unit = dto.unit,
            createdBy = dto.createdBy
        )
        val savedOrder = productionOrderRepository.save(prodOrder)

        for (bomItem in bomItems) {
            val plannedMatQty = bomItem.quantity.multiply(ratio).setScale(SCALE, ROUNDING)
            val stockOpt = stockRepository.findByWarehouseIdAndInventoryItemId(dto.warehouseId, bomItem.rawInventoryItemId)
            val unitCost = stockOpt.map { it.weightedAverageCost }.orElse(BigDecimal.ZERO)

            val orderItem = ProductionOrderItem(
                productionOrderId = savedOrder.id,
                rawInventoryItemId = bomItem.rawInventoryItemId,
                plannedQty = plannedMatQty,
                actualQty = plannedMatQty,
                unit = bomItem.unit,
                unitCost = unitCost,
                totalCost = plannedMatQty.multiply(unitCost).setScale(SCALE, ROUNDING)
            )
            productionOrderItemRepository.save(orderItem)
        }

        return savedOrder
    }

    @Transactional
    fun startProduction(orderId: String): ProductionOrder {
        val order = productionOrderRepository.findById(orderId).orElseThrow { IllegalArgumentException("Production Order not found") }
        order.status = ProductionStatus.IN_PROGRESS
        order.startedAt = Instant.now()
        return productionOrderRepository.save(order)
    }

    @Transactional
    fun completeProduction(orderId: String, dto: CompleteProductionOrderDto): ProductionOrder {
        val order = productionOrderRepository.findById(orderId).orElseThrow { IllegalArgumentException("Production Order not found") }
        if (order.status == ProductionStatus.COMPLETED) {
            throw IllegalArgumentException("Production Order already completed")
        }

        val bom = bomRepository.findById(order.bomId).orElseThrow { IllegalArgumentException("BOM not found") }
        val items = productionOrderItemRepository.findByProductionOrderId(orderId)

        order.actualQuantity = dto.actualQuantity.setScale(SCALE, ROUNDING)
        val yieldPct = if (order.plannedQuantity.compareTo(BigDecimal.ZERO) > 0) {
            order.actualQuantity.divide(order.plannedQuantity, 4, ROUNDING).multiply(BigDecimal("100")).setScale(2, ROUNDING)
        } else BigDecimal("100.00")
        order.yieldPercentage = yieldPct

        order.laborCost = dto.laborCost.setScale(SCALE, ROUNDING)
        order.packagingCost = dto.packagingCost.setScale(SCALE, ROUNDING)
        order.overheadCost = dto.overheadCost.setScale(SCALE, ROUNDING)

        var totalMatCost = BigDecimal.ZERO

        // 1. Consume Raw Materials (PRODUCTION_OUT)
        for (item in items) {
            val stock = stockRepository.findByWarehouseIdAndInventoryItemId(order.warehouseId, item.rawInventoryItemId)
                .orElseThrow { IllegalArgumentException("Insufficient raw material stock") }

            val itemTotalCost = item.actualQty.multiply(stock.weightedAverageCost).setScale(SCALE, ROUNDING)
            item.unitCost = stock.weightedAverageCost
            item.totalCost = itemTotalCost
            productionOrderItemRepository.save(item)

            totalMatCost = totalMatCost.add(itemTotalCost)

            stock.quantity = stock.quantity.subtract(item.actualQty).setScale(SCALE, ROUNDING)
            stockRepository.save(stock)

            val outMovement = StockMovement(
                warehouseId = order.warehouseId,
                inventoryItemId = item.rawInventoryItemId,
                quantity = item.actualQty.negate(),
                unit = item.unit,
                unitCost = stock.weightedAverageCost,
                totalCost = itemTotalCost,
                movementType = MovementType.PRODUCTION_OUT,
                referenceType = "PRODUCTION_ORDER",
                referenceId = order.id
            )
            movementRepository.save(outMovement)
        }

        order.totalMaterialCost = totalMatCost
        val grandTotalCost = totalMatCost.add(dto.laborCost).add(dto.packagingCost).add(dto.overheadCost).setScale(SCALE, ROUNDING)
        val finishedUnitCost = grandTotalCost.divide(order.actualQuantity, SCALE, ROUNDING)

        // 2. Create Finished Goods Stock IN & Update WAC (PRODUCTION_IN)
        val fgStockOpt = stockRepository.findByWarehouseIdAndInventoryItemId(order.warehouseId, bom.finishedInventoryItemId)
        val fgStock = if (fgStockOpt.isPresent) {
            val s = fgStockOpt.get()
            val newWac = wacCalculationService.calculateNewWac(s.quantity, s.weightedAverageCost, order.actualQuantity, finishedUnitCost)
            s.quantity = s.quantity.add(order.actualQuantity).setScale(SCALE, ROUNDING)
            s.weightedAverageCost = newWac
            s.updatedAt = Instant.now()
            stockRepository.save(s)
        } else {
            val s = InventoryStock(
                warehouseId = order.warehouseId,
                inventoryItemId = bom.finishedInventoryItemId,
                quantity = order.actualQuantity,
                weightedAverageCost = finishedUnitCost
            )
            stockRepository.save(s)
        }

        val inMovement = StockMovement(
            warehouseId = order.warehouseId,
            inventoryItemId = bom.finishedInventoryItemId,
            quantity = order.actualQuantity,
            unit = order.unit,
            unitCost = finishedUnitCost,
            totalCost = grandTotalCost,
            movementType = MovementType.PRODUCTION_IN,
            referenceType = "PRODUCTION_ORDER",
            referenceId = order.id
        )
        movementRepository.save(inMovement)

        order.status = ProductionStatus.COMPLETED
        order.completedAt = Instant.now()
        return productionOrderRepository.save(order)
    }

    fun listProductionOrders(warehouseId: String): List<ProductionOrder> = productionOrderRepository.findByWarehouseId(warehouseId)
}

@Service
class SaleConsumptionService(
    private val inventoryEodConsumptionService: InventoryEodConsumptionService,
    private val orderRepository: OrderRepository,
    private val movementRepository: StockMovementRepository
) {
    @Transactional
    fun processSaleConsumption(orderId: String, warehouseId: String): List<StockMovement> {
        val order = orderRepository.findById(orderId).orElseThrow { IllegalArgumentException("Order not found") }
        inventoryEodConsumptionService.consumeBusinessDaySales(
            businessDayId = order.businessDayId,
            branchId = order.branchId,
            warehouseId = warehouseId,
            userId = "system"
        )
        return movementRepository.findByWarehouseId(warehouseId)
            .filter { it.businessDayId == order.businessDayId }
    }
}

@Service
class TraceabilityService(
    private val movementRepository: StockMovementRepository,
    private val warehouseRepository: WarehouseRepository,
    private val itemRepository: InventoryItemRepository
) {
    fun traceMovementsByItem(inventoryItemId: String): List<StockTraceRecordDto> {
        val movements = movementRepository.findByInventoryItemId(inventoryItemId)
        val whMap = warehouseRepository.findAll().associateBy { it.id }
        val itemOpt = itemRepository.findById(inventoryItemId)

        return movements.map { m ->
            StockTraceRecordDto(
                movementId = m.id,
                timestamp = m.createdAt,
                movementType = m.movementType.name,
                warehouseName = whMap[m.warehouseId]?.name ?: m.warehouseId,
                itemName = itemOpt.map { it.name }.orElse(m.inventoryItemId),
                quantity = m.quantity,
                unit = m.unit,
                unitCost = m.unitCost,
                totalCost = m.totalCost,
                referenceType = m.referenceType,
                referenceId = m.referenceId
            )
        }
    }
}

@RestController
@RequestMapping("/api/v1/recipes")
class RecipeController(
    private val recipeService: RecipeService
) {
    @GetMapping
    fun getAllRecipes(): ApiResponse<List<Recipe>> {
        return ApiResponse.success(recipeService.listAllRecipes())
    }

    @GetMapping("/detailed")
    fun getAllRecipesDetailed(): ApiResponse<List<RecipeDetailedDto>> {
        return ApiResponse.success(recipeService.listAllRecipesDetailed())
    }

    @GetMapping("/{id}/ingredients")
    fun getRecipeIngredients(@PathVariable id: String): ApiResponse<List<RecipeIngredient>> {
        return ApiResponse.success(recipeService.getRecipeIngredients(id))
    }

    @PostMapping
    @PreAuthorize("hasAuthority('STOCK_ADJUST') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun createRecipe(@RequestBody dto: CreateRecipeDto): ApiResponse<Recipe> {
        return ApiResponse.success(recipeService.createRecipe(dto), "Recipe created successfully")
    }

    @PostMapping("/ingredients/{ingredientId}/substitutes")
    @PreAuthorize("hasAuthority('STOCK_ADJUST') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun addSubstitute(
        @PathVariable ingredientId: String,
        @RequestBody dto: RecipeIngredientSubstituteDto
    ): ApiResponse<RecipeIngredientSubstitute> {
        return ApiResponse.success(recipeService.addSubstitute(ingredientId, dto), "Substitute added successfully")
    }

    @DeleteMapping("/substitutes/{substituteId}")
    @PreAuthorize("hasAuthority('STOCK_ADJUST') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun deleteSubstitute(@PathVariable substituteId: String): ApiResponse<Boolean> {
        recipeService.deleteSubstitute(substituteId)
        return ApiResponse.success(true, "Substitute deleted successfully")
    }

    @PutMapping("/{id}/toggle-active")
    @PreAuthorize("hasAuthority('STOCK_ADJUST') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun toggleRecipeActive(@PathVariable id: String): ApiResponse<Recipe> {
        return ApiResponse.success(recipeService.toggleActive(id), "Recipe active status toggled")
    }

    @GetMapping("/menu/{menuItemId}")
    fun getRecipe(@PathVariable menuItemId: String): ApiResponse<Recipe?> {
        return ApiResponse.success(recipeService.getActiveRecipe(menuItemId).orElse(null))
    }

    @PostMapping("/boms")
    @PreAuthorize("hasAuthority('STOCK_ADJUST') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun createBom(@RequestBody dto: CreateBomDto): ApiResponse<Bom> {
        return ApiResponse.success(recipeService.createBom(dto), "BOM created successfully")
    }

    @GetMapping("/boms")
    fun listBoms(): ApiResponse<List<Bom>> {
        return ApiResponse.success(recipeService.listBoms())
    }
}

@RestController
@RequestMapping("/api/v1/production")
class ProductionController(
    private val productionService: ProductionService
) {
    @PostMapping
    @PreAuthorize("hasAuthority('STOCK_ADJUST') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun createOrder(@RequestBody dto: CreateProductionOrderDto): ApiResponse<ProductionOrder> {
        return ApiResponse.success(productionService.createProductionOrder(dto), "Production order created successfully")
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAuthority('STOCK_ADJUST') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun startOrder(@PathVariable id: String): ApiResponse<ProductionOrder> {
        return ApiResponse.success(productionService.startProduction(id), "Production order started")
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAuthority('STOCK_ADJUST') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun completeOrder(@PathVariable id: String, @RequestBody dto: CompleteProductionOrderDto): ApiResponse<ProductionOrder> {
        return ApiResponse.success(productionService.completeProduction(id, dto), "Production order completed, finished goods added")
    }

    @GetMapping
    fun listOrders(@RequestParam warehouseId: String): ApiResponse<List<ProductionOrder>> {
        return ApiResponse.success(productionService.listProductionOrders(warehouseId))
    }
}

@RestController
@RequestMapping("/api/v1/inventory/trace")
class TraceabilityController(
    private val traceabilityService: TraceabilityService
) {
    @GetMapping("/item/{itemId}")
    fun traceItem(@PathVariable itemId: String): ApiResponse<List<StockTraceRecordDto>> {
        return ApiResponse.success(traceabilityService.traceMovementsByItem(itemId))
    }
}
