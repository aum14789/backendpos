package com.sunpos.backend.domain.purchasing

import com.sunpos.backend.common.ApiResponse
import com.sunpos.backend.common.JdbcRepository
import org.springframework.jdbc.core.JdbcTemplate
import com.sunpos.backend.domain.inventory.*
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
class SupplierRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<Supplier>(jdbcTemplate, "suppliers", Supplier::class.java)

@Repository
class PurchaseOrderRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<PurchaseOrder>(jdbcTemplate, "purchase_orders", PurchaseOrder::class.java) {
    fun findBySupplierId(supplierId: String): List<PurchaseOrder> = findByField("supplierId", supplierId)
    fun findByWarehouseId(warehouseId: String): List<PurchaseOrder> = findByField("warehouseId", warehouseId)
}

@Repository
class PurchaseOrderItemRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<PurchaseOrderItem>(jdbcTemplate, "purchase_order_items", PurchaseOrderItem::class.java) {
    fun findByPurchaseOrderId(purchaseOrderId: String): List<PurchaseOrderItem> = findByField("purchaseOrderId", purchaseOrderId)
}

@Repository
class GoodsReceiveRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<GoodsReceive>(jdbcTemplate, "goods_receives", GoodsReceive::class.java) {
    fun findByPurchaseOrderId(purchaseOrderId: String): List<GoodsReceive> = findByField("purchaseOrderId", purchaseOrderId)
}

@Repository
class GoodsReceiveItemRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<GoodsReceiveItem>(jdbcTemplate, "goods_receive_items", GoodsReceiveItem::class.java) {
    fun findByGoodsReceiveId(goodsReceiveId: String): List<GoodsReceiveItem> = findByField("goodsReceiveId", goodsReceiveId)
}

@Repository
class PurchaseReturnRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<PurchaseReturn>(jdbcTemplate, "purchase_returns", PurchaseReturn::class.java) {
    fun findByGoodsReceiveId(goodsReceiveId: String): List<PurchaseReturn> = findByField("goodsReceiveId", goodsReceiveId)
}

@Repository
class PurchaseReturnItemRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<PurchaseReturnItem>(jdbcTemplate, "purchase_return_items", PurchaseReturnItem::class.java) {
    fun findByPurchaseReturnId(purchaseReturnId: String): List<PurchaseReturnItem> = findByField("purchaseReturnId", purchaseReturnId)
}

@Repository
class SupplierPriceHistoryRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<SupplierPriceHistory>(jdbcTemplate, "supplier_price_histories", SupplierPriceHistory::class.java) {
    fun findBySupplierId(supplierId: String): List<SupplierPriceHistory> = findByField("supplierId", supplierId)
}

@Service
class PurchasingService(
    private val supplierRepository: SupplierRepository,
    private val poRepository: PurchaseOrderRepository,
    private val poItemRepository: PurchaseOrderItemRepository,
    private val grnRepository: GoodsReceiveRepository,
    private val grnItemRepository: GoodsReceiveItemRepository,
    private val prRepository: PurchaseReturnRepository,
    private val prItemRepository: PurchaseReturnItemRepository,
    private val priceHistoryRepository: SupplierPriceHistoryRepository,
    private val inventoryStockRepository: InventoryStockRepository,
    private val stockMovementRepository: StockMovementRepository,
    private val wacCalculationService: WacCalculationService
) {
    companion object {
        const val SCALE = 4
        val ROUNDING = RoundingMode.HALF_UP
    }

    fun listSuppliers(): List<Supplier> = supplierRepository.findAll()

    fun createSupplier(supplier: Supplier): Supplier = supplierRepository.save(supplier)

    @Transactional
    fun createPO(dto: CreatePurchaseOrderDto): PurchaseOrder {
        val poNum = "PO-${DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.of("UTC")).format(Instant.now())}-${(1000..9999).random()}"
        var totalAmount = BigDecimal.ZERO

        val po = PurchaseOrder(
            poNumber = poNum,
            supplierId = dto.supplierId,
            warehouseId = dto.warehouseId,
            status = POStatus.DRAFT,
            expectedDate = dto.expectedDate,
            notes = dto.notes,
            createdBy = dto.createdBy
        )
        val savedPO = poRepository.save(po)

        for (itemDto in dto.items) {
            val q = itemDto.orderedQty.setScale(SCALE, ROUNDING)
            val p = itemDto.expectedPrice.setScale(SCALE, ROUNDING)
            val tot = q.multiply(p).setScale(SCALE, ROUNDING)
            totalAmount = totalAmount.add(tot)

            val item = PurchaseOrderItem(
                purchaseOrderId = savedPO.id,
                inventoryItemId = itemDto.inventoryItemId,
                orderedQty = q,
                unit = itemDto.unit,
                expectedPrice = p,
                totalPrice = tot
            )
            poItemRepository.save(item)

            // Audit Supplier Price History
            priceHistoryRepository.save(
                SupplierPriceHistory(
                    supplierId = dto.supplierId,
                    inventoryItemId = itemDto.inventoryItemId,
                    price = p
                )
            )
        }

        savedPO.totalExpectedAmount = totalAmount
        return poRepository.save(savedPO)
    }

    @Transactional
    fun approvePO(poId: String, approvedBy: String): PurchaseOrder {
        val po = poRepository.findById(poId).orElseThrow { IllegalArgumentException("Purchase Order not found") }
        po.status = POStatus.APPROVED
        po.approvedBy = approvedBy
        return poRepository.save(po)
    }

    @Transactional
    fun orderPO(poId: String): PurchaseOrder {
        val po = poRepository.findById(poId).orElseThrow { IllegalArgumentException("Purchase Order not found") }
        po.status = POStatus.ORDERED
        return poRepository.save(po)
    }

    @Transactional
    fun processGoodsReceive(dto: CreateGoodsReceiveDto): GoodsReceive {
        val po = poRepository.findById(dto.purchaseOrderId).orElseThrow { IllegalArgumentException("PO not found") }
        val poItems = poItemRepository.findByPurchaseOrderId(po.id).associateBy { it.inventoryItemId }

        val grnNum = "GRN-${DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.of("UTC")).format(Instant.now())}-${(1000..9999).random()}"
        var totalAmount = BigDecimal.ZERO

        val grn = GoodsReceive(
            grnNumber = grnNum,
            purchaseOrderId = po.id,
            warehouseId = po.warehouseId,
            receivedBy = dto.receivedBy
        )
        val savedGRN = grnRepository.save(grn)

        for (grnItemDto in dto.items) {
            val poItem = poItems[grnItemDto.inventoryItemId] ?: throw IllegalArgumentException("Inventory item ${grnItemDto.inventoryItemId} is not in PO")
            
            // Validate receivedQty > 0 and damagedQty >= 0 and damagedQty <= receivedQty
            if (grnItemDto.receivedQty <= BigDecimal.ZERO) {
                throw IllegalArgumentException("Received quantity must be greater than zero")
            }
            if (grnItemDto.damagedQty < BigDecimal.ZERO || grnItemDto.damagedQty > grnItemDto.receivedQty) {
                throw IllegalArgumentException("Damaged quantity must be non-negative and less than or equal to received quantity")
            }

            // Validate that we do not receive more than remaining ordered PO quantity
            val remainingOrdered = poItem.orderedQty.subtract(poItem.receivedQty)
            if (grnItemDto.receivedQty > remainingOrdered) {
                throw IllegalArgumentException("Received quantity (${grnItemDto.receivedQty}) exceeds remaining ordered quantity ($remainingOrdered) for item ${grnItemDto.inventoryItemId}")
            }

            val netQty = grnItemDto.receivedQty.subtract(grnItemDto.damagedQty).setScale(SCALE, ROUNDING)
            val cost = grnItemDto.actualUnitCost.setScale(SCALE, ROUNDING)
            val totalCost = netQty.multiply(cost).setScale(SCALE, ROUNDING)
            totalAmount = totalAmount.add(totalCost)

            val item = GoodsReceiveItem(
                goodsReceiveId = savedGRN.id,
                inventoryItemId = grnItemDto.inventoryItemId,
                receivedQty = grnItemDto.receivedQty,
                damagedQty = grnItemDto.damagedQty,
                unit = grnItemDto.unit,
                actualUnitCost = cost,
                totalCost = totalCost
            )
            grnItemRepository.save(item)

            // Update PO Item received qty
            poItem.receivedQty = poItem.receivedQty.add(grnItemDto.receivedQty).setScale(SCALE, ROUNDING)
            poItemRepository.save(poItem)

            // 1. Create Stock Movement (PURCHASE) & Update Inventory Stock & WAC
            if (netQty.compareTo(BigDecimal.ZERO) > 0) {
                val stockOpt = inventoryStockRepository.findByWarehouseIdAndInventoryItemId(po.warehouseId, grnItemDto.inventoryItemId)
                val stock = if (stockOpt.isPresent) {
                    val s = stockOpt.get()
                    val newWac = wacCalculationService.calculateNewWac(s.quantity, s.weightedAverageCost, netQty, cost)
                    s.quantity = s.quantity.add(netQty).setScale(SCALE, ROUNDING)
                    s.weightedAverageCost = newWac
                    s.updatedAt = Instant.now()
                    inventoryStockRepository.save(s)
                } else {
                    val s = InventoryStock(
                        warehouseId = po.warehouseId,
                        inventoryItemId = grnItemDto.inventoryItemId,
                        quantity = netQty,
                        weightedAverageCost = cost
                    )
                    inventoryStockRepository.save(s)
                }

                val movement = StockMovement(
                    warehouseId = po.warehouseId,
                    inventoryItemId = grnItemDto.inventoryItemId,
                    quantity = netQty,
                    unit = grnItemDto.unit,
                    unitCost = cost,
                    totalCost = totalCost,
                    movementType = MovementType.PURCHASE,
                    referenceType = "GRN",
                    referenceId = savedGRN.id,
                    createdBy = dto.receivedBy
                )
                stockMovementRepository.save(movement)
            }
        }

        savedGRN.totalReceivedAmount = totalAmount
        val finalGRN = grnRepository.save(savedGRN)

        // Check if all PO items are fully received
        val allPoItems = poItemRepository.findByPurchaseOrderId(po.id)
        val fullyReceived = allPoItems.all { it.receivedQty.compareTo(it.orderedQty) >= 0 }
        po.status = if (fullyReceived) POStatus.RECEIVED else POStatus.PARTIALLY_RECEIVED
        poRepository.save(po)

        return finalGRN
    }

    @Transactional
    fun processPurchaseReturn(dto: CreatePurchaseReturnDto): PurchaseReturn {
        val grn = grnRepository.findById(dto.goodsReceiveId).orElseThrow { IllegalArgumentException("GRN not found") }
        val po = poRepository.findById(grn.purchaseOrderId).orElseThrow { IllegalArgumentException("PO not found") }

        val retNum = "PRT-${DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.of("UTC")).format(Instant.now())}-${(1000..9999).random()}"
        var totalReturnVal = BigDecimal.ZERO

        val pr = PurchaseReturn(
            returnNumber = retNum,
            goodsReceiveId = grn.id,
            supplierId = po.supplierId,
            warehouseId = grn.warehouseId,
            reason = dto.reason,
            createdBy = dto.createdBy
        )
        val savedPR = prRepository.save(pr)

        for (itemDto in dto.items) {
            val q = itemDto.returnQty.setScale(SCALE, ROUNDING)
            val c = itemDto.unitCost.setScale(SCALE, ROUNDING)
            val tot = q.multiply(c).setScale(SCALE, ROUNDING)
            totalReturnVal = totalReturnVal.add(tot)

            val item = PurchaseReturnItem(
                purchaseReturnId = savedPR.id,
                inventoryItemId = itemDto.inventoryItemId,
                returnQty = q,
                unit = itemDto.unit,
                unitCost = c,
                totalCost = tot
            )
            prItemRepository.save(item)

            // Reduce Inventory Stock & Create Reversal Stock Movement (RETURN)
            val stockOpt = inventoryStockRepository.findByWarehouseIdAndInventoryItemId(grn.warehouseId, itemDto.inventoryItemId)
            if (stockOpt.isPresent) {
                val s = stockOpt.get()
                s.quantity = s.quantity.subtract(q).setScale(SCALE, ROUNDING)
                inventoryStockRepository.save(s)
            }

            val movement = StockMovement(
                warehouseId = grn.warehouseId,
                inventoryItemId = itemDto.inventoryItemId,
                quantity = q.negate(),
                unit = itemDto.unit,
                unitCost = c,
                totalCost = tot,
                movementType = MovementType.RETURN,
                referenceType = "PURCHASE_RETURN",
                referenceId = savedPR.id,
                createdBy = dto.createdBy
            )
            stockMovementRepository.save(movement)
        }

        savedPR.totalReturnAmount = totalReturnVal
        return prRepository.save(savedPR)
    }

    fun listPOs(): List<PurchaseOrder> = poRepository.findAll()

    fun listGRNs(): List<GoodsReceive> = grnRepository.findAll()

    fun listReturns(): List<PurchaseReturn> = prRepository.findAll()

    fun listPriceHistories(supplierId: String): List<SupplierPriceHistory> = priceHistoryRepository.findBySupplierId(supplierId)
}

// REST Controller
@RestController
@RequestMapping("/api/v1/purchasing")
class PurchasingController(
    private val purchasingService: PurchasingService
) {
    @GetMapping("/suppliers")
    fun listSuppliers(): ApiResponse<List<Supplier>> = ApiResponse.success(purchasingService.listSuppliers())

    @PostMapping("/suppliers")
    @PreAuthorize("hasAuthority('STOCK_ADJUST') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun createSupplier(@RequestBody supplier: Supplier): ApiResponse<Supplier> {
        return ApiResponse.success(purchasingService.createSupplier(supplier), "Supplier created successfully")
    }

    @GetMapping("/orders")
    fun listPOs(): ApiResponse<List<PurchaseOrder>> = ApiResponse.success(purchasingService.listPOs())

    @PostMapping("/orders")
    @PreAuthorize("hasAuthority('STOCK_ADJUST') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun createPO(@RequestBody dto: CreatePurchaseOrderDto): ApiResponse<PurchaseOrder> {
        return ApiResponse.success(purchasingService.createPO(dto), "Purchase Order created successfully")
    }

    @PostMapping("/orders/{id}/approve")
    @PreAuthorize("hasAuthority('STOCK_ADJUST') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun approvePO(@PathVariable id: String, @RequestParam approvedBy: String): ApiResponse<PurchaseOrder> {
        return ApiResponse.success(purchasingService.approvePO(id, approvedBy), "PO approved successfully")
    }

    @PostMapping("/orders/{id}/order")
    @PreAuthorize("hasAuthority('STOCK_ADJUST') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun orderPO(@PathVariable id: String): ApiResponse<PurchaseOrder> {
        return ApiResponse.success(purchasingService.orderPO(id), "PO ordered successfully")
    }

    @GetMapping("/receives")
    fun listGRNs(): ApiResponse<List<GoodsReceive>> = ApiResponse.success(purchasingService.listGRNs())

    @PostMapping("/receive")
    @PreAuthorize("hasAuthority('STOCK_ADJUST') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun processGoodsReceive(@RequestBody dto: CreateGoodsReceiveDto): ApiResponse<GoodsReceive> {
        return ApiResponse.success(purchasingService.processGoodsReceive(dto), "Goods receive processed and WAC updated successfully")
    }

    @GetMapping("/returns")
    fun listReturns(): ApiResponse<List<PurchaseReturn>> = ApiResponse.success(purchasingService.listReturns())

    @PostMapping("/returns")
    @PreAuthorize("hasAuthority('STOCK_ADJUST') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun processPurchaseReturn(@RequestBody dto: CreatePurchaseReturnDto): ApiResponse<PurchaseReturn> {
        return ApiResponse.success(purchasingService.processPurchaseReturn(dto), "Purchase return reversal processed successfully")
    }

    @GetMapping("/price-history")
    fun getPriceHistory(@RequestParam supplierId: String): ApiResponse<List<SupplierPriceHistory>> {
        return ApiResponse.success(purchasingService.listPriceHistories(supplierId))
    }
}
