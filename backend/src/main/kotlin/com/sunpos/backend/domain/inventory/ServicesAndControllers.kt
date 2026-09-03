package com.sunpos.backend.domain.inventory

import com.sunpos.backend.common.ApiResponse
import com.sunpos.backend.common.JdbcRepository
import org.springframework.jdbc.core.JdbcTemplate
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
class WarehouseRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<Warehouse>(jdbcTemplate, "warehouses", Warehouse::class.java) {
    fun findByBranchId(branchId: String): List<Warehouse> = findByField("branchId", branchId)
}

@Repository
class InventoryItemRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<InventoryItem>(jdbcTemplate, "inventory_items", InventoryItem::class.java) {
    fun findBySku(sku: String): Optional<InventoryItem> = findOneByField("sku", sku)
}

@Repository
class InventoryStockRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<InventoryStock>(jdbcTemplate, "inventory_stocks", InventoryStock::class.java) {
    fun findByWarehouseIdAndInventoryItemId(warehouseId: String, inventoryItemId: String): Optional<InventoryStock> {
        val list = findByFields(mapOf("warehouseId" to warehouseId, "inventoryItemId" to inventoryItemId))
        return Optional.ofNullable(list.firstOrNull())
    }
    fun findByWarehouseId(warehouseId: String): List<InventoryStock> = findByField("warehouseId", warehouseId)
}

@Repository
class StockMovementRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<StockMovement>(jdbcTemplate, "stock_movements", StockMovement::class.java) {
    fun findByWarehouseId(warehouseId: String): List<StockMovement> = findByField("warehouseId", warehouseId)
    fun findByInventoryItemId(inventoryItemId: String): List<StockMovement> = findByField("inventoryItemId", inventoryItemId)
}

@Repository
class StockTransferRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<StockTransfer>(jdbcTemplate, "stock_transfers", StockTransfer::class.java) {
    fun findBySourceWarehouseIdOrTargetWarehouseId(sourceWarehouseId: String, targetWarehouseId: String): List<StockTransfer> {
        return findAll().filter { it.sourceWarehouseId == sourceWarehouseId || it.targetWarehouseId == targetWarehouseId }
    }
}

@Repository
class StockTransferItemRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<StockTransferItem>(jdbcTemplate, "stock_transfer_items", StockTransferItem::class.java) {
    fun findByTransferId(transferId: String): List<StockTransferItem> = findByField("transferId", transferId)
}

@Repository
class StockCountRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<StockCount>(jdbcTemplate, "stock_counts", StockCount::class.java) {
    fun findByWarehouseId(warehouseId: String): List<StockCount> = findByField("warehouseId", warehouseId)
}

@Repository
class StockCountItemRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<StockCountItem>(jdbcTemplate, "stock_count_items", StockCountItem::class.java) {
    fun findByCountId(countId: String): List<StockCountItem> = findByField("countId", countId)
}

@Repository
class StockWasteRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<StockWaste>(jdbcTemplate, "stock_wastes", StockWaste::class.java) {
    fun findByWarehouseId(warehouseId: String): List<StockWaste> = findByField("warehouseId", warehouseId)
}

@Repository
class UnitOfMeasureRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<UnitOfMeasure>(jdbcTemplate, "units_of_measure", UnitOfMeasure::class.java) {
    fun findByCode(code: String): Optional<UnitOfMeasure> = findOneByField("code", code)
}

@Service
class InventoryService(
    private val warehouseRepository: WarehouseRepository,
    private val itemRepository: InventoryItemRepository,
    private val unitRepository: UnitOfMeasureRepository,
    private val stockRepository: InventoryStockRepository,
    private val movementRepository: StockMovementRepository,
    private val transferRepository: StockTransferRepository,
    private val transferItemRepository: StockTransferItemRepository,
    private val countRepository: StockCountRepository,
    private val countItemRepository: StockCountItemRepository,
    private val wasteRepository: StockWasteRepository,
    private val wacCalculationService: WacCalculationService
) {
    companion object {
        const val SCALE = 4
        val ROUNDING = RoundingMode.HALF_UP
    }

    fun listWarehouses(branchId: String?): List<Warehouse> {
        return if (branchId.isNullOrBlank()) warehouseRepository.findAll() else warehouseRepository.findByBranchId(branchId)
    }

    fun createWarehouse(wh: Warehouse): Warehouse = warehouseRepository.save(wh)

    fun listInventoryItems(): List<InventoryItem> = itemRepository.findAll()

    fun createInventoryItem(item: InventoryItem): InventoryItem = itemRepository.save(item)

    fun updateInventoryItem(id: String, updated: InventoryItem): InventoryItem {
        val existing = itemRepository.findById(id).orElseGet {
            InventoryItem(id = id)
        }
        existing.sku = updated.sku
        existing.name = updated.name
        existing.categoryName = updated.categoryName
        existing.baseUnit = updated.baseUnit
        existing.receivingUnit = updated.receivingUnit
        existing.receivingUnitFactor = updated.receivingUnitFactor
        existing.dispenseUnit = updated.dispenseUnit
        existing.dispenseUnitFactor = updated.dispenseUnitFactor
        existing.unit = updated.unit
        existing.conversionFactor = updated.conversionFactor
        existing.minStockAlert = updated.minStockAlert
        existing.countFrequency = updated.countFrequency
        existing.countFrequencies = updated.countFrequencies
        existing.brandId = updated.brandId
        existing.isActive = updated.isActive
        return itemRepository.save(existing)
    }

    fun deleteInventoryItem(id: String) {
        itemRepository.deleteById(id)
    }

    fun listUnits(): List<UnitOfMeasure> = unitRepository.findAll()

    fun createUnit(uom: UnitOfMeasure): UnitOfMeasure = unitRepository.save(uom)

    fun updateUnit(id: String, uom: UnitOfMeasure): UnitOfMeasure {
        val existing = unitRepository.findById(id).orElseGet {
            UnitOfMeasure(id = id)
        }
        existing.code = uom.code
        existing.name = uom.name
        existing.category = uom.category
        existing.isBaseUnit = uom.isBaseUnit
        existing.baseUnitCode = uom.baseUnitCode
        existing.conversionFactor = uom.conversionFactor
        existing.description = uom.description
        existing.isActive = uom.isActive
        return unitRepository.save(existing)
    }

    fun deleteUnit(id: String) {
        unitRepository.deleteById(id)
    }

    fun getStockOnHand(warehouseId: String): List<InventoryStock> = stockRepository.findByWarehouseId(warehouseId)

    fun listMovements(warehouseId: String): List<StockMovement> = movementRepository.findByWarehouseId(warehouseId)

    @Transactional
    fun processPurchaseReceive(dto: PurchaseReceiveDto): StockMovement {
        val qty = dto.quantity.setScale(SCALE, ROUNDING)
        val uCost = dto.unitCost.setScale(SCALE, ROUNDING)
        val totCost = qty.multiply(uCost).setScale(SCALE, ROUNDING)

        val stockOpt = stockRepository.findByWarehouseIdAndInventoryItemId(dto.warehouseId, dto.inventoryItemId)
        val stock = if (stockOpt.isPresent) {
            val s = stockOpt.get()
            val newWac = wacCalculationService.calculateNewWac(s.quantity, s.weightedAverageCost, qty, uCost)
            s.quantity = s.quantity.add(qty).setScale(SCALE, ROUNDING)
            s.weightedAverageCost = newWac
            s.updatedAt = Instant.now()
            stockRepository.save(s)
        } else {
            val s = InventoryStock(
                warehouseId = dto.warehouseId,
                inventoryItemId = dto.inventoryItemId,
                quantity = qty,
                weightedAverageCost = uCost
            )
            stockRepository.save(s)
        }

        val movement = StockMovement(
            warehouseId = dto.warehouseId,
            inventoryItemId = dto.inventoryItemId,
            quantity = qty,
            unit = dto.unit,
            unitCost = uCost,
            totalCost = totCost,
            movementType = MovementType.PURCHASE,
            createdBy = dto.createdBy
        )
        return movementRepository.save(movement)
    }

    @Transactional
    fun createTransfer(dto: CreateTransferDto): StockTransfer {
        if (dto.sourceWarehouseId == dto.targetWarehouseId) {
            throw IllegalArgumentException("Source and target warehouse cannot be the same")
        }

        val transferNumber = "TRF-${DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.of("UTC")).format(Instant.now())}-${(1000..9999).random()}"
        val transfer = StockTransfer(
            sourceWarehouseId = dto.sourceWarehouseId,
            targetWarehouseId = dto.targetWarehouseId,
            transferNumber = transferNumber,
            status = TransferStatus.REQUESTED,
            createdBy = dto.createdBy
        )
        val savedTransfer = transferRepository.save(transfer)

        for (itemDto in dto.items) {
            if (itemDto.quantity <= BigDecimal.ZERO) {
                throw IllegalArgumentException("Transfer quantity must be greater than zero")
            }

            val stockOpt = stockRepository.findByWarehouseIdAndInventoryItemId(dto.sourceWarehouseId, itemDto.inventoryItemId)
            val currentCost = stockOpt.map { it.weightedAverageCost }.orElse(BigDecimal.ZERO)

            val item = StockTransferItem(
                transferId = savedTransfer.id,
                inventoryItemId = itemDto.inventoryItemId,
                quantity = itemDto.quantity.setScale(SCALE, ROUNDING),
                unit = itemDto.unit,
                unitCost = currentCost
            )
            transferItemRepository.save(item)
        }

        return savedTransfer
    }

    @Transactional
    fun shipTransfer(transferId: String): StockTransfer {
        val transfer = transferRepository.findById(transferId).orElseThrow { IllegalArgumentException("Transfer not found") }
        if (transfer.status != TransferStatus.REQUESTED && transfer.status != TransferStatus.APPROVED) {
            throw IllegalArgumentException("Cannot ship transfer in status ${transfer.status}")
        }

        val items = transferItemRepository.findByTransferId(transferId)
        for (item in items) {
            val stock = stockRepository.findByWarehouseIdAndInventoryItemId(transfer.sourceWarehouseId, item.inventoryItemId)
                .orElseThrow { IllegalArgumentException("Insufficient stock in source warehouse") }

            stock.quantity = stock.quantity.subtract(item.quantity).setScale(SCALE, ROUNDING)
            stockRepository.save(stock)

            val movement = StockMovement(
                warehouseId = transfer.sourceWarehouseId,
                inventoryItemId = item.inventoryItemId,
                quantity = item.quantity.negate().setScale(SCALE, ROUNDING),
                unit = item.unit,
                unitCost = item.unitCost,
                totalCost = item.quantity.multiply(item.unitCost).setScale(SCALE, ROUNDING),
                movementType = MovementType.TRANSFER_OUT,
                referenceType = "STOCK_TRANSFER",
                referenceId = transfer.id
            )
            movementRepository.save(movement)
        }

        transfer.status = TransferStatus.SHIPPED
        transfer.shippedAt = Instant.now()
        return transferRepository.save(transfer)
    }

    @Transactional
    fun receiveTransfer(transferId: String): StockTransfer {
        val transfer = transferRepository.findById(transferId).orElseThrow { IllegalArgumentException("Transfer not found") }
        if (transfer.status != TransferStatus.SHIPPED) {
            throw IllegalArgumentException("Transfer must be SHIPPED before receiving")
        }

        val items = transferItemRepository.findByTransferId(transferId)
        for (item in items) {
            val stockOpt = stockRepository.findByWarehouseIdAndInventoryItemId(transfer.targetWarehouseId, item.inventoryItemId)
            val stock = if (stockOpt.isPresent) {
                val s = stockOpt.get()
                val newWac = wacCalculationService.calculateNewWac(s.quantity, s.weightedAverageCost, item.quantity, item.unitCost)
                s.quantity = s.quantity.add(item.quantity).setScale(SCALE, ROUNDING)
                s.weightedAverageCost = newWac
                s.updatedAt = Instant.now()
                stockRepository.save(s)
            } else {
                val s = InventoryStock(
                    warehouseId = transfer.targetWarehouseId,
                    inventoryItemId = item.inventoryItemId,
                    quantity = item.quantity,
                    weightedAverageCost = item.unitCost
                )
                stockRepository.save(s)
            }

            val movement = StockMovement(
                warehouseId = transfer.targetWarehouseId,
                inventoryItemId = item.inventoryItemId,
                quantity = item.quantity,
                unit = item.unit,
                unitCost = item.unitCost,
                totalCost = item.quantity.multiply(item.unitCost).setScale(SCALE, ROUNDING),
                movementType = MovementType.TRANSFER_IN,
                referenceType = "STOCK_TRANSFER",
                referenceId = transfer.id
            )
            movementRepository.save(movement)
        }

        transfer.status = TransferStatus.RECEIVED
        transfer.receivedAt = Instant.now()
        return transferRepository.save(transfer)
    }

    @Transactional
    fun recordStockCountAndAdjust(dto: StockCountCreateDto): StockCount {
        val countNumber = "CNT-${DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.of("UTC")).format(Instant.now())}-${(1000..9999).random()}"
        val count = StockCount(
            warehouseId = dto.warehouseId,
            countNumber = countNumber,
            status = CountStatus.APPROVED,
            approvedBy = dto.approvedBy,
            notes = dto.notes
        )
        val savedCount = countRepository.save(count)

        for (itemDto in dto.items) {
            val stockOpt = stockRepository.findByWarehouseIdAndInventoryItemId(dto.warehouseId, itemDto.inventoryItemId)
            val systemQty = stockOpt.map { it.quantity }.orElse(BigDecimal.ZERO)
            val currentCost = stockOpt.map { it.weightedAverageCost }.orElse(BigDecimal.ZERO)

            val actual = itemDto.actualQty.setScale(SCALE, ROUNDING)
            val variance = actual.subtract(systemQty).setScale(SCALE, ROUNDING)

            val countItem = StockCountItem(
                countId = savedCount.id,
                inventoryItemId = itemDto.inventoryItemId,
                systemQty = systemQty,
                actualQty = actual,
                varianceQty = variance,
                unitCost = currentCost
            )
            countItemRepository.save(countItem)

            // Resolve real unit of InventoryItem
            val realUnit = itemRepository.findById(itemDto.inventoryItemId).map { it.unit }.orElse("unit")

            // Update Stock Quantity & Record Movement ADJUSTMENT if variance != 0
            if (variance.compareTo(BigDecimal.ZERO) != 0) {
                val stock = if (stockOpt.isPresent) stockOpt.get() else InventoryStock(warehouseId = dto.warehouseId, inventoryItemId = itemDto.inventoryItemId)
                stock.quantity = actual
                stock.updatedAt = Instant.now()
                stockRepository.save(stock)

                val movement = StockMovement(
                    warehouseId = dto.warehouseId,
                    inventoryItemId = itemDto.inventoryItemId,
                    quantity = variance,
                    unit = realUnit,
                    unitCost = currentCost,
                    totalCost = variance.abs().multiply(currentCost).setScale(SCALE, ROUNDING),
                    movementType = MovementType.ADJUSTMENT,
                    referenceType = "STOCK_COUNT",
                    referenceId = savedCount.id
                )
                movementRepository.save(movement)
            }
        }

        return savedCount
    }

    @Transactional
    fun recordWaste(dto: StockWasteCreateDto): StockWaste {
        val qty = dto.quantity.setScale(SCALE, ROUNDING)
        val stock = stockRepository.findByWarehouseIdAndInventoryItemId(dto.warehouseId, dto.inventoryItemId)
            .orElseThrow { IllegalArgumentException("Stock item not found in warehouse") }

        val uCost = stock.weightedAverageCost
        val totCost = qty.multiply(uCost).setScale(SCALE, ROUNDING)

        stock.quantity = stock.quantity.subtract(qty).setScale(SCALE, ROUNDING)
        stockRepository.save(stock)

        val waste = StockWaste(
            warehouseId = dto.warehouseId,
            inventoryItemId = dto.inventoryItemId,
            quantity = qty,
            unit = dto.unit,
            unitCost = uCost,
            totalCost = totCost,
            reason = dto.reason,
            approvedBy = dto.approvedBy
        )
        val savedWaste = wasteRepository.save(waste)

        val movement = StockMovement(
            warehouseId = dto.warehouseId,
            inventoryItemId = dto.inventoryItemId,
            quantity = qty.negate(),
            unit = dto.unit,
            unitCost = uCost,
            totalCost = totCost,
            movementType = MovementType.WASTE,
            referenceType = "STOCK_WASTE",
            referenceId = savedWaste.id
        )
        movementRepository.save(movement)

        return savedWaste
    }

    fun listTransfers(warehouseId: String): List<StockTransfer> {
        return transferRepository.findBySourceWarehouseIdOrTargetWarehouseId(warehouseId, warehouseId)
    }
}

@RestController
@RequestMapping("/api/v1/inventory")
class InventoryController(
    private val inventoryService: InventoryService
) {
    @GetMapping("/warehouses")
    fun getWarehouses(@RequestParam(required = false) branchId: String?): ApiResponse<List<Warehouse>> {
        return ApiResponse.success(inventoryService.listWarehouses(branchId))
    }

    @PostMapping("/warehouses")
    @PreAuthorize("hasAuthority('STOCK_ADJUST') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun createWarehouse(@RequestBody wh: Warehouse): ApiResponse<Warehouse> {
        return ApiResponse.success(inventoryService.createWarehouse(wh), "Warehouse created successfully")
    }

    @GetMapping("/items")
    fun getInventoryItems(): ApiResponse<List<InventoryItem>> {
        return ApiResponse.success(inventoryService.listInventoryItems())
    }

    @PostMapping("/items")
    @PreAuthorize("hasAuthority('STOCK_ADJUST') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun createInventoryItem(@RequestBody item: InventoryItem): ApiResponse<InventoryItem> {
        return ApiResponse.success(inventoryService.createInventoryItem(item), "Inventory item created successfully")
    }

    @PutMapping("/items/{id}")
    @PreAuthorize("hasAuthority('STOCK_ADJUST') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun updateInventoryItem(@PathVariable id: String, @RequestBody item: InventoryItem): ApiResponse<InventoryItem> {
        return ApiResponse.success(inventoryService.updateInventoryItem(id, item), "Inventory item updated successfully")
    }

    @DeleteMapping("/items/{id}")
    @PreAuthorize("hasAuthority('STOCK_ADJUST') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun deleteInventoryItem(@PathVariable id: String): ApiResponse<Boolean> {
        inventoryService.deleteInventoryItem(id)
        return ApiResponse.success(true, "Inventory item deleted successfully")
    }

    @GetMapping("/units")
    fun getUnits(): ApiResponse<List<UnitOfMeasure>> {
        return ApiResponse.success(inventoryService.listUnits())
    }

    @PostMapping("/units")
    @PreAuthorize("hasAuthority('STOCK_ADJUST') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun createUnit(@RequestBody uom: UnitOfMeasure): ApiResponse<UnitOfMeasure> {
        return ApiResponse.success(inventoryService.createUnit(uom), "Unit of measure created successfully")
    }

    @PutMapping("/units/{id}")
    @PreAuthorize("hasAuthority('STOCK_ADJUST') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun updateUnit(@PathVariable id: String, @RequestBody uom: UnitOfMeasure): ApiResponse<UnitOfMeasure> {
        return ApiResponse.success(inventoryService.updateUnit(id, uom), "Unit of measure updated successfully")
    }

    @DeleteMapping("/units/{id}")
    @PreAuthorize("hasAuthority('STOCK_ADJUST') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun deleteUnit(@PathVariable id: String): ApiResponse<Boolean> {
        inventoryService.deleteUnit(id)
        return ApiResponse.success(true, "Unit of measure deleted successfully")
    }

    @GetMapping("/stocks")
    fun getStockOnHand(@RequestParam warehouseId: String): ApiResponse<List<InventoryStock>> {
        return ApiResponse.success(inventoryService.getStockOnHand(warehouseId))
    }

    @GetMapping("/movements")
    fun getMovements(@RequestParam warehouseId: String): ApiResponse<List<StockMovement>> {
        return ApiResponse.success(inventoryService.listMovements(warehouseId))
    }

    @PostMapping("/purchase-receive")
    @PreAuthorize("hasAuthority('STOCK_ADJUST') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun purchaseReceive(@RequestBody dto: PurchaseReceiveDto): ApiResponse<StockMovement> {
        return ApiResponse.success(inventoryService.processPurchaseReceive(dto), "Purchase receive processed and WAC updated successfully")
    }

    @PostMapping("/transfers")
    @PreAuthorize("hasAuthority('STOCK_TRANSFER') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun createTransfer(@RequestBody dto: CreateTransferDto): ApiResponse<StockTransfer> {
        return ApiResponse.success(inventoryService.createTransfer(dto), "Stock transfer created successfully")
    }

    @PostMapping("/transfers/{id}/ship")
    @PreAuthorize("hasAuthority('STOCK_TRANSFER') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun shipTransfer(@PathVariable id: String): ApiResponse<StockTransfer> {
        return ApiResponse.success(inventoryService.shipTransfer(id), "Stock transfer shipped successfully")
    }

    @PostMapping("/transfers/{id}/receive")
    @PreAuthorize("hasAuthority('STOCK_TRANSFER') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun receiveTransfer(@PathVariable id: String): ApiResponse<StockTransfer> {
        return ApiResponse.success(inventoryService.receiveTransfer(id), "Stock transfer received and WAC updated successfully")
    }

    @PostMapping("/counts")
    @PreAuthorize("hasAuthority('STOCK_ADJUST') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun recordStockCount(@RequestBody dto: StockCountCreateDto): ApiResponse<StockCount> {
        return ApiResponse.success(inventoryService.recordStockCountAndAdjust(dto), "Stock count recorded and inventory adjusted successfully")
    }

    @PostMapping("/waste")
    @PreAuthorize("hasAuthority('STOCK_ADJUST') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun recordWaste(@RequestBody dto: StockWasteCreateDto): ApiResponse<StockWaste> {
        return ApiResponse.success(inventoryService.recordWaste(dto), "Stock waste recorded successfully")
    }
}
