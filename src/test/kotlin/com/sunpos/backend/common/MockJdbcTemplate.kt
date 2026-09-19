package com.sunpos.backend.common

import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.math.BigDecimal
import java.sql.ResultSet
import java.sql.Timestamp

/** A captured `jdbcTemplate.update(sql, args...)` call. */
data class RecordedUpdate(val sql: String, val args: List<Any?>)

/**
 * Mocked [JdbcTemplate] that behaves like a no-op in-memory database:
 * repository reads fall through to their `localCache`, while every `update(...)`
 * call is captured in [sink] so tests can assert on the SQL actually issued.
 */
fun recordingJdbcTemplate(sink: MutableList<RecordedUpdate>): JdbcTemplate =
    mock(JdbcTemplate::class.java, jdbcAnswer { invocation ->
        if (invocation.method.name == "update") {
            val sql = invocation.arguments[0] as String
            sink.add(RecordedUpdate(sql, normalizeVarargs(invocation.arguments.drop(1))))
            0
        } else {
            null // fall through to the JdbcTemplate-shaped default
        }
    })

/**
 * Mocked [JdbcTemplate] whose `query(...)` calls answer with [rows] projected
 * through the *real* RowMapper, so SQL column mapping and the caller's
 * aggregation math are both exercised without a live database.
 * Every issued SQL statement is appended to [queries].
 */
fun rowFedJdbcTemplate(
    rows: List<Map<String, Any?>>,
    queries: MutableList<String> = mutableListOf(),
    updates: MutableList<RecordedUpdate> = mutableListOf()
): JdbcTemplate =
    mock(JdbcTemplate::class.java, jdbcAnswer { invocation ->
        when (invocation.method.name) {
            "query" -> {
                queries.add(invocation.arguments[0] as String)
                @Suppress("UNCHECKED_CAST")
                val mapper = invocation.arguments[1] as RowMapper<Any>
                rows.mapIndexed { index, row -> mapper.mapRow(resultSetOf(row), index) }
            }
            "update" -> {
                updates.add(RecordedUpdate(invocation.arguments[0] as String, emptyList()))
                0
            }
            else -> null // fall through to the JdbcTemplate-shaped default
        }
    })

/**
 * Varargs reach Mockito either expanded or as a trailing array depending on how
 * the call site was compiled, so flatten whenever the last argument is an array.
 */
private fun normalizeVarargs(raw: List<Any?>): List<Any?> {
    if (raw.size == 1) {
        val only = raw[0]
        if (only != null && only::class.java.isArray) return (only as Array<*>).toList()
    }
    return raw
}

/**
 * Builds the default answer for a mocked [JdbcTemplate]. [interpret] returns the
 * caller's own result for a call it cares about, or `null` to accept the
 * JdbcTemplate-shaped default from [defaultReturnValue].
 */
private fun jdbcAnswer(interpret: (InvocationOnMock) -> Any?): Answer<Any> =
    Answer { invocation -> interpret(invocation) ?: defaultReturnValue(invocation) }

/** Empty collections for reads, and the primitive zero/false for anything else. */
private fun defaultReturnValue(invocation: InvocationOnMock): Any? = when {
    invocation.method.name == "query" || invocation.method.name == "queryForList" -> emptyList<Any>()
    invocation.method.name == "batchUpdate" -> IntArray(0)
    else -> when (invocation.method.returnType) {
        java.lang.Boolean.TYPE -> false
        java.lang.Integer.TYPE -> 0
        java.lang.Long.TYPE -> 0L
        else -> null
    }
}

/** Builds a mocked [ResultSet] exposing [row] under the given column labels. */
fun resultSetOf(row: Map<String, Any?>): ResultSet {
    val rs = mock(ResultSet::class.java)
    for ((column, value) in row) {
        when (value) {
            null -> {
                `when`(rs.getString(column)).thenReturn(null)
                `when`(rs.getBigDecimal(column)).thenReturn(null)
                `when`(rs.getTimestamp(column)).thenReturn(null)
                `when`(rs.getObject(column)).thenReturn(null)
            }
            is String -> {
                `when`(rs.getString(column)).thenReturn(value)
                `when`(rs.getObject(column)).thenReturn(value)
            }
            is BigDecimal -> {
                `when`(rs.getBigDecimal(column)).thenReturn(value)
                `when`(rs.getObject(column)).thenReturn(value)
            }
            is Boolean -> {
                `when`(rs.getBoolean(column)).thenReturn(value)
                `when`(rs.getObject(column)).thenReturn(value)
            }
            is Int -> {
                `when`(rs.getInt(column)).thenReturn(value)
                `when`(rs.getObject(column)).thenReturn(value)
            }
            is Long -> {
                `when`(rs.getLong(column)).thenReturn(value)
                `when`(rs.getObject(column)).thenReturn(value)
            }
            is Timestamp -> {
                `when`(rs.getTimestamp(column)).thenReturn(value)
                `when`(rs.getObject(column)).thenReturn(value)
            }
            else -> `when`(rs.getObject(column)).thenReturn(value)
        }
    }
    return rs
}
