package com.sunpos.backend.domain.table

import com.sunpos.backend.common.JdbcRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.Optional

@Repository
class QrTableSessionRepository(
    jdbcTemplate: JdbcTemplate
) : JdbcRepository<QrTableSession>(jdbcTemplate, "qr_table_sessions", QrTableSession::class.java) {

    fun findActiveByTableId(tableId: String): Optional<QrTableSession> {
        val list = findByFields(mapOf("tableId" to tableId, "status" to QrTableSessionStatus.ACTIVE.name))
        return Optional.ofNullable(list.maxByOrNull { it.openedAt })
    }

    fun findActiveByBranchIdAndTableNumber(branchId: String, tableNumber: String): Optional<QrTableSession> {
        val list = findByFields(mapOf(
            "branchId" to branchId,
            "tableNumber" to tableNumber,
            "status" to QrTableSessionStatus.ACTIVE.name
        ))
        return Optional.ofNullable(list.maxByOrNull { it.openedAt })
    }

    fun findBySessionToken(sessionToken: String): Optional<QrTableSession> {
        val list = findByField("sessionToken", sessionToken)
        return Optional.ofNullable(list.firstOrNull())
    }

    fun findAllByTableIdAndStatus(tableId: String, status: String): List<QrTableSession> {
        return findByFields(mapOf("tableId" to tableId, "status" to status))
    }

    fun findAllByBranchIdAndTableNumberAndStatus(branchId: String, tableNumber: String, status: String): List<QrTableSession> {
        return findByFields(mapOf("branchId" to branchId, "tableNumber" to tableNumber, "status" to status))
    }

    fun findAllByBranchIdAndStatus(branchId: String, status: String): List<QrTableSession> {
        return findByFields(mapOf("branchId" to branchId, "status" to status))
    }
}
