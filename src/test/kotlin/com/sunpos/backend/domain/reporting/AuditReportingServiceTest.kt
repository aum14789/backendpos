package com.sunpos.backend.domain.reporting

import com.sunpos.backend.common.rowFedJdbcTemplate
import com.sunpos.backend.domain.crm.CustomerRepository
import com.sunpos.backend.domain.crm.PointLedgerRepository
import com.sunpos.backend.domain.inventory.InventoryStockRepository
import com.sunpos.backend.domain.inventory.StockMovementRepository
import com.sunpos.backend.domain.order.OrderRepository
import com.sunpos.backend.domain.payment.PaymentTransactionRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.sql.Timestamp
import java.time.Instant

/**
 * ADR 0029 – Backoffice audit reporting endpoints.
 *
 * These tests feed canned SQL rows through the *real* RowMappers so that both
 * the column mapping and the aggregation math (void totals, waste vs restock
 * split, satang → baht conversion) are verified without a live database.
 */
class AuditReportingServiceTest {

    private fun service(jdbcTemplate: JdbcTemplate?): ReportingService = ReportingService(
        orderRepository = mock(OrderRepository::class.java),
        paymentRepository = mock(PaymentTransactionRepository::class.java),
        inventoryStockRepository = mock(InventoryStockRepository::class.java),
        stockMovementRepository = mock(StockMovementRepository::class.java),
        customerRepository = mock(CustomerRepository::class.java),
        pointLedgerRepository = mock(PointLedgerRepository::class.java),
        jdbcTemplate = jdbcTemplate
    )

    private fun assertMoney(expected: String, actual: BigDecimal) {
        assertEquals(0, actual.compareTo(BigDecimal(expected)), "expected $expected but was $actual")
    }

    private fun ts(iso: String): Timestamp = Timestamp.from(Instant.parse(iso))

    @Test
    fun `void audit report splits waste from restock and totals money per branch`() {
        val queries = mutableListOf<String>()
        val rows = listOf(
            mapOf(
                "id" to "oi-1", "order_id" to "ord-1", "name_snapshot" to "Pad Thai",
                "quantity" to BigDecimal("2"), "unit_price_snapshot" to BigDecimal("80.00"),
                "is_voided" to true, "status" to "VOIDED",
                "voided_by" to "cashier-1", "void_approved_by" to "manager-1",
                "voided_at" to ts("2026-09-19T04:10:00Z"), "void_reason" to "ลูกค้าแจ้งว่าช้าเกินไป",
                "is_waste" to true, "ordered_by" to "staff-cashier-1", "ordered_at" to ts("2026-09-19T03:50:00Z")
            ),
            mapOf(
                "id" to "oi-2", "order_id" to "ord-1", "name_snapshot" to "Coke",
                "quantity" to BigDecimal("1"), "unit_price_snapshot" to BigDecimal("25.00"),
                "is_voided" to true, "status" to "VOIDED",
                "voided_by" to "cashier-1", "void_approved_by" to "manager-1",
                "voided_at" to ts("2026-09-19T04:11:00Z"), "void_reason" to "ลูกค้าสั่งผิด",
                "is_waste" to false, "ordered_by" to "qr-guest", "ordered_at" to ts("2026-09-19T03:55:00Z")
            ),
            mapOf(
                "id" to "oi-3", "order_id" to "ord-2", "name_snapshot" to "Spring Roll",
                "quantity" to BigDecimal("3"), "unit_price_snapshot" to BigDecimal("10.00"),
                "is_voided" to true, "status" to "VOIDED",
                "voided_by" to "cashier-2", "void_approved_by" to "manager-1",
                "voided_at" to ts("2026-09-19T05:00:00Z"), "void_reason" to "ทำหล่น",
                "is_waste" to true, "ordered_by" to "staff-cashier-2", "ordered_at" to ts("2026-09-19T04:40:00Z")
            )
        )

        val report = service(rowFedJdbcTemplate(rows, queries)).getVoidAuditReport("br-100")

        assertEquals(3, report.totalCount)
        assertMoney("215.00", report.totalAmount)

        assertEquals(2, report.wasteCount)
        assertMoney("190.00", report.wasteAmount)
        assertEquals(1, report.restockCount)
        assertMoney("25.00", report.restockAmount)

        val first = report.items.first { it.id == "oi-1" }
        assertEquals("Pad Thai", first.name)
        assertMoney("160.00", first.totalPrice)
        assertTrue(first.isWaste)
        assertEquals("ลูกค้าแจ้งว่าช้าเกินไป", first.voidReason)
        assertEquals("staff-cashier-1", first.orderedBy)
        assertEquals("2026-09-19T04:10:00Z", first.voidedAt)

        // The report is branch-scoped and only counts soft-voided lines
        val sql = queries.single()
        assertTrue(sql.contains("is_voided = true"), sql)
        assertTrue(sql.contains("o.branch_id = ?"), sql)
    }

    @Test
    fun `void audit report is empty rather than throwing when no database is configured`() {
        val report = service(null).getVoidAuditReport(null)

        assertEquals(0, report.totalCount)
        assertTrue(report.items.isEmpty())
        assertMoney("0", report.totalAmount)
        assertMoney("0", report.wasteAmount)
        assertMoney("0", report.restockAmount)
    }

    @Test
    fun `table transfer audit report resolves table names and falls back to ids`() {
        val rows = listOf(
            mapOf(
                "id" to "ttl-1", "order_id" to "ord-777",
                "from_table_id" to "tbl-A01", "to_table_id" to "tbl-A02",
                "transferred_by" to "cashier-1", "transferred_at" to ts("2026-09-19T06:00:00Z"),
                "reason" to "ลูกค้าขอย้ายไปโต๊ะใหญ่",
                "from_table_name" to "A01", "to_table_name" to "A02"
            ),
            mapOf(
                "id" to "ttl-2", "order_id" to "ord-778",
                "from_table_id" to "tbl-B01", "to_table_id" to "tbl-B02",
                "transferred_by" to "cashier-2", "transferred_at" to ts("2026-09-19T07:30:00Z"),
                "reason" to null,
                "from_table_name" to null, "to_table_name" to null
            )
        )

        val logs = service(rowFedJdbcTemplate(rows)).getTableTransferAuditReport("br-100")

        assertEquals(2, logs.size)

        val first = logs.first { it.id == "ttl-1" }
        assertEquals("A01", first.fromTableName)
        assertEquals("A02", first.toTableName)
        assertEquals("cashier-1", first.transferredBy)
        assertEquals("2026-09-19T06:00:00Z", first.transferredAt)
        assertEquals("ลูกค้าขอย้ายไปโต๊ะใหญ่", first.reason)

        // Missing join rows must not blank out the audit trail
        val second = logs.first { it.id == "ttl-2" }
        assertEquals("tbl-B01", second.fromTableName)
        assertEquals("tbl-B02", second.toTableName)
        assertNull(second.reason)
    }

    @Test
    fun `bill check audit report converts satang snapshots to baht with the check sequence`() {
        val queries = mutableListOf<String>()
        val rows = listOf(
            mapOf(
                "id" to "bcl-1", "order_id" to "ord-1", "check_sequence" to 1,
                "checked_at" to ts("2026-09-19T08:00:00Z"), "checked_by" to "cashier-1",
                "snapshot_total_satang" to 10000L
            ),
            mapOf(
                "id" to "bcl-2", "order_id" to "ord-1", "check_sequence" to 2,
                "checked_at" to ts("2026-09-19T08:25:00Z"), "checked_by" to "manager-1",
                "snapshot_total_satang" to 7000L
            )
        )

        val logs = service(rowFedJdbcTemplate(rows, queries)).getBillCheckAuditReport("ord-1")

        assertEquals(2, logs.size)
        assertEquals(listOf(1, 2), logs.map { it.checkSequence })
        assertEquals(10000L, logs[0].snapshotTotalSatang)
        assertMoney("100.00", logs[0].snapshotTotalBaht)
        assertEquals(7000L, logs[1].snapshotTotalSatang)
        assertMoney("70.00", logs[1].snapshotTotalBaht)
        assertEquals("manager-1", logs[1].checkedBy)
        assertEquals("2026-09-19T08:25:00Z", logs[1].checkedAt)

        // The post-check discrepancy guard needs the latest snapshot, so the
        // report must be ordered by check sequence ascending.
        assertTrue(queries.single().contains("ORDER BY bcl.order_id, bcl.check_sequence ASC"))
    }
}
