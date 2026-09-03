package com.sunpos.backend.domain.recipe

import com.sunpos.backend.common.JdbcRepository
import org.springframework.jdbc.core.JdbcTemplate
import com.sunpos.backend.domain.inventory.*
import com.sunpos.backend.domain.order.*
import com.sunpos.backend.domain.promotion.OrderPromotionAllocationRepository
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

enum class BatchStatus {
    PROCESSING,
    COMPLETED,
    FAILED
}

class InventoryCloseBatch(
    val id: String = UUID.randomUUID().toString(),
    var businessDayId: String = "",
    var branchId: String = "",
    var warehouseId: String = "",
    var status: BatchStatus = BatchStatus.PROCESSING,
    val startedAt: Instant = Instant.now(),
    var completedAt: Instant? = null,
    var createdBy: String? = null
)

@Repository
class InventoryCloseBatchRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<InventoryCloseBatch>(jdbcTemplate, "inventory_close_batches", InventoryCloseBatch::class.java) {
    fun findByBusinessDayIdAndWarehouseId(businessDayId: String, warehouseId: String): List<InventoryCloseBatch> =
        findByFields(mapOf("businessDayId" to businessDayId, "warehouseId" to warehouseId))
}

@Service
class InventoryEodConsumptionService(
    private val batchRepository: InventoryCloseBatchRepository,
    private val recipeRepository: RecipeRepository,
    private val recipeIngredientRepository: RecipeIngredientRepository,
    private val substituteRepository: RecipeIngredientSubstituteRepository? = null,
    private val stockRepository: InventoryStockRepository,
    private val itemRepository: InventoryItemRepository,
    private val movementRepository: StockMovementRepository,
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val orderComboSnapshotRepository: OrderComboSnapshotRepository,
    private val orderPromotionAllocationRepository: OrderPromotionAllocationRepository,
    private val snapshotRepository: OrderRecipeSnapshotRepository,
    private val buffetSessionRepository: BuffetSessionRepository,
    private val buffetPackageRecipeRepository: BuffetPackageRecipeRepository,
    private val inventoryConfigService: InventoryConfigService
) {
    companion object {
        const val SCALE = 4
        val ROUNDING = RoundingMode.HALF_UP
    }

    private fun resolveCascadingIngredientConsumption(
        warehouseId: String,
        ing: RecipeIngredient,
        soldQuantity: BigDecimal,
        currentStockDeductions: MutableMap<String, BigDecimal>
    ): Pair<String, BigDecimal> {
        val wasteCoeff = BigDecimal.ONE.add(ing.wastePercentage.divide(BigDecimal("100"), SCALE, ROUNDING))
        val mainNeeded = ing.quantity.multiply(wasteCoeff).multiply(soldQuantity).setScale(SCALE, ROUNDING)

        val substitutes = substituteRepository?.findByRecipeIngredientId(ing.id)?.sortedBy { it.priority } ?: emptyList()
        if (substitutes.isEmpty()) {
            return Pair(ing.inventoryItemId, mainNeeded)
        }

        // Check if main ingredient has enough stock
        val mainStockOpt = stockRepository.findByWarehouseIdAndInventoryItemId(warehouseId, ing.inventoryItemId)
        val mainStockAvailable = if (mainStockOpt.isPresent) {
            val allocated = currentStockDeductions[ing.inventoryItemId] ?: BigDecimal.ZERO
            mainStockOpt.get().quantity.subtract(allocated)
        } else {
            BigDecimal.ZERO
        }

        if (mainStockAvailable.compareTo(mainNeeded) >= 0) {
            return Pair(ing.inventoryItemId, mainNeeded)
        }

        // Main is insufficient, evaluate substitutes in order of priority (B -> C -> ...)
        for (sub in substitutes) {
            val subWasteCoeff = BigDecimal.ONE.add(sub.wastePercentage.divide(BigDecimal("100"), SCALE, ROUNDING))
            val subNeeded = sub.quantity.multiply(subWasteCoeff).multiply(soldQuantity).setScale(SCALE, ROUNDING)

            val subStockOpt = stockRepository.findByWarehouseIdAndInventoryItemId(warehouseId, sub.inventoryItemId)
            if (subStockOpt.isPresent) {
                val subAllocated = currentStockDeductions[sub.inventoryItemId] ?: BigDecimal.ZERO
                val subStockAvailable = subStockOpt.get().quantity.subtract(subAllocated)
                if (subStockAvailable.compareTo(subNeeded) >= 0) {
                    return Pair(sub.inventoryItemId, subNeeded)
                }
            }
        }

        // All substitutes exhausted -> Fallback to main ingredient (allowing negative stock)
        return Pair(ing.inventoryItemId, mainNeeded)
    }

    @Transactional
    fun consumeBusinessDaySales(businessDayId: String, branchId: String, warehouseId: String, userId: String? = null) {
        // Check inventory config — if REALTIME mode, skip EOD consumption entirely
        val invConfig = inventoryConfigService.getConfigForBranch(branchId)
        if (invConfig.stockDeductionMode == StockDeductionMode.REALTIME) {
            return
        }

        val existingBatches = batchRepository.findByBusinessDayIdAndWarehouseId(businessDayId, warehouseId)
        if (existingBatches.any { it.status == BatchStatus.COMPLETED }) {
            // Idempotency: Already completed EOD consumption for this business day and warehouse.
            return
        }

        // Clean up any failed or processing attempts to allow a clean retry
        for (batch in existingBatches) {
            if (batch.status != BatchStatus.COMPLETED) {
                val movementsToDelete = movementRepository.findByWarehouseId(warehouseId)
                    .filter { it.referenceType == "INVENTORY_CLOSE_BATCH" && it.referenceId == batch.id }
                movementRepository.deleteAll(movementsToDelete)
                batchRepository.delete(batch)
            }
        }

        val batch = batchRepository.save(
            InventoryCloseBatch(
                businessDayId = businessDayId,
                branchId = branchId,
                warehouseId = warehouseId,
                status = BatchStatus.PROCESSING,
                createdBy = userId
            )
        )

        try {
            // 1. Fetch eligible completed/paid sales orders for this business day
            val orders = orderRepository.findByBranchId(branchId)
                .filter { 
                    it.businessDayId == businessDayId && 
                    it.status == OrderStatus.COMPLETED &&
                    it.status != OrderStatus.CANCELLED &&
                    it.status != OrderStatus.VOIDED
                }

            if (orders.isEmpty()) {
                batch.status = BatchStatus.COMPLETED
                batch.completedAt = Instant.now()
                batchRepository.save(batch)
                return
            }

            // 2a. Aggregate raw ingredient consumption from ORDER LINE ITEMS
            //     Track per-order consumption separately for dedup with buffet headcount
            val orderLineConsumptions = mutableMapOf<String, MutableMap<String, BigDecimal>>() // orderId -> (itemId -> qty)
            val globalIngredientConsumptions = mutableMapOf<String, BigDecimal>()

            for (order in orders) {
                val perOrderConsumption = mutableMapOf<String, BigDecimal>()
                val items = orderItemRepository.findByOrderId(order.id)
                for (item in items) {
                    // Resolve recipe: Prioritize recipe snapshot taken at time of sale
                    val recipe = if (!item.recipeIdSnapshot.isNullOrBlank()) {
                        recipeRepository.findById(item.recipeIdSnapshot!!).orElse(null)
                    } else {
                        recipeRepository.findByMenuItemIdAndIsActiveTrue(item.menuItemId).orElse(null)
                    }

                    if (recipe != null) {
                        // Audit snapshot record
                        if (snapshotRepository.findByOrderId(order.id).none { it.menuItemId == item.menuItemId }) {
                            snapshotRepository.save(
                                OrderRecipeSnapshot(
                                    orderId = order.id,
                                    menuItemId = item.menuItemId,
                                    recipeId = recipe.id,
                                    recipeVersion = recipe.version
                                )
                            )
                        }

                        val ingredients = recipeIngredientRepository.findByRecipeId(recipe.id)
                        for (ing in ingredients) {
                            val (targetItemId, neededQty) = resolveCascadingIngredientConsumption(
                                warehouseId,
                                ing,
                                item.quantity,
                                perOrderConsumption
                            )
                            perOrderConsumption[targetItemId] = (perOrderConsumption[targetItemId] ?: BigDecimal.ZERO).add(neededQty)
                        }
                    }

                    // Consume combo choices if present
                    val comboChoices = orderComboSnapshotRepository.findByOrderItemId(item.id)
                    for (choice in comboChoices) {
                        val choiceRecipe = recipeRepository.findByMenuItemIdAndIsActiveTrue(choice.menuItemId).orElse(null)
                        if (choiceRecipe != null) {
                            val choiceIngredients = recipeIngredientRepository.findByRecipeId(choiceRecipe.id)
                            for (cIng in choiceIngredients) {
                                val (targetItemId, neededQty) = resolveCascadingIngredientConsumption(
                                    warehouseId,
                                    cIng,
                                    item.quantity,
                                    perOrderConsumption
                                )
                                perOrderConsumption[targetItemId] = (perOrderConsumption[targetItemId] ?: BigDecimal.ZERO).add(neededQty)
                            }
                        }
                    }
                }

                // Consume promotional free/reward items for this order
                val promoAllocations = orderPromotionAllocationRepository.findByOrderId(order.id)
                for (alloc in promoAllocations) {
                    val rewardItemId = alloc.rewardMenuItemId
                    if (rewardItemId != null && alloc.freeQuantity > BigDecimal.ZERO) {
                        val rewardRecipe = recipeRepository.findByMenuItemIdAndIsActiveTrue(rewardItemId).orElse(null)
                        if (rewardRecipe != null) {
                            val rewardIngredients = recipeIngredientRepository.findByRecipeId(rewardRecipe.id)
                            for (rIng in rewardIngredients) {
                                val (targetItemId, neededQty) = resolveCascadingIngredientConsumption(
                                    warehouseId,
                                    rIng,
                                    alloc.freeQuantity,
                                    perOrderConsumption
                                )
                                perOrderConsumption[targetItemId] = (perOrderConsumption[targetItemId] ?: BigDecimal.ZERO).add(neededQty)
                            }
                        }
                    }
                }

                orderLineConsumptions[order.id] = perOrderConsumption
            }

            // 2b. Buffet Headcount Consumption (if enabled in config)
            if (invConfig.buffetConsumptionMode == BuffetConsumptionMode.HEADCOUNT_RECIPE) {
                for (order in orders) {
                    if (order.orderType != OrderType.BUFFET) continue

                    val session = buffetSessionRepository.findByOrderId(order.id) ?: continue
                    // Only process closed/expired sessions
                    if (session.status != BuffetSessionStatus.CLOSED && session.status != BuffetSessionStatus.EXPIRED) continue

                    val packageRecipes = buffetPackageRecipeRepository.findByBuffetTierIdAndIsActiveTrue(session.buffetTierId)
                    if (packageRecipes.isEmpty()) continue

                    val headcount = session.adultCount + session.childCount
                    if (headcount <= 0) continue

                    val headcountConsumption = mutableMapOf<String, BigDecimal>()
                    for (pkgRecipe in packageRecipes) {
                        val wasteCoeff = BigDecimal.ONE.add(pkgRecipe.wastePercentage.divide(BigDecimal("100"), SCALE, ROUNDING))
                        val needed = pkgRecipe.quantityPerHead
                            .multiply(BigDecimal(headcount))
                            .multiply(wasteCoeff)
                            .setScale(SCALE, ROUNDING)
                        headcountConsumption[pkgRecipe.inventoryItemId] =
                            (headcountConsumption[pkgRecipe.inventoryItemId] ?: BigDecimal.ZERO).add(needed)
                    }

                    // Dedup: For each ingredient, take MAX(order_line_total, headcount_total)
                    // This prevents double-counting when a buffet order has both QR-ordered items
                    // and self-serve consumption estimated via headcount.
                    val orderLineCons = orderLineConsumptions[order.id] ?: emptyMap()
                    val allIngredientIds = (orderLineCons.keys + headcountConsumption.keys).toSet()

                    val mergedOrderConsumption = mutableMapOf<String, BigDecimal>()
                    for (itemId in allIngredientIds) {
                        val fromLines = orderLineCons[itemId] ?: BigDecimal.ZERO
                        val fromHeadcount = headcountConsumption[itemId] ?: BigDecimal.ZERO
                        mergedOrderConsumption[itemId] = fromLines.max(fromHeadcount)
                    }

                    // Replace this order's consumption with the merged (deduped) version
                    orderLineConsumptions[order.id] = mergedOrderConsumption
                }
            }

            // 2c. Aggregate all per-order consumptions into global totals
            for ((_, perOrderMap) in orderLineConsumptions) {
                for ((itemId, qty) in perOrderMap) {
                    globalIngredientConsumptions[itemId] =
                        (globalIngredientConsumptions[itemId] ?: BigDecimal.ZERO).add(qty)
                }
            }

            // 3. Deduct Inventory Stock (Pessimistic Lock & Correct Item Unit)
            for ((inventoryItemId, totalQtyNeeded) in globalIngredientConsumptions) {
                val stockOpt = stockRepository.findByWarehouseIdAndInventoryItemId(warehouseId, inventoryItemId)
                if (!stockOpt.isPresent) {
                    // Item not stocked in this warehouse (e.g. Bar warehouse vs Kitchen warehouse); skip deduction in this warehouse
                    continue
                }
                val currentStock = stockOpt.get()

                if (!invConfig.allowNegativeStock && currentStock.quantity.compareTo(totalQtyNeeded) < 0) {
                    throw IllegalArgumentException("Insufficient stock for item $inventoryItemId. Available: ${currentStock.quantity}, Required: $totalQtyNeeded")
                }

                // Update stock balance
                currentStock.quantity = currentStock.quantity.subtract(totalQtyNeeded).setScale(SCALE, ROUNDING)
                stockRepository.save(currentStock)

                // Resolve correct unit name from InventoryItem entity
                val rawItem = itemRepository.findById(inventoryItemId).orElse(null)
                val unitName = rawItem?.unit ?: "unit"

                // Create Stock Movement (SALE_CONSUMPTION)
                val movement = StockMovement(
                    warehouseId = warehouseId,
                    inventoryItemId = inventoryItemId,
                    quantity = totalQtyNeeded.negate(),
                    unit = unitName,
                    unitCost = currentStock.weightedAverageCost,
                    totalCost = totalQtyNeeded.multiply(currentStock.weightedAverageCost).setScale(SCALE, ROUNDING),
                    movementType = MovementType.SALE_CONSUMPTION,
                    referenceType = "INVENTORY_CLOSE_BATCH",
                    referenceId = batch.id,
                    businessDayId = businessDayId,
                    createdBy = userId
                )
                movementRepository.save(movement)
            }

            // 4. Commit Batch Status
            batch.status = BatchStatus.COMPLETED
            batch.completedAt = Instant.now()
            batchRepository.save(batch)

        } catch (e: Exception) {
            batch.status = BatchStatus.FAILED
            batchRepository.save(batch)
            throw e
        }
    }
}
