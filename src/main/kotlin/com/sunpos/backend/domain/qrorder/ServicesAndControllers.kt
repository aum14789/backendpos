package com.sunpos.backend.domain.qrorder

import com.sunpos.backend.common.ApiResponse
import com.sunpos.backend.domain.catalog.MenuCategoryRepository
import com.sunpos.backend.domain.catalog.MenuItemRepository
import com.sunpos.backend.domain.catalog.ScheduledCatalogRepository
import com.sunpos.backend.domain.catalog.ScheduledCatalogStatus
import com.sunpos.backend.domain.organization.BranchRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class QrOrderService(
    private val qrOrderRepository: QrOrderRepository,
    private val qrOrderItemRepository: QrOrderItemRepository,
    private val branchRepository: BranchRepository,
    private val menuCategoryRepository: MenuCategoryRepository,
    private val menuItemRepository: MenuItemRepository,
    @org.springframework.context.annotation.Lazy
    private val branchOrderPushService: com.sunpos.backend.domain.websocket.BranchOrderPushService? = null,
    private val scheduledCatalogRepository: ScheduledCatalogRepository? = null
) {
    private val logger = LoggerFactory.getLogger(QrOrderService::class.java)

    @Transactional
    fun createOrder(dto: CreateQrOrderDto): QrOrderDetailsDto {
        require(dto.branchId.isNotBlank()) { "branchId cannot be blank" }
        require(dto.tableNumber.isNotBlank()) { "tableNumber cannot be blank" }
        require(dto.items.isNotEmpty()) { "Order must contain at least one item" }

        var calculatedTotal = BigDecimal.ZERO
        for (item in dto.items) {
            require(item.quantity > 0) { "Item quantity must be greater than 0" }
            calculatedTotal = calculatedTotal.add(item.unitPrice.multiply(BigDecimal(item.quantity)))
        }

        val order = QrOrder(
            id = UUID.randomUUID().toString(),
            branchId = dto.branchId.trim(),
            tableNumber = dto.tableNumber.trim(),
            status = QrOrderStatus.pending,
            customerNote = dto.customerNote?.trim()?.ifBlank { null },
            totalAmount = calculatedTotal,
            source = "qr",
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        val savedOrder = qrOrderRepository.save(order)

        val savedItems = dto.items.map { itemDto ->
            val orderItem = QrOrderItem(
                id = UUID.randomUUID().toString(),
                orderId = savedOrder.id,
                productId = itemDto.productId,
                productName = itemDto.productName,
                quantity = itemDto.quantity,
                unitPrice = itemDto.unitPrice,
                options = itemDto.options,
                note = itemDto.note?.trim()?.ifBlank { null }
            )
            qrOrderItemRepository.save(orderItem)
        }

        logger.info("QR Order created: id={} table={} branch={} total={}", savedOrder.id, savedOrder.tableNumber, savedOrder.branchId, savedOrder.totalAmount)
        try {
            branchOrderPushService?.pushOrderToBranch(savedOrder, savedItems)
        } catch (ex: Exception) {
            logger.warn("Could not push order {} to branch WebSocket: {}", savedOrder.id, ex.message)
        }
        return QrOrderDetailsDto(savedOrder, savedItems)
    }

    @Transactional
    fun createPublicOrder(dto: CreatePublicOrderRequest, idempotencyKey: String?): PublicOrderResponse {
        require(dto.branchId.isNotBlank()) { "branchId cannot be blank" }
        require(dto.tableNumber.isNotBlank()) { "tableNumber cannot be blank" }
        require(dto.items.isNotEmpty()) { "Order must contain at least one item" }

        if (!idempotencyKey.isNullOrBlank()) {
            val existing = qrOrderRepository.findByIdempotencyKey(idempotencyKey.trim())
            if (existing.isPresent) {
                logger.info("Idempotent request matched for key {}: returning existing order {}", idempotencyKey, existing.get().id)
                return PublicOrderResponse(
                    orderId = existing.get().id,
                    status = existing.get().status.name,
                    message = "Order already received (Idempotent replay)"
                )
            }
        }

        var calculatedTotal = BigDecimal.ZERO
        for (item in dto.items) {
            require(item.quantity > 0) { "Item quantity must be greater than 0" }
            calculatedTotal = calculatedTotal.add(item.unitPrice.multiply(BigDecimal(item.quantity)))
        }

        val order = QrOrder(
            id = UUID.randomUUID().toString(),
            branchId = dto.branchId.trim(),
            tableNumber = dto.tableNumber.trim(),
            status = QrOrderStatus.pending,
            customerNote = dto.customerNote?.trim()?.ifBlank { null },
            totalAmount = calculatedTotal,
            source = "qr",
            idempotencyKey = idempotencyKey?.trim()?.ifBlank { null },
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        val savedOrder = qrOrderRepository.save(order)

        val savedItems = dto.items.map { itemDto ->
            val optionsStr = when (val opt = itemDto.options) {
                null -> null
                is String -> opt
                else -> opt.toString()
            }

            val orderItem = QrOrderItem(
                id = UUID.randomUUID().toString(),
                orderId = savedOrder.id,
                productId = itemDto.productId,
                productName = itemDto.productName,
                quantity = itemDto.quantity,
                unitPrice = itemDto.unitPrice,
                options = optionsStr,
                note = itemDto.note?.trim()?.ifBlank { null }
            )
            qrOrderItemRepository.save(orderItem)
        }

        logger.info("Public QR Order created: id={} table={} branch={} total={}", savedOrder.id, savedOrder.tableNumber, savedOrder.branchId, savedOrder.totalAmount)
        try {
            branchOrderPushService?.pushOrderToBranch(savedOrder, savedItems)
        } catch (ex: Exception) {
            logger.warn("Could not push order {} to branch WebSocket: {}", savedOrder.id, ex.message)
        }
        return PublicOrderResponse(
            orderId = savedOrder.id,
            status = savedOrder.status.name,
            message = "Order received successfully"
        )
    }

    fun getOrderDetails(orderId: String): QrOrderDetailsDto {
        val order = qrOrderRepository.findById(orderId)
            .orElseThrow { NoSuchElementException("QR Order '$orderId' not found") }
        val items = qrOrderItemRepository.findByOrderId(orderId)
        return QrOrderDetailsDto(order, items)
    }

    fun getActiveOrdersForTable(branchId: String, tableNumber: String): List<QrOrderDetailsDto> {
        val orders = qrOrderRepository.findByBranchIdAndTableNumber(branchId, tableNumber)
            .sortedByDescending { it.createdAt }
        return orders.map { order ->
            val items = qrOrderItemRepository.findByOrderId(order.id)
            QrOrderDetailsDto(order, items)
        }
    }

    fun getPendingOrdersForBranch(branchId: String): List<QrOrderDetailsDto> {
        val allOrders = qrOrderRepository.findByField("branchId", branchId)
        val pendingOrders = allOrders.filter { 
            it.status == QrOrderStatus.pending || it.status == QrOrderStatus.sent_to_branch 
        }.sortedBy { it.createdAt }

        return pendingOrders.map { order ->
            val items = qrOrderItemRepository.findByOrderId(order.id)
            QrOrderDetailsDto(order, items)
        }
    }

    @Transactional
    fun updateOrderStatus(orderId: String, newStatus: QrOrderStatus): QrOrderDetailsDto {
        val order = qrOrderRepository.findById(orderId)
            .orElseThrow { NoSuchElementException("QR Order '$orderId' not found") }
        order.status = newStatus
        order.updatedAt = Instant.now()
        val saved = qrOrderRepository.save(order)
        val items = qrOrderItemRepository.findByOrderId(orderId)
        logger.info("QR Order {} updated to status {}", orderId, newStatus)
        return QrOrderDetailsDto(saved, items)
    }

    fun getBranchMenu(branchId: String): QrMenuResponseDto {
        val branchOpt = branchRepository.findById(branchId)
        val branchName = branchOpt.map { it.name }.orElse("SunPOS Restaurant")

        // 1. Fetch categories for this branch (sorted by sortOrder)
        val branchCategories = menuCategoryRepository.findByBranchIdOrderBySortOrderAsc(branchId)
        val allCategories = if (branchCategories.isNotEmpty()) {
            branchCategories
        } else {
            menuCategoryRepository.findAll().filter { it.branchId == branchId || it.branchId.isBlank() }
        }.filter { it.isActive }.sortedBy { it.sortOrder }

        // 2. Fetch products for this branch (active and availability == AVAILABLE)
        val branchProducts = menuItemRepository.findByBranchId(branchId)
        val allProducts = if (branchProducts.isNotEmpty()) {
            branchProducts
        } else {
            menuItemRepository.findAll().filter { it.branchId == branchId || it.branchId.isBlank() }
        }.filter { it.isActive && it.availability.equals("AVAILABLE", ignoreCase = true) }
            .sortedBy { it.sortOrder }

        // 3. Resolve branch-specific pricing overrides from ScheduledCatalog if present
        val branchPriceMap: Map<String, BigDecimal> = try {
            val now = Instant.now()
            scheduledCatalogRepository?.findByBranchIdAndStatus(branchId, ScheduledCatalogStatus.ACTIVE)
                ?.filter { it.startAt.isBefore(now) && it.endAt.isAfter(now) }
                ?.associateBy({ it.menuItemId }, { it.scheduledPrice }) ?: emptyMap()
        } catch (e: Exception) {
            logger.debug("Scheduled catalog branch pricing lookup skipped: {}", e.message)
            emptyMap()
        }

        // 4. Map products into categories and omit categories without available items
        val categoryDtos = if (allCategories.isNotEmpty() && allProducts.isNotEmpty()) {
            allCategories.mapNotNull { cat ->
                val prods = allProducts.filter { it.categoryId == cat.id }
                if (prods.isEmpty()) {
                    null
                } else {
                    QrMenuCategoryDto(
                        id = cat.id,
                        name = cat.name,
                        sortOrder = cat.sortOrder,
                        products = prods.map { p ->
                            val effectivePrice = branchPriceMap[p.id] ?: p.basePrice
                            QrMenuProductDto(
                                id = p.id,
                                categoryId = p.categoryId,
                                name = p.name,
                                description = p.description,
                                price = effectivePrice,
                                imageUrl = p.imageUrl,
                                isAvailable = true
                            )
                        }
                    )
                }
            }
        } else {
            // Return empty list if branch has no catalog items (No mock menu in production)
            emptyList()
        }

        logger.info("Retrieved {} menu categories ({} products) for branch [{}] ({})",
            categoryDtos.size, categoryDtos.sumOf { it.products.size }, branchId, branchName)

        return QrMenuResponseDto(
            branchId = branchId,
            branchName = branchName,
            categories = categoryDtos
        )
    }
}

@RestController
@RequestMapping("/api/v1/qr")
class QrOrderController(
    private val qrOrderService: QrOrderService
) {

    @PostMapping("/orders")
    fun createOrder(@RequestBody dto: CreateQrOrderDto): ApiResponse<QrOrderDetailsDto> {
        val created = qrOrderService.createOrder(dto)
        return ApiResponse.success(created, "Order submitted successfully")
    }

    @GetMapping("/orders/{orderId}")
    fun getOrderDetails(@PathVariable orderId: String): ApiResponse<QrOrderDetailsDto> {
        return ApiResponse.success(qrOrderService.getOrderDetails(orderId))
    }

    @GetMapping("/orders/table/{branchId}/{tableNumber}")
    fun getActiveOrdersForTable(
        @PathVariable branchId: String,
        @PathVariable tableNumber: String
    ): ApiResponse<List<QrOrderDetailsDto>> {
        return ApiResponse.success(qrOrderService.getActiveOrdersForTable(branchId, tableNumber))
    }

    @GetMapping("/branch/{branchId}/pending")
    fun getPendingOrdersForBranch(@PathVariable branchId: String): ApiResponse<List<QrOrderDetailsDto>> {
        return ApiResponse.success(qrOrderService.getPendingOrdersForBranch(branchId))
    }

    @PatchMapping("/orders/{orderId}/status")
    fun updateOrderStatus(
        @PathVariable orderId: String,
        @RequestBody dto: UpdateQrOrderStatusDto
    ): ApiResponse<QrOrderDetailsDto> {
        return ApiResponse.success(qrOrderService.updateOrderStatus(orderId, dto.status), "Status updated")
    }

    @GetMapping("/menu/{branchId}")
    fun getBranchMenu(@PathVariable branchId: String): ApiResponse<QrMenuResponseDto> {
        return ApiResponse.success(qrOrderService.getBranchMenu(branchId))
    }
}

@RestController
@RequestMapping("/api/public")
class PublicOrderController(
    private val qrOrderService: QrOrderService
) {

    @PostMapping("/orders")
    fun createPublicOrder(
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @RequestBody dto: CreatePublicOrderRequest
    ): PublicOrderResponse {
        return qrOrderService.createPublicOrder(dto, idempotencyKey)
    }

    @GetMapping("/menu/{branchId}")
    fun getPublicMenu(@PathVariable branchId: String): QrMenuResponseDto {
        return qrOrderService.getBranchMenu(branchId)
    }
}
