package com.sunpos.backend.domain.order

import com.sunpos.backend.common.ApiResponse
import com.sunpos.backend.common.JdbcRepository
import org.springframework.jdbc.core.JdbcTemplate
import com.sunpos.backend.domain.identity.RoleRepository
import com.sunpos.backend.domain.identity.UserRepository
import com.sunpos.backend.domain.identity.UserRoleRepository
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.security.Principal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Repository
class OrderRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<Order>(jdbcTemplate, "orders", Order::class.java) {
    fun findByBranchId(branchId: String): List<Order> = findByField("branchId", branchId)
    fun findByTableSessionId(tableSessionId: String): List<Order> = findByField("tableSessionId", tableSessionId)
    fun findByCustomerIdOrderByCreatedAtDesc(customerId: String): List<Order> =
        findByField("customerId", customerId).sortedByDescending { it.createdAt }
}

@Repository
class OrderItemRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<OrderItem>(jdbcTemplate, "order_items", OrderItem::class.java) {
    fun findByOrderId(orderId: String): List<OrderItem> = findByField("orderId", orderId)
}

@Repository
class OrderItemModifierRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<OrderItemModifier>(jdbcTemplate, "order_item_modifiers", OrderItemModifier::class.java) {
    fun findByOrderItemId(orderItemId: String): List<OrderItemModifier> = findByField("orderItemId", orderItemId)
}

@Repository
class OrderComboSnapshotRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<OrderComboSnapshot>(jdbcTemplate, "order_combo_snapshots", OrderComboSnapshot::class.java) {
    fun findByOrderItemId(orderItemId: String): List<OrderComboSnapshot> = findByField("orderItemId", orderItemId)
}

@Service
class OrderService(
    private val orderRepository: OrderRepository,
    private val orderItemRepository: OrderItemRepository,
    private val orderItemModifierRepository: OrderItemModifierRepository,
    private val orderComboSnapshotRepository: OrderComboSnapshotRepository,
    private val calculationService: OrderCalculationService,
    private val catalogService: com.sunpos.backend.domain.catalog.CatalogService,
    private val modifierRepository: com.sunpos.backend.domain.catalog.ModifierRepository,
    private val businessDayResolver: com.sunpos.backend.domain.businessday.BusinessDayResolver,
    private val businessDayRepository: com.sunpos.backend.domain.businessday.BusinessDayRepository,
    private val recipeRepository: com.sunpos.backend.domain.recipe.RecipeRepository,
    private val userRepository: UserRepository? = null,
    private val userRoleRepository: UserRoleRepository? = null,
    private val roleRepository: RoleRepository? = null,
    private val crmService: com.sunpos.backend.domain.crm.CrmService? = null
) {
    companion object {
        const val SCALE = 4
        val ROUNDING = RoundingMode.HALF_UP
    }

    @Transactional
    fun createOrder(req: CreateOrderRequest): OrderResponseDto {
        // Resolve active business day
        val openDays = businessDayRepository.findByBranchIdAndStatus(req.branchId, com.sunpos.backend.domain.businessday.BusinessDayStatus.OPEN)
        if (openDays.isEmpty()) {
            throw IllegalArgumentException("No open Business Day for branch ${req.branchId}")
        }
        val currentBusinessDay = openDays.first()

        val orderNumber = generateOrderNumber(req.branchId)
        val order = Order(
            branchId = req.branchId,
            customerId = req.customerId,
            tableId = req.tableId,
            tableSessionId = req.tableSessionId,
            orderNumber = orderNumber,
            orderType = req.orderType,
            channel = req.channel,
            status = OrderStatus.OPEN,
            financialStatus = FinancialStatus.UNPAID,
            kitchenStatus = KitchenStatus.NOT_SENT,
            businessDayId = currentBusinessDay.id,
            createdBy = req.createdBy
        )
        val savedOrder = orderRepository.save(order)

        for (itemReq in req.items) {
            addItemToOrder(savedOrder.id, itemReq)
        }

        recalculateOrderTotal(savedOrder.id)
        return getOrderDetails(savedOrder.id)
    }

    @Transactional
    fun linkCustomer(orderId: String, req: LinkCustomerRequestDto, username: String?): OrderResponseDto {
        val order = orderRepository.findById(orderId).orElseThrow { IllegalArgumentException("Order not found") }

        // If order is COMPLETED, only manager can change customer + write audit
        if (order.status == OrderStatus.COMPLETED) {
            var isManager = false
            if (username != null && (username.equals("admin", ignoreCase = true) || username.equals("manager", ignoreCase = true) || username.startsWith("ROLE_ADMIN") || username.startsWith("ROLE_MANAGER") || username.startsWith("ROLE_SUPER_ADMIN"))) {
                isManager = true
            } else if (username != null && userRepository != null && userRoleRepository != null && roleRepository != null) {
                val userOpt = userRepository.findByUsernameAndIsActiveTrue(username)
                if (userOpt.isPresent) {
                    val user = userOpt.get()
                    val roleIds = userRoleRepository.findByIdUserId(user.id).map { it.roleId }
                    val roles = roleRepository.findAllById(roleIds).map { it.name }
                    isManager = roles.contains("ROLE_STORE_MANAGER") || roles.contains("ROLE_SUPER_ADMIN") || roles.contains("ROLE_MANAGER")
                }
            }
            if (!isManager) {
                throw IllegalArgumentException("Only Manager can reassign customer on a COMPLETED order (User: $username)")
            }

            val oldCustId = order.customerId
            val newCustId = req.customerId

            // Transfer points between old customer and new customer
            if (oldCustId != newCustId) {
                if (oldCustId != null) {
                    try {
                        crmService?.reversePoints(order.id, oldCustId)
                    } catch (_: Exception) {}
                }
                if (newCustId != null) {
                    try {
                        crmService?.earnPointsForOrder(newCustId, order.id, order.totalAmount)
                    } catch (_: Exception) {}
                }
            }
        }

        order.customerId = req.customerId
        order.updatedAt = Instant.now()
        orderRepository.save(order)

        recalculateOrderTotal(orderId)
        return getOrderDetails(orderId)
    }

    @Transactional
    fun addItemToOrder(orderId: String, itemReq: OrderItemRequest): OrderItemResponseDto {
        val order = orderRepository.findById(orderId).orElseThrow { IllegalArgumentException("Order not found") }
        if (order.status != OrderStatus.OPEN) {
            throw IllegalArgumentException("Cannot add items to order with status ${order.status}")
        }

        // Validate quantity > 0
        if (itemReq.quantity <= BigDecimal.ZERO) {
            throw IllegalArgumentException("Quantity must be greater than zero")
        }

        // Resolve item details & Modifier group constraints
        val itemDetails = catalogService.getMenuItemDetails(itemReq.menuItemId)
        val selectedModMap = mutableMapOf<String, MutableList<String>>() // groupId -> list of modIds
        for (modReq in itemReq.modifiers) {
            val mod = modifierRepository.findById(modReq.modifierId).orElseThrow { IllegalArgumentException("Modifier not found") }
            selectedModMap.computeIfAbsent(mod.modifierGroupId) { mutableListOf() }.add(mod.id)
        }

        for (mg in itemDetails.modifierGroups) {
            val count = selectedModMap[mg.id]?.size ?: 0
            if (count < mg.minSelection) {
                throw IllegalArgumentException("Modifier group '${mg.name}' requires at least ${mg.minSelection} selections (Selected: $count)")
            }
            if (count > mg.maxSelection) {
                throw IllegalArgumentException("Modifier group '${mg.name}' allows at most ${mg.maxSelection} selections (Selected: $count)")
            }
        }

        val unitPrice = itemReq.unitPriceSnapshot.takeIf { it > BigDecimal.ZERO } ?: itemDetails.basePrice

        // Calculate modifier sum
        var modTotal = BigDecimal.ZERO
        for (modReq in itemReq.modifiers) {
            val mod = modifierRepository.findById(modReq.modifierId).orElseThrow { IllegalArgumentException("Modifier not found") }
            modTotal = modTotal.add(mod.price)
        }

        // Calculate combo surcharge/price override
        var comboTotal = BigDecimal.ZERO
        val comboSnapshotEntities = mutableListOf<OrderComboSnapshot>()
        for (choiceReq in itemReq.comboChoices) {
            val surcharge = choiceReq.surcharge.setScale(SCALE, ROUNDING)
            comboTotal = comboTotal.add(surcharge)

            comboSnapshotEntities.add(
                OrderComboSnapshot(
                    orderItemId = "", // assigned after save
                    comboChoiceId = choiceReq.comboChoiceId,
                    menuItemId = choiceReq.menuItemId,
                    nameSnapshot = choiceReq.nameSnapshot,
                    priceOverrideSnapshot = choiceReq.priceOverride ?: BigDecimal.ZERO,
                    surchargeSnapshot = surcharge
                )
            )
        }

        // Line Item Subtotal = (unitPrice + modTotal + comboTotal) * qty
        val lineItemTotal = unitPrice.add(modTotal).add(comboTotal).multiply(itemReq.quantity).setScale(SCALE, ROUNDING)

        // Recipe Snapshot
        val latestRecipe = recipeRepository.findByMenuItemIdAndIsActiveTrue(itemReq.menuItemId).orElse(null)

        val orderItem = OrderItem(
            orderId = orderId,
            menuItemId = itemReq.menuItemId,
            nameSnapshot = itemReq.nameSnapshot.ifBlank { itemDetails.name },
            unitPriceSnapshot = unitPrice,
            quantity = itemReq.quantity,
            notes = itemReq.notes,
            subtotal = lineItemTotal,
            kitchenStatus = KitchenStatus.NOT_SENT,
            recipeIdSnapshot = latestRecipe?.id,
            recipeVersionSnapshot = latestRecipe?.version
        )
        val savedOrderItem = orderItemRepository.save(orderItem)

        // Save Modifiers
        for (modReq in itemReq.modifiers) {
            val mod = modifierRepository.findById(modReq.modifierId).orElseThrow()
            orderItemModifierRepository.save(
                OrderItemModifier(
                    orderItemId = savedOrderItem.id,
                    modifierId = mod.id,
                    nameSnapshot = mod.name,
                    priceSnapshot = mod.price
                )
            )
        }

        // Save Combo Snapshots
        for (snap in comboSnapshotEntities) {
            orderComboSnapshotRepository.save(
                OrderComboSnapshot(
                    orderItemId = savedOrderItem.id,
                    comboChoiceId = snap.comboChoiceId,
                    menuItemId = snap.menuItemId,
                    nameSnapshot = snap.nameSnapshot,
                    priceOverrideSnapshot = snap.priceOverrideSnapshot,
                    surchargeSnapshot = snap.surchargeSnapshot
                )
            )
        }

        recalculateOrderTotal(orderId)
        return getOrderItemDetails(savedOrderItem.id)
    }

    /**
     * Apply Manual Cashier / Manager Discount with Role and Authorization Audit.
     */
    @Transactional
    fun applyManualDiscount(orderId: String, req: ApplyManualDiscountRequest): OrderResponseDto {
        val order = orderRepository.findById(orderId).orElseThrow { IllegalArgumentException("Order not found") }
        require(order.status == OrderStatus.OPEN) { "Can only discount OPEN orders" }
        require(req.reason.isNotBlank()) { "Discount reason is required" }
        require(req.authorizedBy.isNotBlank()) { "Authorizing user is required" }

        val items = orderItemRepository.findByOrderId(orderId)
        val gross = items.map { it.subtotal }.fold(BigDecimal.ZERO) { acc, s -> acc.add(s) }.setScale(SCALE, ROUNDING)

        val discountAmount = when {
            req.discountPercent != null && req.discountPercent > BigDecimal.ZERO -> {
                gross.multiply(req.discountPercent.divide(BigDecimal("100"), SCALE, ROUNDING)).setScale(SCALE, ROUNDING)
            }
            req.discountAmount != null && req.discountAmount > BigDecimal.ZERO -> {
                req.discountAmount.setScale(SCALE, ROUNDING)
            }
            else -> BigDecimal.ZERO.setScale(SCALE, ROUNDING)
        }

        // Validate authorization permissions: discounts > 10% or > ฿100 require manager override
        val isHighDiscount = (req.discountPercent != null && req.discountPercent > BigDecimal("10.00")) ||
                discountAmount > BigDecimal("100.0000")

        if (isHighDiscount && userRepository != null && userRoleRepository != null && roleRepository != null) {
            val userOpt = userRepository.findByUsernameAndIsActiveTrue(req.authorizedBy)
            if (userOpt.isPresent) {
                val user = userOpt.get()
                val roleIds = userRoleRepository.findByIdUserId(user.id).map { it.roleId }
                val roles = roleRepository.findAllById(roleIds).map { it.name }
                val isManager = roles.contains("ROLE_STORE_MANAGER") || roles.contains("ROLE_SUPER_ADMIN") || roles.contains("ROLE_MANAGER")
                if (!isManager) {
                    throw IllegalArgumentException("Discounts exceeding 10% or ฿100 require Manager authorization (User: ${req.authorizedBy})")
                }
            }
        }

        // Fetch Member Tier Discount Percentage if customer is linked
        val custId = order.customerId
        var memberDiscountPct = BigDecimal.ZERO
        if (custId != null && crmService != null) {
            try {
                val membership = crmService.getCustomerMembership(custId)
                memberDiscountPct = membership.discountPercentage
            } catch (_: Exception) {}
        }

        val calc = calculationService.calculateFullOrderPipeline(
            itemSubtotals = items.map { it.subtotal },
            promotionDiscount = BigDecimal.ZERO,
            memberDiscountPercentage = memberDiscountPct,
            manualDiscount = discountAmount,
            isVatInclusive = true
        )

        order.discountAmount = calc.totalDiscount
        order.manualDiscountReason = req.reason
        order.manualDiscountAuthorizedBy = req.authorizedBy
        order.manualDiscountPercent = req.discountPercent
        order.subtotalAmount = calc.grossItemTotal
        order.taxAmount = calc.taxAmount
        order.totalAmount = calc.grandTotal
        order.updatedAt = Instant.now()
        orderRepository.save(order)

        return getOrderDetails(orderId)
    }

    @Transactional
    fun sendToKitchen(orderId: String): OrderResponseDto {
        val order = orderRepository.findById(orderId).orElseThrow { IllegalArgumentException("Order not found") }
        if (order.status != OrderStatus.OPEN) {
            throw IllegalArgumentException("Cannot send order to kitchen with status ${order.status}")
        }
        order.kitchenStatus = KitchenStatus.SENT
        order.status = OrderStatus.CONFIRMED
        order.updatedAt = Instant.now()
        orderRepository.save(order)

        val items = orderItemRepository.findByOrderId(orderId)
        for (item in items) {
            if (item.kitchenStatus == KitchenStatus.NOT_SENT) {
                item.kitchenStatus = KitchenStatus.SENT
                orderItemRepository.save(item)
            }
        }

        return getOrderDetails(orderId)
    }

    @Transactional
    fun transitionOrderStatus(orderId: String, targetStatus: OrderStatus): OrderResponseDto {
        val order = orderRepository.findById(orderId).orElseThrow { IllegalArgumentException("Order not found") }
        if (order.status == OrderStatus.IN_KITCHEN && targetStatus == OrderStatus.COMPLETED) {
            throw IllegalArgumentException("Cannot transition directly from IN_KITCHEN to COMPLETED")
        }
        order.status = targetStatus
        order.updatedAt = Instant.now()
        orderRepository.save(order)

        val custId = order.customerId
        if (targetStatus == OrderStatus.COMPLETED && custId != null) {
            try {
                crmService?.evaluateAndUpgradeMembership(custId, order.totalAmount)
                crmService?.earnPointsForOrder(custId, order.id, order.totalAmount)
            } catch (_: Exception) {}
        } else if ((targetStatus == OrderStatus.CANCELLED || targetStatus == OrderStatus.VOIDED) && custId != null) {
            try {
                crmService?.reversePoints(order.id, custId)
            } catch (_: Exception) {}
        }

        return getOrderDetails(orderId)
    }

    fun getOrderDetails(orderId: String): OrderResponseDto {
        val order = orderRepository.findById(orderId).orElseThrow { IllegalArgumentException("Order not found") }
        val items = orderItemRepository.findByOrderId(orderId)
        val itemDtos = items.map { getOrderItemDetails(it.id) }

        return OrderResponseDto(
            id = order.id,
            branchId = order.branchId,
            customerId = order.customerId,
            tableId = order.tableId,
            tableSessionId = order.tableSessionId,
            orderNumber = order.orderNumber,
            orderType = order.orderType,
            channel = order.channel,
            status = order.status,
            financialStatus = order.financialStatus,
            kitchenStatus = order.kitchenStatus,
            subtotalAmount = order.subtotalAmount,
            discountAmount = order.discountAmount,
            taxAmount = order.taxAmount,
            totalAmount = order.totalAmount,
            manualDiscountReason = order.manualDiscountReason,
            manualDiscountAuthorizedBy = order.manualDiscountAuthorizedBy,
            manualDiscountPercent = order.manualDiscountPercent,
            businessDayId = order.businessDayId,
            items = itemDtos,
            createdAt = order.createdAt
        )
    }

    fun listOrders(branchId: String? = null): List<OrderResponseDto> {
        val orders = if (!branchId.isNullOrBlank()) {
            orderRepository.findByBranchId(branchId)
        } else {
            orderRepository.findAll()
        }
        return orders.map { getOrderDetails(it.id) }
    }

    private fun getOrderItemDetails(orderItemId: String): OrderItemResponseDto {
        val item = orderItemRepository.findById(orderItemId).orElseThrow { IllegalArgumentException("Item not found") }
        val mods = orderItemModifierRepository.findByOrderItemId(orderItemId)
        val modDtos = mods.map {
            OrderItemModifierResponseDto(
                id = it.id,
                modifierId = it.modifierId,
                nameSnapshot = it.nameSnapshot,
                priceSnapshot = it.priceSnapshot
            )
        }
        val combos = orderComboSnapshotRepository.findByOrderItemId(orderItemId)
        val comboDtos = combos.map {
            OrderComboSnapshotResponseDto(
                id = it.id,
                comboChoiceId = it.comboChoiceId,
                menuItemId = it.menuItemId,
                nameSnapshot = it.nameSnapshot,
                priceOverrideSnapshot = it.priceOverrideSnapshot,
                surchargeSnapshot = it.surchargeSnapshot
            )
        }

        return OrderItemResponseDto(
            id = item.id,
            menuItemId = item.menuItemId,
            nameSnapshot = item.nameSnapshot,
            unitPriceSnapshot = item.unitPriceSnapshot,
            quantity = item.quantity,
            notes = item.notes,
            subtotal = item.subtotal,
            kitchenStatus = item.kitchenStatus,
            modifiers = modDtos,
            comboChoices = comboDtos
        )
    }

    private fun recalculateOrderTotal(orderId: String) {
        val order = orderRepository.findById(orderId).orElseThrow { IllegalArgumentException("Order not found") }
        val items = orderItemRepository.findByOrderId(orderId)
        val subtotals = items.map { it.subtotal }

        // Fetch Member Tier Discount Percentage if customer is linked
        val custId = order.customerId
        var memberDiscountPct = BigDecimal.ZERO
        if (custId != null && crmService != null) {
            try {
                val membership = crmService.getCustomerMembership(custId)
                memberDiscountPct = membership.discountPercentage
            } catch (_: Exception) {}
        }

        val manualDiscAmount = if (order.manualDiscountPercent != null && order.manualDiscountPercent!! > BigDecimal.ZERO) {
            val gross = subtotals.fold(BigDecimal.ZERO) { acc, s -> acc.add(s) }
            gross.multiply(order.manualDiscountPercent!!.divide(BigDecimal("100"), SCALE, ROUNDING))
        } else {
            BigDecimal.ZERO
        }

        val calc = calculationService.calculateFullOrderPipeline(
            itemSubtotals = subtotals,
            promotionDiscount = BigDecimal.ZERO,
            memberDiscountPercentage = memberDiscountPct,
            manualDiscount = manualDiscAmount,
            isVatInclusive = true
        )

        order.subtotalAmount = calc.grossItemTotal
        order.discountAmount = calc.totalDiscount
        order.taxAmount = calc.taxAmount
        order.totalAmount = calc.grandTotal
        order.updatedAt = Instant.now()
        orderRepository.save(order)
    }

    private fun generateOrderNumber(branchId: String): String {
        val dateStr = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.of("UTC")).format(Instant.now())
        val randomSeq = (1000..9999).random()
        return "ORD-$dateStr-$randomSeq"
    }
}

@RestController
@RequestMapping("/api/v1/orders")
class OrderController(
    private val orderService: OrderService
) {
    @GetMapping
    fun getOrders(@RequestParam(required = false) branchId: String?): ApiResponse<List<OrderResponseDto>> {
        return ApiResponse.success(orderService.listOrders(branchId))
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ORDER_CREATE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_CASHIER')")
    fun createOrder(@RequestBody req: CreateOrderRequest): ApiResponse<OrderResponseDto> {
        return ApiResponse.success(orderService.createOrder(req), "Order created successfully")
    }

    @GetMapping("/{id}")
    fun getOrder(@PathVariable id: String): ApiResponse<OrderResponseDto> {
        return ApiResponse.success(orderService.getOrderDetails(id))
    }

    @PatchMapping("/{id}/customer")
    @PreAuthorize("hasAuthority('ORDER_CREATE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_STORE_MANAGER') or hasAuthority('ROLE_CASHIER')")
    fun linkCustomer(
        @PathVariable id: String,
        @RequestBody req: LinkCustomerRequestDto,
        principal: Principal?
    ): ApiResponse<OrderResponseDto> {
        return ApiResponse.success(orderService.linkCustomer(id, req, principal?.name), "Customer linked to order successfully")
    }

    @PostMapping("/{id}/items")
    @PreAuthorize("hasAuthority('ORDER_CREATE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_CASHIER')")
    fun addItem(@PathVariable id: String, @RequestBody itemReq: OrderItemRequest): ApiResponse<OrderItemResponseDto> {
        return ApiResponse.success(orderService.addItemToOrder(id, itemReq), "Item added to order successfully")
    }

    @PostMapping("/{id}/discount")
    @PreAuthorize("hasAuthority('DISCOUNT_APPLY') or hasAuthority('DISCOUNT_OVERRIDE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_STORE_MANAGER') or hasAuthority('ROLE_CASHIER')")
    fun applyDiscount(@PathVariable id: String, @RequestBody req: ApplyManualDiscountRequest): ApiResponse<OrderResponseDto> {
        return ApiResponse.success(orderService.applyManualDiscount(id, req), "Discount applied successfully")
    }

    @PostMapping("/{id}/send-to-kitchen")
    @PreAuthorize("hasAuthority('ORDER_CREATE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_CASHIER')")
    fun sendToKitchen(@PathVariable id: String): ApiResponse<OrderResponseDto> {
        return ApiResponse.success(orderService.sendToKitchen(id), "Order sent to kitchen successfully")
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ORDER_CREATE') or hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_CASHIER')")
    fun transitionStatus(@PathVariable id: String, @RequestParam targetStatus: OrderStatus): ApiResponse<OrderResponseDto> {
        return ApiResponse.success(orderService.transitionOrderStatus(id, targetStatus), "Order status updated successfully")
    }
}
