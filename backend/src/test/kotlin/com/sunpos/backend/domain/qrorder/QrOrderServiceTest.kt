package com.sunpos.backend.domain.qrorder

import com.sunpos.backend.domain.catalog.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.Instant

@SpringBootTest
@ActiveProfiles("test")
class QrOrderServiceTest {

    @Autowired
    private lateinit var qrOrderService: QrOrderService

    @Autowired
    private lateinit var menuCategoryRepository: MenuCategoryRepository

    @Autowired
    private lateinit var menuItemRepository: MenuItemRepository

    @Autowired
    private lateinit var scheduledCatalogRepository: ScheduledCatalogRepository

    @Test
    fun `test create QR order, query by table, and update status lifecycle`() {
        val branchId = "branch-qr-test"
        val tableNumber = "T-99"

        // 1. Submit Order
        val createDto = CreateQrOrderDto(
            branchId = branchId,
            tableNumber = tableNumber,
            customerNote = "ขอช้อนส้อมเพิ่มด้วยครับ",
            items = listOf(
                CreateQrOrderItemDto(
                    productId = "prod-01",
                    productName = "ชาบูหมูคุโรบูตะ",
                    quantity = 2,
                    unitPrice = BigDecimal("350.00"),
                    options = "{\"broth\":\"ดำ\", \"spicy\":\"น้อย\"}",
                    note = "ไม่ใส่กระเทียม"
                ),
                CreateQrOrderItemDto(
                    productId = "prod-02",
                    productName = "ชาเขียวมัทฉะเย็น",
                    quantity = 1,
                    unitPrice = BigDecimal("45.00"),
                    options = "{\"sweetness\":\"50%\"}"
                )
            )
        )

        val created = qrOrderService.createOrder(createDto)
        assertNotNull(created.order.id)
        assertEquals(branchId, created.order.branchId)
        assertEquals(tableNumber, created.order.tableNumber)
        assertEquals(QrOrderStatus.pending, created.order.status)
        assertEquals("qr", created.order.source)
        // Expected total: 2 * 350.00 + 1 * 45.00 = 745.00
        assertEquals(BigDecimal("745.00"), created.order.totalAmount)
        assertEquals(2, created.items.size)

        // 2. Query Active Orders by Table
        val tableOrders = qrOrderService.getActiveOrdersForTable(branchId, tableNumber)
        assertTrue(tableOrders.any { it.order.id == created.order.id })

        // 3. Poll Pending Orders for Branch
        val pendingOrders = qrOrderService.getPendingOrdersForBranch(branchId)
        assertTrue(pendingOrders.any { it.order.id == created.order.id })

        // 4. Update Status through progression
        val updatedReceived = qrOrderService.updateOrderStatus(created.order.id, QrOrderStatus.received)
        assertEquals(QrOrderStatus.received, updatedReceived.order.status)

        val updatedPreparing = qrOrderService.updateOrderStatus(created.order.id, QrOrderStatus.preparing)
        assertEquals(QrOrderStatus.preparing, updatedPreparing.order.status)

        val updatedCompleted = qrOrderService.updateOrderStatus(created.order.id, QrOrderStatus.completed)
        assertEquals(QrOrderStatus.completed, updatedCompleted.order.status)

        // 5. Query Order Details by ID
        val details = qrOrderService.getOrderDetails(created.order.id)
        assertEquals(QrOrderStatus.completed, details.order.status)
        assertEquals(2, details.items.size)
        val shabuItem = details.items.first { it.productId == "prod-01" }
        assertEquals("ชาบูหมูคุโรบูตะ", shabuItem.productName)
        assertEquals("ไม่ใส่กระเทียม", shabuItem.note)
    }

    @Test
    fun `test get branch menu returns empty list when branch has no menu (no mock in production)`() {
        val emptyBranchId = "branch-empty-${System.currentTimeMillis()}"
        val menu = qrOrderService.getBranchMenu(emptyBranchId)
        assertEquals(emptyBranchId, menu.branchId)
        assertTrue(menu.categories.isEmpty(), "Categories must be empty when branch has no menu items (no mock)")
    }

    @Test
    fun `test get branch menu returns branch specific categories, active products, and scheduled pricing`() {
        val testBranchId = "branch-menu-test-${System.currentTimeMillis()}"

        // 1. Create branch-specific category
        val cat = menuCategoryRepository.save(
            MenuCategory(
                branchId = testBranchId,
                name = "พรีเมียมวากิว",
                sortOrder = 1,
                isActive = true
            )
        )

        // 2. Create active product
        val activeProd = menuItemRepository.save(
            MenuItem(
                branchId = testBranchId,
                categoryId = cat.id,
                name = "วากิว A5 สไลซ์",
                basePrice = BigDecimal("590.00"),
                availability = "AVAILABLE",
                isActive = true,
                sortOrder = 1
            )
        )

        // 3. Create inactive product (should be filtered out)
        menuItemRepository.save(
            MenuItem(
                branchId = testBranchId,
                categoryId = cat.id,
                name = "สินค้าหมดสต็อก",
                basePrice = BigDecimal("100.00"),
                availability = "SOLD_OUT",
                isActive = true
            )
        )

        // 4. Create scheduled catalog price override
        scheduledCatalogRepository.save(
            ScheduledCatalog(
                branchId = testBranchId,
                menuItemId = activeProd.id,
                scheduledPrice = BigDecimal("499.00"),
                startAt = Instant.now().minusSeconds(3600),
                endAt = Instant.now().plusSeconds(3600),
                status = ScheduledCatalogStatus.ACTIVE
            )
        )

        // 5. Query branch menu
        val menu = qrOrderService.getBranchMenu(testBranchId)
        assertEquals(testBranchId, menu.branchId)
        assertEquals(1, menu.categories.size)
        assertEquals("พรีเมียมวากิว", menu.categories[0].name)
        assertEquals(1, menu.categories[0].products.size)

        val product = menu.categories[0].products[0]
        assertEquals("วากิว A5 สไลซ์", product.name)
        // Price should reflect the scheduled branch price override (499.00) rather than basePrice (590.00)
        assertEquals(BigDecimal("499.00"), product.price)
        assertTrue(product.isAvailable)
    }

    @Test
    fun `test create public order with idempotency key returns identical order on retry`() {
        val idempotencyKey = "idemp-test-${System.currentTimeMillis()}"
        val req = CreatePublicOrderRequest(
            branchId = "BR001",
            tableNumber = "5",
            customerNote = "ไม่ใส่ผัก",
            items = listOf(
                PublicOrderItemRequest(
                    productId = "P001",
                    productName = "ข้าวผัดกุ้ง",
                    quantity = 1,
                    unitPrice = BigDecimal("120.00"),
                    options = mapOf("size" to "normal"),
                    note = "ไม่ใส่หอม"
                )
            )
        )

        // 1. First execution
        val resp1 = qrOrderService.createPublicOrder(req, idempotencyKey)
        assertNotNull(resp1.orderId)
        assertEquals("pending", resp1.status)
        assertEquals("Order received successfully", resp1.message)

        // 2. Duplicate retry with same idempotency key
        val resp2 = qrOrderService.createPublicOrder(req, idempotencyKey)
        assertEquals(resp1.orderId, resp2.orderId)
        assertEquals("pending", resp2.status)
        assertTrue(resp2.message.contains("Idempotent replay"))
    }
}
