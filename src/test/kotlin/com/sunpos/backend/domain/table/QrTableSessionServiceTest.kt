package com.sunpos.backend.domain.table

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.springframework.jdbc.core.JdbcTemplate
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

class QrTableSessionServiceTest {

    private lateinit var jdbcTemplate: JdbcTemplate
    private lateinit var qrTableSessionRepository: QrTableSessionRepository
    private lateinit var tableRepository: TableRepository
    private lateinit var qrTableSessionService: QrTableSessionService

    @BeforeEach
    fun setUp() {
        jdbcTemplate = mock(JdbcTemplate::class.java)
        // Using real repository with in-memory localCache fallback from JdbcRepository
        qrTableSessionRepository = QrTableSessionRepository(jdbcTemplate)
        tableRepository = TableRepository(jdbcTemplate)
        qrTableSessionService = QrTableSessionService(
            qrTableSessionRepository = qrTableSessionRepository,
            tableRepository = tableRepository,
            qrOrderBaseUrl = "https://sunqrorder.vercel.app"
        )
    }

    @Test
    fun `createSession should generate 16-character token and set status ACTIVE`() {
        val branchId = "branch-001"
        val tableId = "table-001"
        val tableNumber = "T-01"
        val openedBy = "user-cashier-1"

        val session = qrTableSessionService.createSession(
            branchId = branchId,
            tableId = tableId,
            tableNumber = tableNumber,
            openedBy = openedBy
        )

        assertNotNull(session.id)
        assertEquals(branchId, session.branchId)
        assertEquals(tableId, session.tableId)
        assertEquals(tableNumber, session.tableNumber)
        assertEquals(16, session.sessionToken.length)
        assertEquals("ACTIVE", session.status)
        assertEquals(openedBy, session.openedBy)
        assertNotNull(session.openedAt)
        assertNull(session.closedAt)
    }

    @Test
    fun `createSession should close any previous active session for the table`() {
        val branchId = "branch-001"
        val tableId = "table-001"
        val tableNumber = "T-01"

        // First session
        val firstSession = qrTableSessionService.createSession(
            branchId = branchId,
            tableId = tableId,
            tableNumber = tableNumber,
            openedBy = "user-1"
        )
        assertEquals("ACTIVE", firstSession.status)

        // Second session on same table
        val secondSession = qrTableSessionService.createSession(
            branchId = branchId,
            tableId = tableId,
            tableNumber = tableNumber,
            openedBy = "user-2"
        )

        assertEquals("ACTIVE", secondSession.status)
        assertNotEquals(firstSession.sessionToken, secondSession.sessionToken)

        // First session should now be CLOSED
        val updatedFirst = qrTableSessionRepository.findById(firstSession.id).get()
        assertEquals("CLOSED", updatedFirst.status)
        assertNotNull(updatedFirst.closedAt)
    }

    @Test
    fun `getActiveSession should return active session by branchId and tableNumber`() {
        val branchId = "branch-001"
        val tableId = "table-001"
        val tableNumber = "T-01"

        qrTableSessionService.createSession(branchId, tableId, tableNumber)

        val activeOpt = qrTableSessionService.getActiveSession(branchId, tableNumber)
        assertTrue(activeOpt.isPresent)
        assertEquals(tableNumber, activeOpt.get().tableNumber)
        assertEquals("ACTIVE", activeOpt.get().status)
    }

    @Test
    fun `getActiveSessionByTableId should return active session`() {
        val branchId = "branch-001"
        val tableId = "table-002"
        val tableNumber = "T-02"

        qrTableSessionService.createSession(branchId, tableId, tableNumber)

        val activeOpt = qrTableSessionService.getActiveSessionByTableId(tableId)
        assertTrue(activeOpt.isPresent)
        assertEquals(tableId, activeOpt.get().tableId)
    }

    @Test
    fun `closeSession should close active session by tableId or sessionId`() {
        val branchId = "branch-001"
        val tableId = "table-003"
        val tableNumber = "T-03"

        val session = qrTableSessionService.createSession(branchId, tableId, tableNumber)
        assertEquals("ACTIVE", session.status)

        // Close by tableId
        val closed = qrTableSessionService.closeSession(tableId)
        assertEquals(1, closed.size)
        assertEquals("CLOSED", closed[0].status)
        assertNotNull(closed[0].closedAt)

        // Verify active session is now empty
        val activeOpt = qrTableSessionService.getActiveSessionByTableId(tableId)
        assertTrue(activeOpt.isEmpty)
    }

    @Test
    fun `regenerateToken should produce new token for reprint`() {
        val branchId = "branch-001"
        val tableId = "table-004"
        val tableNumber = "T-04"

        val session = qrTableSessionService.createSession(branchId, tableId, tableNumber)
        val originalToken = session.sessionToken

        val reprinted = qrTableSessionService.regenerateToken(session.id)
        assertEquals(session.id, reprinted.id)
        assertNotEquals(originalToken, reprinted.sessionToken)
        assertEquals(16, reprinted.sessionToken.length)
        assertEquals("ACTIVE", reprinted.status)
    }

    @Test
    fun `regenerateToken by tableId should also work`() {
        val branchId = "branch-001"
        val tableId = "table-005"
        val tableNumber = "T-05"

        val session = qrTableSessionService.createSession(branchId, tableId, tableNumber)
        val originalToken = session.sessionToken

        val reprinted = qrTableSessionService.regenerateToken(tableId)
        assertNotEquals(originalToken, reprinted.sessionToken)
    }

    @Test
    fun `buildQrOrderUrl returns correctly formatted URL`() {
        val branchId = "b123"
        val tableNumber = "A-10"
        val token = "xyz987token1234"

        val url = qrTableSessionService.buildQrOrderUrl(branchId, tableNumber, token)
        assertEquals("https://sunqrorder.vercel.app/menu/b123/A-10?token=xyz987token1234", url)
    }

    @Test
    fun `isSessionTokenValid returns true for valid token and false for invalid`() {
        val branchId = "branch-001"
        val tableId = "table-006"
        val tableNumber = "T-06"

        val session = qrTableSessionService.createSession(branchId, tableId, tableNumber)

        assertTrue(qrTableSessionService.isSessionTokenValid(branchId, tableNumber, session.sessionToken))
        assertFalse(qrTableSessionService.isSessionTokenValid(branchId, tableNumber, "wrong-token"))
        assertFalse(qrTableSessionService.isSessionTokenValid(branchId, "T-99", session.sessionToken))
    }

    @Test
    fun `isSessionTokenValid returns false if expired`() {
        val branchId = "branch-001"
        val tableId = "table-007"
        val tableNumber = "T-07"
        val past = Instant.now().minus(1, ChronoUnit.HOURS)

        val session = qrTableSessionService.createSession(
            branchId = branchId,
            tableId = tableId,
            tableNumber = tableNumber,
            expiresAt = past
        )

        assertFalse(qrTableSessionService.isSessionTokenValid(branchId, tableNumber, session.sessionToken))
    }

    @Test
    fun `TableSessionService automatically creates and closes QR Table Session`() {
        val tableSessionRepo = TableSessionRepository(jdbcTemplate)
        val testTableRepo = TableRepository(jdbcTemplate)
        val mockQrService = mock(QrTableSessionService::class.java)

        val tableSessionService = TableSessionService(
            tableSessionRepository = tableSessionRepo,
            tableRepository = testTableRepo,
            qrTableSessionService = mockQrService
        )

        val table = RestaurantTable(
            id = "tbl-100",
            branchId = "br-100",
            nameNumber = "Table-100",
            isActive = true,
            status = "AVAILABLE"
        )
        testTableRepo.save(table)

        // Test openSession
        val opened = tableSessionService.openSession(
            OpenSessionDto(tableId = "tbl-100", branchId = "br-100", openedBy = "staff-1")
        )
        assertNotNull(opened)
        verify(mockQrService, times(1)).createSession("br-100", "tbl-100", "Table-100", "staff-1")

        // Test closeSession
        tableSessionService.closeSession(opened.id, "staff-1")
        verify(mockQrService, times(1)).closeSession("tbl-100")
    }

    @Test
    fun `QrTableSessionController endpoints work correctly`() {
        val mockTableRepo = mock(TableRepository::class.java)
        val table = RestaurantTable(
            id = "tbl-200",
            branchId = "br-200",
            nameNumber = "Table-200",
            isActive = true,
            status = "AVAILABLE"
        )
        `when`(mockTableRepo.findById("tbl-200")).thenReturn(Optional.of(table))

        val controller = QrTableSessionController(qrTableSessionService, mockTableRepo)

        // 1. POST /{tableId}/qr-session
        val openResponse = controller.openQrSession("tbl-200", OpenQrSessionRequest(openedBy = "user-99"))
        assertEquals(201, openResponse.statusCode.value())
        assertNotNull(openResponse.body?.data)
        val responseDto = openResponse.body!!.data!!
        assertEquals("tbl-200", responseDto.tableId)
        assertEquals("Table-200", responseDto.tableNumber)
        assertTrue(responseDto.qrOrderUrl.contains("token="))

        // 2. GET /{tableId}/qr-session/active
        val activeResponse = controller.getActiveQrSession("tbl-200")
        assertEquals(200, activeResponse.statusCode.value())
        assertEquals(responseDto.sessionToken, activeResponse.body?.data?.sessionToken)

        // 3. POST /{tableId}/qr-session/reprint
        val reprintResponse = controller.reprintQrSession("tbl-200")
        assertEquals(200, reprintResponse.statusCode.value())
        assertNotEquals(responseDto.sessionToken, reprintResponse.body?.data?.sessionToken)

        // 4. POST /{tableId}/qr-session/close
        val closeResponse = controller.closeQrSession("tbl-200")
        assertEquals(200, closeResponse.statusCode.value())
        assertEquals(1, closeResponse.body?.data?.size)
        assertEquals("CLOSED", closeResponse.body?.data?.get(0)?.status)

        // 5. GET after close should return 404
        val afterClose = controller.getActiveQrSession("tbl-200")
        assertEquals(404, afterClose.statusCode.value())
    }
}
