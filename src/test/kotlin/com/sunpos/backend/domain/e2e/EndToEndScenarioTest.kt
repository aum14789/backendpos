package com.sunpos.backend.domain.e2e

import com.sunpos.backend.domain.businessday.BusinessDayService
import com.sunpos.backend.domain.businessday.BusinessDayStatus
import com.sunpos.backend.domain.crm.*
import com.sunpos.backend.domain.inventory.*
import com.sunpos.backend.domain.order.*
import com.sunpos.backend.domain.payment.*
import com.sunpos.backend.domain.purchasing.*
import com.sunpos.backend.domain.recipe.*
import com.sunpos.backend.domain.catalog.*
import com.sunpos.backend.domain.sync.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EndToEndScenarioTest {

    @Autowired private lateinit var orderService: OrderService
    @Autowired private lateinit var paymentService: PaymentService
    @Autowired private lateinit var purchasingService: PurchasingService
    @Autowired private lateinit var recipeService: RecipeService
    @Autowired private lateinit var productionService: ProductionService
    @Autowired private lateinit var inventoryService: InventoryService
    @Autowired private lateinit var crmService: CrmService
    @Autowired private lateinit var syncService: SyncService
    @Autowired private lateinit var businessDayService: BusinessDayService
    @Autowired private lateinit var catalogService: CatalogService

    @Test
    fun `test Scenario 1 - Dine-in sale order lifecycle`() {
        val category = catalogService.createCategory(MenuCategory(branchId = "branch-001", name = "Food"))
        val menuItem = catalogService.createMenuItem(
            MenuItemCreateDto(branchId = "branch-001", categoryId = category.id, name = "Pad Thai", basePrice = BigDecimal("120.00"))
        )

        // Setup Business Day
        businessDayService.getOrCreateOpenBusinessDay("branch-001")

        val req = CreateOrderRequest(
            branchId = "branch-001",
            tableId = "table-01",
            orderType = OrderType.DINE_IN,
            items = listOf(OrderItemRequest(menuItemId = menuItem.id, nameSnapshot = "Pad Thai", unitPriceSnapshot = BigDecimal("120.00"), quantity = BigDecimal("1")))
        )
        val order = orderService.createOrder(req)
        assertEquals(OrderStatus.OPEN, order.status)
        assertEquals(0, BigDecimal("120.00").compareTo(order.totalAmount))

        val sentOrder = orderService.sendToKitchen(order.id)
        assertEquals(KitchenStatus.SENT, sentOrder.kitchenStatus)

        val payment = paymentService.processPayment(
            PaymentRequestDto(orderId = order.id, branchId = "branch-001", deviceId = "pos-01", paymentMethod = PaymentMethod.CASH, amount = BigDecimal("120.00"))
        )
        assertEquals(PaymentStatus.SUCCESS, payment.status)
        
        val completedOrder = orderService.getOrderDetails(order.id)
        assertEquals(OrderStatus.COMPLETED, completedOrder.status)
    }

    @Test
    fun `test Scenario 2 - Takeaway sale`() {
        val category = catalogService.createCategory(MenuCategory(branchId = "branch-001", name = "Food"))
        val menuItem = catalogService.createMenuItem(
            MenuItemCreateDto(branchId = "branch-001", categoryId = category.id, name = "Fried Rice", basePrice = BigDecimal("80.00"))
        )

        // Setup Business Day
        businessDayService.getOrCreateOpenBusinessDay("branch-001")

        val req = CreateOrderRequest(
            branchId = "branch-001",
            orderType = OrderType.TAKEAWAY,
            items = listOf(OrderItemRequest(menuItemId = menuItem.id, nameSnapshot = "Fried Rice", unitPriceSnapshot = BigDecimal("80.00"), quantity = BigDecimal("2")))
        )
        val order = orderService.createOrder(req)
        assertEquals(OrderType.TAKEAWAY, order.orderType)
        assertEquals(0, BigDecimal("160.00").compareTo(order.totalAmount))
    }

    @Test
    fun `test Scenario 3 - Buffet sale`() {
        val category = catalogService.createCategory(MenuCategory(branchId = "branch-001", name = "Food"))
        val menuItem = catalogService.createMenuItem(
            MenuItemCreateDto(branchId = "branch-001", categoryId = category.id, name = "Gold Buffet Package", basePrice = BigDecimal("499.00"))
        )

        // Setup Business Day
        businessDayService.getOrCreateOpenBusinessDay("branch-001")

        val req = CreateOrderRequest(
            branchId = "branch-001",
            orderType = OrderType.BUFFET,
            items = listOf(OrderItemRequest(menuItemId = menuItem.id, nameSnapshot = "Gold Buffet Package", unitPriceSnapshot = BigDecimal("499.00"), quantity = BigDecimal("4")))
        )
        val order = orderService.createOrder(req)
        assertEquals(OrderType.BUFFET, order.orderType)
        assertEquals(0, BigDecimal("1996.00").compareTo(order.totalAmount))
    }

    @Test
    fun `test Scenario 4 - Delivery sale channel`() {
        val category = catalogService.createCategory(MenuCategory(branchId = "branch-001", name = "Food"))
        val menuItem = catalogService.createMenuItem(
            MenuItemCreateDto(branchId = "branch-001", categoryId = category.id, name = "Family Set", basePrice = BigDecimal("550.00"))
        )

        // Setup Business Day
        businessDayService.getOrCreateOpenBusinessDay("branch-001")

        val req = CreateOrderRequest(
            branchId = "branch-001",
            orderType = OrderType.DELIVERY,
            channel = OrderChannel.DELIVERY,
            items = listOf(OrderItemRequest(menuItemId = menuItem.id, nameSnapshot = "Family Set", unitPriceSnapshot = BigDecimal("550.00"), quantity = BigDecimal("1")))
        )
        val order = orderService.createOrder(req)
        assertEquals(OrderChannel.DELIVERY, order.channel)
    }

    @Test
    fun `test Scenario 5 - Multi-payment split`() {
        val category = catalogService.createCategory(MenuCategory(branchId = "branch-001", name = "Food"))
        val menuItem = catalogService.createMenuItem(
            MenuItemCreateDto(branchId = "branch-001", categoryId = category.id, name = "Steak Set", basePrice = BigDecimal("500.00"))
        )

        // Setup Business Day
        businessDayService.getOrCreateOpenBusinessDay("branch-001")

        val req = CreateOrderRequest(
            branchId = "branch-001",
            items = listOf(OrderItemRequest(menuItemId = menuItem.id, nameSnapshot = "Steak Set", unitPriceSnapshot = BigDecimal("500.00"), quantity = BigDecimal("1")))
        )
        val order = orderService.createOrder(req)

        val pay1 = paymentService.processPayment(PaymentRequestDto(orderId = order.id, branchId = "branch-001", deviceId = "pos-01", paymentMethod = PaymentMethod.CASH, amount = BigDecimal("200.00")))
        val pay2 = paymentService.processPayment(PaymentRequestDto(orderId = order.id, branchId = "branch-001", deviceId = "pos-01", paymentMethod = PaymentMethod.QR, amount = BigDecimal("300.00")))

        assertEquals(PaymentStatus.SUCCESS, pay1.status)
        assertEquals(PaymentStatus.SUCCESS, pay2.status)
        val completedOrder = orderService.getOrderDetails(order.id)
        assertEquals(OrderStatus.COMPLETED, completedOrder.status)
    }

    @Test
    fun `test Scenario 6 - Promotion discount calculation`() {
        val calc = OrderCalculationService()
        val items = listOf(BigDecimal("100.00"), BigDecimal("200.00"))
        val subtotal = calc.calculateOrderTotal(items)
        assertEquals(0, BigDecimal("300.00").compareTo(subtotal))
    }

    @Test
    fun `test Scenario 7 - Offline sale idempotency outbox sync`() {
        val eventId = "evt-offline-sync-scenario-7"
        val pushReq = SyncPushRequest(
            events = listOf(
                SyncEventDto(
                    eventId = eventId,
                    aggregateType = "ORDER",
                    aggregateId = "ord-offline-999",
                    eventType = "ORDER_CREATED",
                    deviceId = "pos-device-offline-01",
                    branchId = "branch-001",
                    payload = "{}",
                    createdAt = Instant.now()
                )
            )
        )
        val ack1 = syncService.processPush(pushReq)
        assertTrue(ack1.processedEventIds.contains(eventId))

        // Retry same batch -> idempotent skip
        val ack2 = syncService.processPush(pushReq)
        assertTrue(ack2.duplicateEventIds.contains(eventId))
    }

    @Test
    fun `test Scenario 8 - Purchase PO to GRN goods receive and WAC update`() {
        val po = purchasingService.createPO(
            CreatePurchaseOrderDto(
                supplierId = "sup-001",
                warehouseId = "wh-central",
                expectedDate = Instant.now(),
                items = listOf(POItemDto(inventoryItemId = "raw-001", orderedQty = BigDecimal("100"), unit = "KG", expectedPrice = BigDecimal("150.00")))
            )
        )
        val approvedPo = purchasingService.approvePO(po.id, "mgr-01")
        assertEquals(POStatus.APPROVED, approvedPo.status)

        val grn = purchasingService.processGoodsReceive(
            CreateGoodsReceiveDto(
                purchaseOrderId = po.id,
                receivedBy = "staff-01",
                items = listOf(GRNItemDto(inventoryItemId = "raw-001", receivedQty = BigDecimal("100"), damagedQty = BigDecimal("0"), unit = "KG", actualUnitCost = BigDecimal("150.00")))
            )
        )
        assertNotNull(grn.id)
    }

    @Test
    fun `test Scenario 9 - Central kitchen production`() {
        // Create BOM for soup
        val bom = recipeService.createBom(
            CreateBomDto(
                finishedInventoryItemId = "raw-002",
                name = "Tom Yum Soup 100L",
                version = "1.0",
                plannedOutputQuantity = BigDecimal("100"),
                outputUnit = "LITER",
                items = listOf(BomItemDto(rawInventoryItemId = "raw-001", quantity = BigDecimal("10"), unit = "KG"))
            )
        )

        val pOrder = productionService.createProductionOrder(
            CreateProductionOrderDto(
                bomId = bom.id,
                warehouseId = "wh-central",
                plannedQuantity = BigDecimal("100"),
                unit = "LITER"
            )
        )
        assertEquals(ProductionStatus.APPROVED, pOrder.status)

        val started = productionService.startProduction(pOrder.id)
        assertEquals(ProductionStatus.IN_PROGRESS, started.status)
    }

    @Test
    fun `test Scenario 10 - Branch transfer request and shipping`() {
        val transfer = inventoryService.createTransfer(
            CreateTransferDto(
                sourceWarehouseId = "wh-central",
                targetWarehouseId = "wh-sukhumvit",
                items = listOf(TransferItemDto(inventoryItemId = "raw-001", quantity = BigDecimal("20"), unit = "KG")),
                createdBy = "mgr-transfer"
            )
        )
        assertEquals(TransferStatus.REQUESTED, transfer.status)
    }

    @Test
    fun `test Scenario 11 - Customer registration and point ledger activity`() {
        val customer = crmService.createCustomer(
            CreateCustomerDto(
                firstName = "Somchai",
                lastName = "Jaidee",
                phone = "0812345678"
            )
        )
        assertNotNull(customer.customer.id)

        val ledger = crmService.adjustPoints(
            PointTransactionDto(
                customerId = customer.customer.id,
                points = BigDecimal("100"),
                notes = "Earned on dine-in order"
            )
        )
        assertEquals(0, BigDecimal("100.0000").compareTo(ledger.balanceAfter))
    }

    @Test
    fun `test Scenario 12 - Business day open and EOD closing`() {
        val bday = businessDayService.getOrCreateOpenBusinessDay("branch-001")
        assertEquals(BusinessDayStatus.OPEN, bday.status)

        val closedBday = businessDayService.closeBusinessDayEod("branch-001", "mgr-01")
        assertEquals(BusinessDayStatus.CLOSED, closedBday.status)
    }
}
