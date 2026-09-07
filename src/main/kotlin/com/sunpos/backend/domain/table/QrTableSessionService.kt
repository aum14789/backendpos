package com.sunpos.backend.domain.table

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Instant
import java.util.Optional

@Service
class QrTableSessionService(
    private val qrTableSessionRepository: QrTableSessionRepository,
    private val tableRepository: TableRepository,
    @Value("\${app.qr-order.base-url:\${sunpos.qr.base-url:\${QR_ORDER_BASE_URL:https://sunqrorder.vercel.app}}}")
    private val qrOrderBaseUrl: String = "https://sunqrorder.vercel.app"
) {
    private val logger = LoggerFactory.getLogger(QrTableSessionService::class.java)
    private val secureRandom = SecureRandom()
    private val tokenChars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

    private fun generateSecureToken(length: Int = 16): String {
        val sb = StringBuilder(length)
        for (i in 0 until length) {
            val idx = secureRandom.nextInt(tokenChars.length)
            sb.append(tokenChars[idx])
        }
        return sb.toString()
    }

    fun buildQrOrderUrl(branchId: String, tableNumber: String, sessionToken: String): String {
        val cleanBase = qrOrderBaseUrl.trim().trimEnd('/')
        val encodedBranch = java.net.URLEncoder.encode(branchId.trim(), Charsets.UTF_8).replace("+", "%20")
        val encodedTable = java.net.URLEncoder.encode(tableNumber.trim(), Charsets.UTF_8).replace("+", "%20")
        return "$cleanBase/menu/$encodedBranch/$encodedTable?token=$sessionToken"
    }

    fun normalizeTableNumber(value: String): String {
        return value.trim().lowercase().replace(Regex("[^a-z0-9ก-๙]"), "")
    }

    fun tableNumbersMatch(left: String, right: String): Boolean {
        if (left.equals(right, ignoreCase = true)) return true
        val a = normalizeTableNumber(left)
        val b = normalizeTableNumber(right)
        return a.isNotBlank() && a == b
    }

    fun toResponseDto(session: QrTableSession): QrSessionResponseDto {
        return QrSessionResponseDto(
            id = session.id,
            branchId = session.branchId,
            tableId = session.tableId,
            tableNumber = session.tableNumber,
            sessionToken = session.sessionToken,
            qrOrderUrl = buildQrOrderUrl(session.branchId, session.tableNumber, session.sessionToken),
            status = session.status,
            openedAt = session.openedAt,
            closedAt = session.closedAt,
            openedBy = session.openedBy,
            expiresAt = session.expiresAt
        )
    }

    @Transactional
    fun createSession(
        branchId: String,
        tableId: String,
        tableNumber: String,
        openedBy: String? = null,
        expiresAt: Instant? = null
    ): QrTableSession {
        require(branchId.isNotBlank()) { "branchId cannot be blank" }
        require(tableId.isNotBlank()) { "tableId cannot be blank" }
        require(tableNumber.isNotBlank()) { "tableNumber cannot be blank" }

        // 1. Close any existing ACTIVE session for this table
        val existingActiveByTable = qrTableSessionRepository.findAllByTableIdAndStatus(tableId, QrTableSessionStatus.ACTIVE.name)
        val existingActiveByBranchAndNum = qrTableSessionRepository.findAllByBranchIdAndTableNumberAndStatus(
            branchId, tableNumber, QrTableSessionStatus.ACTIVE.name
        )
        val toClose = (existingActiveByTable + existingActiveByBranchAndNum).distinctBy { it.id }

        val now = Instant.now()
        for (oldSession in toClose) {
            oldSession.status = QrTableSessionStatus.CLOSED.name
            oldSession.closedAt = now
            qrTableSessionRepository.save(oldSession)
            logger.info("Closed previous QR table session {} for tableId={} tableNumber={}", oldSession.id, tableId, tableNumber)
        }

        // 2. Create new session with fresh secure token
        val newSession = QrTableSession(
            branchId = branchId.trim(),
            tableId = tableId.trim(),
            tableNumber = tableNumber.trim(),
            sessionToken = generateSecureToken(16),
            status = QrTableSessionStatus.ACTIVE.name,
            openedAt = now,
            openedBy = openedBy?.trim()?.ifBlank { null },
            expiresAt = expiresAt
        )

        val saved = qrTableSessionRepository.save(newSession)
        logger.info("Created new QR table session {} for table {} ({}) at branch {}",
            saved.id, saved.tableNumber, saved.tableId, saved.branchId)

        return saved
    }

    fun getActiveSession(branchId: String, tableNumber: String): Optional<QrTableSession> {
        val exact = qrTableSessionRepository.findActiveByBranchIdAndTableNumber(branchId, tableNumber)
        if (exact.isPresent) return exact

        val target = normalizeTableNumber(tableNumber)
        if (target.isBlank()) return Optional.empty()
        val fallback = qrTableSessionRepository.findAllByBranchIdAndStatus(branchId, QrTableSessionStatus.ACTIVE.name)
            .filter { tableNumbersMatch(it.tableNumber, tableNumber) }
            .maxByOrNull { it.openedAt }
        return Optional.ofNullable(fallback)
    }

    fun findActiveSessionByToken(token: String): Optional<QrTableSession> {
        if (token.isBlank()) return Optional.empty()
        val found = qrTableSessionRepository.findBySessionToken(token.trim()).orElse(null) ?: return Optional.empty()
        if (found.status != QrTableSessionStatus.ACTIVE.name) return Optional.empty()
        if (found.expiresAt != null && found.expiresAt!!.isBefore(Instant.now())) return Optional.empty()
        return Optional.of(found)
    }

    fun getActiveSessionByTableId(tableId: String): Optional<QrTableSession> {
        return qrTableSessionRepository.findActiveByTableId(tableId)
    }

    @Transactional
    fun closeSession(tableIdOrSessionId: String): List<QrTableSession> {
        require(tableIdOrSessionId.isNotBlank()) { "Identifier cannot be blank" }

        val closedSessions = mutableListOf<QrTableSession>()
        val now = Instant.now()

        // Case A: Parameter is directly a session ID
        val sessionOpt = qrTableSessionRepository.findById(tableIdOrSessionId)
        if (sessionOpt.isPresent) {
            val session = sessionOpt.get()
            if (session.status == QrTableSessionStatus.ACTIVE.name) {
                session.status = QrTableSessionStatus.CLOSED.name
                session.closedAt = now
                val saved = qrTableSessionRepository.save(session)
                closedSessions.add(saved)
                logger.info("Closed QR table session by sessionId: {}", session.id)
            }
        }

        // Case B: Parameter is a table ID
        val activeForTable = qrTableSessionRepository.findAllByTableIdAndStatus(tableIdOrSessionId, QrTableSessionStatus.ACTIVE.name)
        for (session in activeForTable) {
            if (closedSessions.none { it.id == session.id }) {
                session.status = QrTableSessionStatus.CLOSED.name
                session.closedAt = now
                val saved = qrTableSessionRepository.save(session)
                closedSessions.add(saved)
                logger.info("Closed QR table session {} for tableId: {}", session.id, tableIdOrSessionId)
            }
        }

        return closedSessions
    }

    @Transactional
    fun regenerateToken(sessionIdOrTableId: String): QrTableSession {
        require(sessionIdOrTableId.isNotBlank()) { "Identifier cannot be blank" }

        // Try lookup by sessionId first, then by tableId active session
        var session = qrTableSessionRepository.findById(sessionIdOrTableId).orElse(null)
        if (session == null || session.status != QrTableSessionStatus.ACTIVE.name) {
            session = qrTableSessionRepository.findActiveByTableId(sessionIdOrTableId).orElse(null)
        }

        if (session == null) {
            throw NoSuchElementException("Active QR table session not found for identifier: $sessionIdOrTableId")
        }

        if (session.status != QrTableSessionStatus.ACTIVE.name) {
            throw IllegalStateException("Cannot regenerate token for closed session: ${session.id}")
        }

        val oldToken = session.sessionToken
        session.sessionToken = generateSecureToken(16)
        val saved = qrTableSessionRepository.save(session)

        logger.info("Regenerated QR session token for session {}: oldToken={}... newToken={}",
            saved.id, oldToken.take(4), saved.sessionToken.take(4))

        return saved
    }

    fun isSessionTokenValid(branchId: String, tableNumber: String, token: String): Boolean {
        if (token.isBlank()) return false
        val byToken = findActiveSessionByToken(token)
        if (byToken.isPresent) {
            val session = byToken.get()
            return session.branchId == branchId.trim() && tableNumbersMatch(session.tableNumber, tableNumber)
        }

        val active = getActiveSession(branchId, tableNumber)
        if (active.isEmpty) return false
        val session = active.get()
        if (session.sessionToken != token) return false
        if (session.expiresAt != null && session.expiresAt!!.isBefore(Instant.now())) {
            return false
        }
        return true
    }
}
