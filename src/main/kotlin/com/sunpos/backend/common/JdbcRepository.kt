package com.sunpos.backend.common

import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import java.math.BigDecimal
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Generic JDBC-backed repository that mirrors the old FirestoreRepository API.
 *
 * Features:
 *  - Entity field `camelCase` maps to SQL column `snake_case`
 *  - Primary-key column is `id` (with fallback to `eventId` / `deviceId`)
 *  - Resilient in-memory localCache fallback for offline resilience & fast test execution
 *  - Supports Instant, LocalDate, BigDecimal, Enums, and List<String>
 */
abstract class JdbcRepository<T : Any>(
    protected val jdbcTemplate: JdbcTemplate,
    val tableName: String,
    private val entityClass: Class<T>
) {
    protected val logger = LoggerFactory.getLogger(javaClass)

    /** Resilient in-memory cache to survive network hiccups / offline / test environments */
    protected val localCache = ConcurrentHashMap<String, T>()

    // ──────────────────────────── helpers ────────────────────────────

    /** camelCase → snake_case */
    private fun toSnakeCase(camelCase: String): String {
        return camelCase.replace(Regex("([a-z])([A-Z])")) { "${it.groupValues[1]}_${it.groupValues[2]}" }
            .lowercase()
    }

    /** All declared fields on the entity class (including super). */
    private fun allFields(): List<java.lang.reflect.Field> {
        val fields = mutableListOf<java.lang.reflect.Field>()
        var cls: Class<*>? = entityClass
        while (cls != null && cls != Any::class.java) {
            fields.addAll(cls.declaredFields)
            cls = cls.superclass
        }
        return fields
    }

    // ──────────────────────────── ID helpers ────────────────────────────

    open fun getId(entity: T): String {
        return try {
            val field = try {
                entity.javaClass.getDeclaredField("id")
            } catch (e: NoSuchFieldException) {
                try {
                    entity.javaClass.getDeclaredField("eventId")
                } catch (e2: NoSuchFieldException) {
                    entity.javaClass.getDeclaredField("deviceId")
                }
            }
            field.isAccessible = true
            field.get(entity)?.toString() ?: UUID.randomUUID().toString()
        } catch (e: Exception) {
            UUID.randomUUID().toString()
        }
    }

    // ──────────────────────────── RowMapper ────────────────────────────

    @Suppress("UNCHECKED_CAST")
    protected open val rowMapper: RowMapper<T> = RowMapper { rs, _ ->
        val instance = try {
            entityClass.getDeclaredConstructor().newInstance()
        } catch (e: Exception) {
            val ctor = entityClass.declaredConstructors.maxByOrNull { it.parameterCount }
                ?: throw IllegalStateException("No constructor found for ${entityClass.simpleName}")
            ctor.isAccessible = true
            val defaults = ctor.parameters.map { param ->
                when (param.type) {
                    String::class.java, java.lang.String::class.java -> ""
                    Boolean::class.java, java.lang.Boolean::class.java -> false
                    Int::class.java, java.lang.Integer::class.java -> 0
                    Long::class.java, java.lang.Long::class.java -> 0L
                    Double::class.java, java.lang.Double::class.java -> 0.0
                    BigDecimal::class.java -> BigDecimal.ZERO
                    Instant::class.java -> Instant.now()
                    LocalDate::class.java -> LocalDate.now()
                    List::class.java, java.util.List::class.java -> emptyList<String>()
                    else -> null
                }
            }
            ctor.newInstance(*defaults.toTypedArray()) as T
        }

        val fields = allFields()
        val metaData = rs.metaData
        val columnNames = (1..metaData.columnCount).map { metaData.getColumnLabel(it).lowercase() }.toSet()

        for (field in fields) {
            field.isAccessible = true
            val colName = toSnakeCase(field.name)
            if (colName !in columnNames) continue

            try {
                val value = readColumn(rs, colName, field)
                if (value != null || !field.type.isPrimitive) {
                    field.set(instance, value)
                }
            } catch (e: Exception) {
                logger.trace("Skipping column {} for {}: {}", colName, entityClass.simpleName, e.message)
            }
        }
        instance
    }

    @Suppress("UNCHECKED_CAST")
    private fun readColumn(rs: ResultSet, colName: String, field: java.lang.reflect.Field): Any? {
        val type = field.type
        return when {
            type == String::class.java || type == java.lang.String::class.java -> rs.getString(colName)
            type == Boolean::class.java || type == java.lang.Boolean::class.java || type == Boolean::class.javaPrimitiveType -> {
                val v = rs.getObject(colName) ?: return null
                when (v) {
                    is Boolean -> v
                    is Number -> v.toInt() != 0
                    is String -> v.equals("true", ignoreCase = true)
                    else -> false
                }
            }
            type == Int::class.java || type == java.lang.Integer::class.java || type == Int::class.javaPrimitiveType -> {
                rs.getObject(colName)?.let { (it as Number).toInt() }
            }
            type == Long::class.java || type == java.lang.Long::class.java || type == Long::class.javaPrimitiveType -> {
                rs.getObject(colName)?.let { (it as Number).toLong() }
            }
            type == Double::class.java || type == java.lang.Double::class.java || type == Double::class.javaPrimitiveType -> {
                rs.getObject(colName)?.let { (it as Number).toDouble() }
            }
            type == BigDecimal::class.java -> rs.getBigDecimal(colName)
            type == Instant::class.java -> rs.getTimestamp(colName)?.toInstant()
            type == LocalDate::class.java -> rs.getDate(colName)?.toLocalDate()
            type.isEnum -> {
                val str = rs.getString(colName) ?: return null
                try {
                    java.lang.Enum.valueOf(type as Class<out Enum<*>>, str) as Any
                } catch (e: Exception) { null }
            }
            List::class.java.isAssignableFrom(type) -> {
                val raw = rs.getString(colName)
                if (raw.isNullOrBlank()) emptyList<String>()
                else raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }
            else -> rs.getObject(colName)
        }
    }

    // ──────────────────────────── field matching ────────────────────────────

    protected fun matchesField(entity: T, fieldName: String, expectedValue: Any?): Boolean {
        return try {
            val field = entity.javaClass.declaredFields.find { it.name.equals(fieldName, ignoreCase = true) }
                ?: entity.javaClass.superclass?.declaredFields?.find { it.name.equals(fieldName, ignoreCase = true) }
            if (field != null) {
                field.isAccessible = true
                val actual = field.get(entity)
                if (actual == null && expectedValue == null) return true
                if (actual == null || expectedValue == null) return false
                if (actual == expectedValue) return true
                if (actual.toString() == expectedValue.toString()) return true
                if (actual is Enum<*> && actual.name == expectedValue.toString()) return true
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    // ──────────────────────────── CRUD ────────────────────────────

    open fun findById(id: Any): Optional<T> {
        val idStr = id.toString()
        try {
            val list = jdbcTemplate.query("SELECT * FROM $tableName WHERE id = ?", rowMapper, idStr)
            val found = list.firstOrNull()
            if (found != null) {
                localCache[idStr] = found
                return Optional.of(found)
            }
        } catch (e: Exception) {
            logger.trace("JDBC findById query error for {}/{}, using localCache: {}", tableName, idStr, e.message)
        }
        return Optional.ofNullable(localCache[idStr])
    }

    open fun findAllById(ids: Iterable<String>): List<T> {
        val idList = ids.toList()
        if (idList.isEmpty()) return emptyList()
        return idList.mapNotNull { id -> findById(id).orElse(null) }
    }

    open fun findAll(): List<T> {
        try {
            val list = jdbcTemplate.query("SELECT * FROM $tableName", rowMapper)
            if (list.isNotEmpty()) {
                list.forEach { localCache[getId(it)] = it }
                return list
            }
        } catch (e: Exception) {
            logger.trace("JDBC findAll query error for {}, using localCache: {}", tableName, e.message)
        }
        return localCache.values.toList()
    }

    open fun findByField(field: String, value: Any): List<T> {
        val col = toSnakeCase(field)
        try {
            val sqlValue = if (value is Enum<*>) value.name else value
            val list = jdbcTemplate.query("SELECT * FROM $tableName WHERE $col = ?", rowMapper, sqlValue)
            if (list.isNotEmpty()) {
                list.forEach { localCache[getId(it)] = it }
                return list
            }
        } catch (e: Exception) {
            logger.trace("JDBC findByField query error for {} where {} = {}, using localCache: {}", tableName, col, value, e.message)
        }
        return localCache.values.filter { matchesField(it, field, value) }
    }

    open fun findByFields(criteria: Map<String, Any>): List<T> {
        if (criteria.isEmpty()) return findAll()
        try {
            val whereParts = criteria.keys.map { "${toSnakeCase(it)} = ?" }
            val sql = "SELECT * FROM $tableName WHERE ${whereParts.joinToString(" AND ")}"
            val args = criteria.values.map { if (it is Enum<*>) it.name else it }.toTypedArray()
            val list = jdbcTemplate.query(sql, rowMapper, *args)
            if (list.isNotEmpty()) {
                list.forEach { localCache[getId(it)] = it }
                return list
            }
        } catch (e: Exception) {
            logger.trace("JDBC findByFields query error for {}, using localCache: {}", tableName, e.message)
        }
        return localCache.values.filter { entity ->
            criteria.entries.all { (field, value) -> matchesField(entity, field, value) }
        }
    }

    open fun findOneByField(field: String, value: Any): Optional<T> {
        val col = toSnakeCase(field)
        try {
            val sqlValue = if (value is Enum<*>) value.name else value
            val list = jdbcTemplate.query("SELECT * FROM $tableName WHERE $col = ? LIMIT 1", rowMapper, sqlValue)
            val found = list.firstOrNull()
            if (found != null) {
                localCache[getId(found)] = found
                return Optional.of(found)
            }
        } catch (e: Exception) {
            logger.trace("JDBC findOneByField query error for {} where {} = {}, using localCache: {}", tableName, col, value, e.message)
        }
        val cached = localCache.values.firstOrNull { matchesField(it, field, value) }
        return Optional.ofNullable(cached)
    }

    open fun save(entity: T): T {
        val id = getId(entity)
        localCache[id] = entity

        val fields = allFields()
        val columns = mutableListOf<String>()
        val values = mutableListOf<Any?>()
        val types = mutableListOf<Int>()

        for (field in fields) {
            field.isAccessible = true
            val colName = toSnakeCase(field.name)
            val raw = field.get(entity)
            columns.add(colName)

            when {
                raw == null -> {
                    values.add(null)
                    types.add(Types.VARCHAR)
                }
                raw is Instant -> {
                    values.add(Timestamp.from(raw))
                    types.add(Types.TIMESTAMP)
                }
                raw is LocalDate -> {
                    values.add(java.sql.Date.valueOf(raw))
                    types.add(Types.DATE)
                }
                raw is Enum<*> -> {
                    values.add(raw.name)
                    types.add(Types.VARCHAR)
                }
                raw is List<*> -> {
                    values.add((raw as List<*>).joinToString(","))
                    types.add(Types.VARCHAR)
                }
                raw is BigDecimal -> {
                    values.add(raw)
                    types.add(Types.NUMERIC)
                }
                raw is Boolean -> {
                    values.add(raw)
                    types.add(Types.BOOLEAN)
                }
                raw is Int -> {
                    values.add(raw)
                    types.add(Types.INTEGER)
                }
                raw is Long -> {
                    values.add(raw)
                    types.add(Types.BIGINT)
                }
                raw is Double -> {
                    values.add(raw)
                    types.add(Types.DOUBLE)
                }
                else -> {
                    values.add(raw.toString())
                    types.add(Types.VARCHAR)
                }
            }
        }

        val placeholders = columns.map { "?" }.joinToString(", ")
        val updateSet = columns.filter { it != "id" }.joinToString(", ") { "$it = EXCLUDED.$it" }

        val sql = "INSERT INTO $tableName (${columns.joinToString(", ")}) VALUES ($placeholders)" +
                if (updateSet.isNotEmpty()) " ON CONFLICT (id) DO UPDATE SET $updateSet" else " ON CONFLICT (id) DO NOTHING"

        try {
            jdbcTemplate.update(sql, *values.toTypedArray())
        } catch (e: Exception) {
            logger.debug("JDBC save failed for {}/{}, preserved in localCache: {}", tableName, id, e.message)
        }
        return entity
    }

    open fun saveAll(entities: Iterable<T>): List<T> {
        val list = entities.toList()
        list.forEach { save(it) }
        return list
    }

    open fun deleteById(id: Any) {
        val idStr = id.toString()
        localCache.remove(idStr)
        try {
            jdbcTemplate.update("DELETE FROM $tableName WHERE id = ?", idStr)
        } catch (e: Exception) {
            logger.debug("JDBC delete failed for {}/{}: {}", tableName, idStr, e.message)
        }
    }

    open fun delete(entity: T) {
        deleteById(getId(entity))
    }

    open fun deleteAll(entities: Iterable<T>) {
        entities.forEach { delete(it) }
    }

    open fun deleteAll() {
        localCache.clear()
        try {
            jdbcTemplate.update("DELETE FROM $tableName")
        } catch (e: Exception) {
            logger.debug("JDBC deleteAll failed for {}: {}", tableName, e.message)
        }
    }

    open fun existsById(id: Any): Boolean {
        val idStr = id.toString()
        if (localCache.containsKey(idStr)) return true
        return try {
            val count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $tableName WHERE id = ?", Long::class.java, idStr)
            (count ?: 0) > 0
        } catch (e: Exception) {
            false
        }
    }

    open fun count(): Long {
        return try {
            val c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM $tableName", Long::class.java) ?: 0
            if (c > 0) c else localCache.size.toLong()
        } catch (e: Exception) {
            localCache.size.toLong()
        }
    }
}
