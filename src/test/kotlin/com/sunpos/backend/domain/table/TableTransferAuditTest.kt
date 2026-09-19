package com.sunpos.backend.domain.table

import com.sunpos.backend.common.RecordedUpdate
import com.sunpos.backend.common.recordingJdbcTemplate
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate

/**
 * ADR 0029 – Session-Preserved Table Transfer Log & QR Cloud Continuity.
 *
 * A table move must (1) keep the existing session alive, (2) write an immutable
 * audit row into `table_transfer_logs`, and (3) redirect the active QR table
 * session to the destination table so the customer's mobile browser keeps
 * ordering without rescanning the QR code.
 */
class TableTransferAuditTest {

    private lateinit var jdbcTemplate: JdbcTemplate
    private lateinit var tableRepository: TableRepository
    private lateinit var tableSessionRepository: TableSessionRepository
    private lateinit var qrSessionRepository: QrTableSessionRepository
    private lateinit var qrTableSessionService: QrTableSessionService
    private lateinit var tableSessionService: TableSessionService

    /** Every `jdbcTemplate.update(sql, args...)` the services issued. */
    private val updates = mutableListOf<RecordedUpdate>()

    @BeforeEach
    fun setUp() {
        updates.clear()
        jdbcTemplate = recordingJdbcTemplate(updates)
        tableRepository = TableRepository(jdbcTemplate)
        tableSessionRepository = TableSessionRepository(jdbcTemplate)
        qrSessionRepository = QrTableSessionRepository(jdbcTemplate)
        qrTableSessionService = QrTableSessionService(
            qrTableSessionRepository = qrSessionRepository,
            tableRepository = tableRepository,
            qrOrderBaseUrl = "https://sunqrorder.vercel.app"
        )
        tableSessionService = TableSessionService(
            tableSessionRepository = tableSessionRepository,
            tableRepository = tableRepository,
            jdbcTemplate = jdbcTemplate,
            qrTableSessionService = qrTableSessionService
        )
    }

    private fun satDownTable(id: String, name: String, branchId: String = "br-100"): RestaurantTable =
        tableRepository.save(
            RestaurantTable(id = id, branchId = branchId, nameNumber = name, status = "AVAILABLE")
        )

    private fun transferLogUpdate(): RecordedUpdate =
        updates.first { it.sql.contains("INSERT INTO table_transfer_logs") }

    @Test
    fun `move table keeps the session, logs the transfer audit row, and redirects the QR session`() {
        val from = satDownTable("tbl-A01", "A01")
        val to = satDownTable("tbl-A02", "A02")

        val opened = tableSessionService.openSession(
            OpenSessionDto(tableId = from.id, branchId = "br-100", openedBy = "cashier-1")
        )
        assertEquals("ACTIVE", opened.status)
        assertEquals("OCCUPIED", from.status)

        val moved = tableSessionService.moveTable(
            MoveTableDto(
                fromTableId = "tbl-A01",
                toTableId = "tbl-A02",
                orderId = "ord-777",
                reason = "ลูกค้าขอย้ายไปโต๊ะใหญ่",
                transferredBy = "cashier-1"
            )
        )

        // ── Session preserved: same session id, now attached to the new table ──
        assertEquals(to.id, moved.id)
        assertEquals("OCCUPIED", moved.status)

        val stillActive = tableSessionRepository.findByTableIdAndStatus("tbl-A02", "ACTIVE")
        assertTrue(stillActive.isPresent, "Session must follow the customer to the new table")
        assertEquals(opened.id, stillActive.get().id)
        assertEquals("br-100", stillActive.get().branchId)

        // Nothing is left behind on the vacated table
        assertTrue(tableSessionRepository.findByTableIdAndStatus("tbl-A01", "ACTIVE").isEmpty)
        assertEquals("AVAILABLE", tableRepository.findById("tbl-A01").get().status)
        assertEquals("OCCUPIED", tableRepository.findById("tbl-A02").get().status)

        // ── Immutable audit row with full attribution ──
        val log = transferLogUpdate()
        assertEquals("ord-777", log.args[1])
        assertEquals("tbl-A01", log.args[2])
        assertEquals("tbl-A02", log.args[3])
        assertEquals("cashier-1", log.args[4])
        assertNotNull(log.args[5], "transferred_at timestamp must be recorded")
        assertEquals("ลูกค้าขอย้ายไปโต๊ะใหญ่", log.args[6])

        // ── Active QR session redirected so the customer's phone follows along ──
        val qrRedirect = updates.firstOrNull { it.sql.contains("UPDATE qr_table_sessions") }
        assertNotNull(qrRedirect, "Active QR session must be redirected to the destination table")
        assertTrue(qrRedirect!!.sql.contains("status = 'ACTIVE'"), "Only active QR sessions move")
        assertEquals("tbl-A02", qrRedirect.args[0])
        assertEquals("A02", qrRedirect.args[1])
        assertEquals("tbl-A01", qrRedirect.args[2])
    }

    @Test
    fun `move table falls back to sensible defaults when reason and operator are omitted`() {
        satDownTable("tbl-B01", "B01")
        satDownTable("tbl-B02", "B02")
        tableSessionService.openSession(OpenSessionDto(tableId = "tbl-B01", branchId = "br-100"))

        tableSessionService.moveTable(MoveTableDto(fromTableId = "tbl-B01", toTableId = "tbl-B02"))

        val log = transferLogUpdate()
        assertEquals("POS", log.args[4])
        assertEquals("ลูกค้าย้ายโต๊ะ", log.args[6])
        // No explicit orderId: the active session id is attributed instead
        assertFalse((log.args[1] as String).isBlank())
    }

    @Test
    fun `redirected QR session reports a table mismatch so the browser can notify the customer`() {
        // The session row the cloud keeps after a transfer points at the new table,
        // while the URL the customer still holds mentions the old one.
        val redirected = QrTableSession(
            branchId = "br-100",
            tableId = "tbl-A02",
            tableNumber = "A02",
            sessionToken = "tok-abc123",
            status = "ACTIVE"
        )
        qrSessionRepository.save(redirected)

        val resolved = qrTableSessionService.findActiveSessionByToken("tok-abc123")
        assertTrue(resolved.isPresent)
        assertEquals("A02", resolved.get().tableNumber)

        // isTransferred === the requested table no longer matches the session's table
        assertFalse(qrTableSessionService.tableNumbersMatch("A01", resolved.get().tableNumber))
        assertTrue(qrTableSessionService.tableNumbersMatch("a-02", resolved.get().tableNumber))
    }
}
