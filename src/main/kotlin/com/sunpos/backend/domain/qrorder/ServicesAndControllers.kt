package com.sunpos.backend.domain.qrorder

import com.sunpos.backend.common.ApiResponse
import com.sunpos.backend.domain.catalog.MenuCategoryRepository
import com.sunpos.backend.domain.catalog.MenuItemRepository
import com.sunpos.backend.domain.catalog.ScheduledCatalogRepository
import com.sunpos.backend.domain.catalog.ScheduledCatalogStatus
import com.sunpos.backend.domain.organization.BranchRepository
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.http.ResponseEntity
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
    private val qrOrderMenuItemSettingRepository: QrOrderMenuItemSettingRepository,
    private val quarantinedQrOrderRepository: QuarantinedQrOrderRepository? = null,
    @org.springframework.context.annotation.Lazy
    private val branchOrderPushService: com.sunpos.backend.domain.websocket.BranchOrderPushService? = null,
    private val scheduledCatalogRepository: ScheduledCatalogRepository? = null,
    @org.springframework.context.annotation.Lazy
    private val qrTableSessionService: com.sunpos.backend.domain.table.QrTableSessionService? = null
) {
    private val logger = LoggerFactory.getLogger(QrOrderService::class.java)

    @Transactional
    fun createOrder(dto: CreateQrOrderDto): QrOrderDetailsDto {
        require(dto.branchId.isNotBlank()) { "branchId cannot be blank" }
        require(dto.tableNumber.isNotBlank()) { "tableNumber cannot be blank" }
        require(dto.items.isNotEmpty()) { "Order must contain at least one item" }

        val branchToggle = branchRepository.findById(dto.branchId.trim()).orElse(null)
        if (branchToggle != null && !branchToggle.isQrOrderEnabled) {
            throw IllegalStateException("ขออภัย ระบบสั่งอาหารผ่าน QR งดให้บริการชั่วคราว กรุณาติดต่อพนักงาน")
        }

        ensureItemsAreEnabledForQr(dto.branchId.trim(), dto.items.map { it.productId })

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
            cloudReceivedAt = Instant.now(),
            orderedAt = Instant.now(),
            createdAt = Instant.now(),
            updatedAt = Instant.now()
        )

        qrTableSessionService?.getActiveSession(order.branchId, order.tableNumber)?.ifPresent { session ->
            order.sessionId = session.id
            order.tableId = session.tableId
            order.tableNumber = session.tableNumber
        }

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

        val requestedBranch = dto.branchId.trim()
        // ปิด QR Order ทั้งสาขาชั่วคราว
        val branchForToggle = branchRepository.findById(requestedBranch).orElse(null)
        if (branchForToggle != null && !branchForToggle.isQrOrderEnabled) {
            throw IllegalStateException("ขออภัย ระบบสั่งอาหารผ่าน QR งดให้บริการชั่วคราว กรุณาติดต่อพนักงาน")
        }
        val requestedTable = dto.tableNumber.trim()
        val sessionService = qrTableSessionService
        val resolvedSession = when {
            !dto.token.isNullOrBlank() && sessionService != null -> {
                val byToken = sessionService.findActiveSessionByToken(dto.token)
                if (byToken.isPresent) {
                    val session = byToken.get()
                    if (session.branchId != requestedBranch || !sessionService.tableNumbersMatch(session.tableNumber, requestedTable)) {
                        logger.warn("QR Order rejected: Token/table mismatch table={} branch={}", requestedTable, requestedBranch)
                        throw IllegalStateException("โต๊ะนี้ปิดแล้ว กรุณาติดต่อพนักงาน")
                    }
                    session
                } else {
                    logger.warn("QR Order rejected: Token invalid or session expired for table {} branch {}", requestedTable, requestedBranch)
                    throw IllegalStateException("โต๊ะนี้ปิดแล้ว กรุณาติดต่อพนักงาน")
                }
            }
            sessionService != null -> sessionService.getActiveSession(requestedBranch, requestedTable).orElse(null)
            else -> null
        }

        if (!dto.token.isNullOrBlank() && sessionService != null && resolvedSession == null) {
            throw IllegalStateException("โต๊ะนี้ปิดแล้ว กรุณาติดต่อพนักงาน")
        }

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

        ensureItemsAreEnabledForQr(
            resolvedSession?.branchId ?: requestedBranch,
            dto.items.map { it.productId }
        )

        var calculatedTotal = BigDecimal.ZERO
        for (item in dto.items) {
            require(item.quantity > 0) { "Item quantity must be greater than 0" }
            calculatedTotal = calculatedTotal.add(item.unitPrice.multiply(BigDecimal(item.quantity)))
        }

        val order = QrOrder(
            id = UUID.randomUUID().toString(),
            branchId = resolvedSession?.branchId ?: requestedBranch,
            tableNumber = resolvedSession?.tableNumber ?: requestedTable,
            tableId = resolvedSession?.tableId,
            sessionId = resolvedSession?.id,
            status = QrOrderStatus.pending,
            customerNote = dto.customerNote?.trim()?.ifBlank { null },
            totalAmount = calculatedTotal,
            source = "qr",
            idempotencyKey = idempotencyKey?.trim()?.ifBlank { null },
            cloudReceivedAt = Instant.now(),
            orderedAt = Instant.now(),
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

    fun getActiveOrdersForTable(
        branchId: String,
        tableNumber: String,
        token: String? = null
    ): List<QrOrderDetailsDto> {
        val closedStatuses = setOf(
            QrOrderStatus.completed,
            QrOrderStatus.cancelled
        )

        // ถ้ามี token → กรองเฉพาะออเดอร์ของ session ปัจจุบัน (ประวัติไม่มั่วข้ามรอบ)
        val sessionService = qrTableSessionService
        if (!token.isNullOrBlank() && sessionService != null) {
            val byToken = sessionService.findActiveSessionByToken(token)
            if (byToken.isPresent) {
                return ordersForCurrentSession(byToken.get())
                    .filter { it.order.status !in closedStatuses }
            }
            val active = sessionService.getActiveSession(branchId, tableNumber)
            if (active.isPresent) {
                return ordersForCurrentSession(active.get())
                    .filter { it.order.status !in closedStatuses }
            }
        }

        // ไม่มี token / ไม่เจอ session → ดึงของโต๊ะนี้ที่ยังไม่จบ
        val orders = qrOrderRepository.findByBranchIdAndTableNumber(branchId, tableNumber)
            .filter { it.status !in closedStatuses }
            .sortedByDescending { it.createdAt }

        return orders.map { order ->
            val items = qrOrderItemRepository.findByOrderId(order.id)
            QrOrderDetailsDto(order, items)
        }
    }

    private fun ordersForCurrentSession(session: com.sunpos.backend.domain.table.QrTableSession): List<QrOrderDetailsDto> {
        val sessionService = qrTableSessionService
        val byBranch = qrOrderRepository.findByField("branchId", session.branchId)
        val scoped = byBranch.filter { order ->
            if (order.status == QrOrderStatus.cancelled) return@filter false
            val sameSession = !order.sessionId.isNullOrBlank() && order.sessionId == session.id
            val sameTable = sessionService?.tableNumbersMatch(order.tableNumber, session.tableNumber) == true ||
                (!order.tableId.isNullOrBlank() && order.tableId == session.tableId)
            val inThisRound = !order.createdAt.isBefore(session.openedAt)
            sameSession || (sameTable && inThisRound)
        }.sortedByDescending { it.createdAt }

        return scoped.map { order ->
            QrOrderDetailsDto(order, qrOrderItemRepository.findByOrderId(order.id))
        }
    }

    fun getPendingOrdersForBranch(branchId: String): List<QrOrderDetailsDto> {
        val allOrders = qrOrderRepository.findByField("branchId", branchId)
        val cutoff = Instant.now().minus(java.time.Duration.ofMinutes(5))
        val pendingOrders = allOrders.filter { 
            (it.status == QrOrderStatus.pending || it.status == QrOrderStatus.sent_to_branch) &&
            it.createdAt.isAfter(cutoff)
        }.sortedBy { it.createdAt }

        return pendingOrders.map { order ->
            val items = qrOrderItemRepository.findByOrderId(order.id)
            QrOrderDetailsDto(order, items)
        }
    }

    fun getQuarantinedOrders(branchId: String, from: Instant? = null, to: Instant? = null): List<QuarantinedQrOrder> {
        val repo = quarantinedQrOrderRepository ?: return emptyList()
        return if (from != null && to != null) {
            repo.findByBranchIdAndDateRange(branchId, from, to)
        } else {
            repo.findByBranchId(branchId)
        }
    }

    @Transactional
    fun markOrderPrinted(orderId: String): QrOrder {
        val order = qrOrderRepository.findById(orderId)
            .orElseThrow { NoSuchElementException("QR Order '$orderId' not found") }
        order.printedAt = Instant.now()
        order.updatedAt = Instant.now()
        return qrOrderRepository.save(order)
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
        // ปิด QR Order ทั้งสาขาชั่วคราว → ลูกค้าเว็บเห็นข้อความงดให้บริการ
        if (branchOpt.isPresent && !branchOpt.get().isQrOrderEnabled) {
            throw IllegalStateException("ขออภัย ระบบสั่งอาหารผ่าน QR งดให้บริการชั่วคราว กรุณาติดต่อพนักงาน")
        }
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

        val qrEnabledByItemId = qrOrderMenuItemSettingRepository.findByBranchId(branchId)
            .associate { it.menuItemId to it.isEnabled }
        val qrProducts = allProducts.filter { qrEnabledByItemId[it.id] != false }

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
        val categoryDtos = if (allCategories.isNotEmpty() && qrProducts.isNotEmpty()) {
            allCategories.mapNotNull { cat ->
                val prods = qrProducts.filter { it.categoryId == cat.id }
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

    /** List all sellable items, including QR-hidden items, for the branch manager screen. */
    fun getQrMenuManagementItems(branchId: String): List<QrOrderMenuManagementItemDto> {
        val products = getSellableProductsForBranch(branchId)
        val enabledByItemId = qrOrderMenuItemSettingRepository.findByBranchId(branchId)
            .associate { it.menuItemId to it.isEnabled }
        return products.map { product ->
            QrOrderMenuManagementItemDto(
                id = product.id,
                categoryId = product.categoryId,
                name = product.name,
                description = product.description,
                price = product.basePrice,
                imageUrl = product.imageUrl,
                isEnabled = enabledByItemId[product.id] != false
            )
        }
    }

    @Transactional
    fun updateQrMenuItemEnabled(branchId: String, menuItemId: String, enabled: Boolean): QrOrderMenuManagementItemDto {
        val product = getSellableProductsForBranch(branchId).firstOrNull { it.id == menuItemId }
            ?: throw IllegalArgumentException("Menu item '$menuItemId' is not available for branch '$branchId'")
        val setting = qrOrderMenuItemSettingRepository.findByBranchIdAndMenuItemId(branchId, menuItemId)
            .orElseGet { QrOrderMenuItemSetting(branchId = branchId, menuItemId = menuItemId) }
        setting.isEnabled = enabled
        setting.updatedAt = Instant.now()
        qrOrderMenuItemSettingRepository.save(setting)
        return QrOrderMenuManagementItemDto(
            id = product.id, categoryId = product.categoryId, name = product.name,
            description = product.description, price = product.basePrice,
            imageUrl = product.imageUrl, isEnabled = enabled
        )
    }

    private fun ensureItemsAreEnabledForQr(branchId: String, productIds: List<String>) {
        val disabledIds = qrOrderMenuItemSettingRepository.findByBranchId(branchId)
            .filter { !it.isEnabled }.map { it.menuItemId }.toSet()
        if (productIds.any { it in disabledIds }) {
            throw IllegalStateException("มีรายการที่ปิดรับสั่งผ่าน QR Order แล้ว กรุณารีเฟรชเมนู")
        }
    }

    private fun getSellableProductsForBranch(branchId: String) =
        menuItemRepository.findByBranchId(branchId).let { directItems ->
            if (directItems.isNotEmpty()) directItems
            else menuItemRepository.findAll().filter { it.branchId == branchId || it.branchId.isBlank() }
        }
            .filter { it.isActive && it.availability.equals("AVAILABLE", ignoreCase = true) }
            .sortedBy { it.sortOrder }

    fun getQrOrderEnabled(branchId: String): Boolean? {
        val branch = branchRepository.findById(branchId).orElse(null) ?: return null
        return branch.isQrOrderEnabled
    }

    @Transactional
    fun setQrOrderEnabled(branchId: String, enabled: Boolean): Boolean {
        val branch = branchRepository.findById(branchId).orElse(null)
            ?: throw NoSuchElementException("Branch '$branchId' not found")
        branch.isQrOrderEnabled = enabled
        branchRepository.save(branch)
        return enabled
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

    @GetMapping("/branch/{branchId}/menu-items")
    @PreAuthorize("hasAuthority('MENU_MANAGE') or hasAuthority('ROLE_BRANCH_MANAGER') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun getQrMenuManagementItems(@PathVariable branchId: String): ApiResponse<List<QrOrderMenuManagementItemDto>> {
        return ApiResponse.success(qrOrderService.getQrMenuManagementItems(branchId))
    }

    @PatchMapping("/branch/{branchId}/menu-items/{menuItemId}")
    @PreAuthorize("hasAuthority('MENU_MANAGE') or hasAuthority('ROLE_BRANCH_MANAGER') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun updateQrMenuItemEnabled(
        @PathVariable branchId: String,
        @PathVariable menuItemId: String,
        @RequestBody request: UpdateQrOrderMenuItemRequest
    ): ApiResponse<QrOrderMenuManagementItemDto> = ApiResponse.success(
        qrOrderService.updateQrMenuItemEnabled(branchId, menuItemId, request.enabled),
        "QR Order menu item updated"
    )

    // ── QR Order Toggle (branch-level) ──

    @GetMapping("/branch/{branchId}/enabled")
    fun getQrOrderEnabled(@PathVariable branchId: String): ResponseEntity<Boolean> {
        val enabled = qrOrderService.getQrOrderEnabled(branchId)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(enabled)
    }

    @GetMapping("/branch/{branchId}/quarantined")
    fun getQuarantinedOrders(
        @PathVariable branchId: String,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?
    ): ApiResponse<List<QuarantinedQrOrder>> {
        val fromInstant = if (!from.isNullOrBlank()) Instant.parse(from) else null
        val toInstant = if (!to.isNullOrBlank()) Instant.parse(to) else null
        return ApiResponse.success(qrOrderService.getQuarantinedOrders(branchId, fromInstant, toInstant))
    }

    @PostMapping("/orders/{orderId}/printed")
    fun markOrderPrinted(
        @PathVariable orderId: String
    ): ApiResponse<QrOrderDetailsDto> {
        val updated = qrOrderService.markOrderPrinted(orderId)
        val items = qrOrderService.getOrderDetails(orderId).items
        return ApiResponse.success(QrOrderDetailsDto(updated, items), "Order marked as printed")
    }

    @PostMapping("/branch/{branchId}/enabled")
    @PreAuthorize("hasAuthority('MENU_MANAGE') or hasAuthority('ROLE_BRANCH_MANAGER') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun setQrOrderEnabled(
        @PathVariable branchId: String,
        @RequestBody payload: Map<String, Any>
    ): ResponseEntity<Void> {
        val enabled = payload["enabled"] as? Boolean
            ?: return ResponseEntity.badRequest().build()
        qrOrderService.setQrOrderEnabled(branchId, enabled)
        return ResponseEntity.ok().build()
    }
}


@RestController
@RequestMapping("/api/public")
class PublicOrderController(
    private val qrOrderService: QrOrderService,
    @org.springframework.context.annotation.Lazy
    private val qrTableSessionService: com.sunpos.backend.domain.table.QrTableSessionService? = null
) {

    @PostMapping("/orders")
    fun createPublicOrder(
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @RequestHeader(value = "X-Session-Token", required = false) headerToken: String?,
        @RequestBody dto: CreatePublicOrderRequest
    ): PublicOrderResponse {
        val effectiveDto = if (dto.token.isNullOrBlank() && !headerToken.isNullOrBlank()) {
            dto.copy(token = headerToken.trim())
        } else {
            dto
        }
        return qrOrderService.createPublicOrder(effectiveDto, idempotencyKey)
    }

    @GetMapping("/menu/{branchId}")
    fun getPublicMenu(@PathVariable branchId: String): QrMenuResponseDto {
        return qrOrderService.getBranchMenu(branchId)
    }

    /** ให้ web QR ตรวจได้ว่าสาขานี้เปิดรับออเดอร์ผ่าน QR หรือไม่ */
    @GetMapping("/branch/{branchId}/enabled")
    fun getPublicQrOrderEnabled(@PathVariable branchId: String): ResponseEntity<Boolean> {
        val enabled = qrOrderService.getQrOrderEnabled(branchId) ?: true
        return ResponseEntity.ok(enabled)
    }

    @GetMapping("/session/validate")
    fun validateSession(
        @RequestParam branchId: String,
        @RequestParam tableNumber: String,
        @RequestParam(required = false) token: String?,
        @RequestHeader(value = "X-Session-Token", required = false) headerToken: String?
    ): org.springframework.http.ResponseEntity<Map<String, Any>> {
        val effectiveToken = token?.trim()?.ifBlank { null } ?: headerToken?.trim()?.ifBlank { null }
        val isValid = if (qrTableSessionService != null && !effectiveToken.isNullOrBlank()) {
            qrTableSessionService.isSessionTokenValid(branchId, tableNumber, effectiveToken)
        } else {
            true
        }
        val activeSession = qrTableSessionService?.getActiveSession(branchId, tableNumber)?.orElse(null)
        val isTableOccupied = activeSession != null

        return if (!isValid || (!isTableOccupied && !effectiveToken.isNullOrBlank())) {
            org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).body(
                mapOf(
                    "valid" to false,
                    "active" to false,
                    "message" to "โต๊ะนี้ปิดแล้ว กรุณาติดต่อพนักงาน"
                )
            )
        } else {
            org.springframework.http.ResponseEntity.ok(
                mapOf(
                    "valid" to true,
                    "active" to isTableOccupied,
                    "tableNumber" to tableNumber,
                    "branchId" to branchId
                )
            )
        }
    }

    @GetMapping("/orders/table")
    fun getTableOrders(
        @RequestParam branchId: String,
        @RequestParam tableNumber: String,
        @RequestParam(required = false) token: String?,
        @RequestHeader(value = "X-Session-Token", required = false) headerToken: String?
    ): List<QrOrderDetailsDto> {
        val effectiveToken = token?.trim()?.ifBlank { null } ?: headerToken?.trim()?.ifBlank { null }
        return qrOrderService.getActiveOrdersForTable(branchId.trim(), tableNumber.trim(), effectiveToken)
    }

    @GetMapping("/orders/table/{branchId}/{tableNumber}")
    fun getTableOrdersByPath(
        @PathVariable branchId: String,
        @PathVariable tableNumber: String
    ): List<QrOrderDetailsDto> {
        return qrOrderService.getActiveOrdersForTable(branchId.trim(), tableNumber.trim())
    }
}
