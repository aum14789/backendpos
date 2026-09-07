package com.sunpos.backend.domain.table

import com.sunpos.backend.common.ApiResponse
import com.sunpos.backend.common.JdbcRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.util.Optional

@Repository
class ZoneRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<Zone>(jdbcTemplate, "zones", Zone::class.java) {
    fun findByBranchIdOrderBySortOrderAsc(branchId: String): List<Zone> =
        findByField("branchId", branchId).sortedBy { it.sortOrder }
}

@Repository
class TableTypeRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<TableType>(jdbcTemplate, "table_types", TableType::class.java) {
    fun findByBranchId(branchId: String): List<TableType> = findByField("branchId", branchId)
}

@Repository
class TableRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<RestaurantTable>(jdbcTemplate, "tables", RestaurantTable::class.java) {
    fun findByBranchId(branchId: String): List<RestaurantTable> = findByField("branchId", branchId)
    fun findByZoneId(zoneId: String): List<RestaurantTable> = findByField("zoneId", zoneId)
}

@Repository
class TableSessionRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<TableSession>(jdbcTemplate, "table_sessions", TableSession::class.java) {
    fun findByTableIdAndStatus(tableId: String, status: String): Optional<TableSession> {
        val list = findByFields(mapOf("tableId" to tableId, "status" to status))
        return Optional.ofNullable(list.firstOrNull())
    }
    fun findByBranchIdAndStatus(branchId: String, status: String): List<TableSession> =
        findByFields(mapOf("branchId" to branchId, "status" to status))
}

@Service
class TableService(
    private val zoneRepository: ZoneRepository,
    private val tableTypeRepository: TableTypeRepository,
    private val tableRepository: TableRepository
) {
    fun listZones(branchId: String? = null): List<Zone> {
        return if (!branchId.isNullOrBlank()) {
            zoneRepository.findByBranchIdOrderBySortOrderAsc(branchId)
        } else {
            zoneRepository.findAll().sortedBy { it.sortOrder }
        }
    }

    fun createZone(dto: ZoneCreateDto): Zone {
        val zone = Zone(
            branchId = dto.branchId,
            name = dto.name,
            zoneType = dto.zoneType,
            sortOrder = dto.sortOrder,
            isActive = true
        )
        return zoneRepository.save(zone)
    }

    @Transactional
    fun updateZone(zoneId: String, dto: ZoneUpdateDto): Zone {
        val zone = zoneRepository.findById(zoneId).orElseThrow { IllegalArgumentException("Zone not found") }
        zone.name = dto.name
        zone.zoneType = dto.zoneType
        zone.sortOrder = dto.sortOrder
        zone.isActive = dto.isActive
        return zoneRepository.save(zone)
    }

    @Transactional
    fun deleteZone(zoneId: String) {
        zoneRepository.deleteById(zoneId)
    }

    fun listTableTypes(branchId: String? = null): List<TableType> {
        return if (!branchId.isNullOrBlank()) {
            tableTypeRepository.findByBranchId(branchId)
        } else {
            tableTypeRepository.findAll()
        }
    }

    fun createTableType(tableType: TableType): TableType = tableTypeRepository.save(tableType)

    fun listTables(branchId: String? = null, zoneId: String? = null): List<RestaurantTable> {
        return when {
            !branchId.isNullOrBlank() && !zoneId.isNullOrBlank() ->
                tableRepository.findByBranchId(branchId).filter { it.zoneId == zoneId }
            !branchId.isNullOrBlank() ->
                tableRepository.findByBranchId(branchId)
            !zoneId.isNullOrBlank() ->
                tableRepository.findByZoneId(zoneId)
            else ->
                tableRepository.findAll()
        }
    }

    fun createTable(dto: TableCreateDto): RestaurantTable {
        val table = RestaurantTable(
            branchId = dto.branchId,
            zoneId = dto.zoneId,
            tableTypeId = dto.tableTypeId,
            nameNumber = dto.nameNumber,
            capacity = dto.capacity,
            isActive = dto.isActive
        )
        return tableRepository.save(table)
    }

    @Transactional
    fun updateTable(tableId: String, dto: TableUpdateDto): RestaurantTable {
        val table = tableRepository.findById(tableId).orElseThrow { IllegalArgumentException("Table not found") }
        table.zoneId = dto.zoneId
        table.tableTypeId = dto.tableTypeId
        table.nameNumber = dto.nameNumber
        table.capacity = dto.capacity
        table.isActive = dto.isActive
        return tableRepository.save(table)
    }

    @Transactional
    fun toggleTableActive(tableId: String, isActive: Boolean): RestaurantTable {
        val table = tableRepository.findById(tableId).orElseThrow { IllegalArgumentException("Table not found") }
        table.isActive = isActive
        return tableRepository.save(table)
    }

    @Transactional
    fun deleteTable(tableId: String) {
        tableRepository.deleteById(tableId)
    }
}

@Service
class TableSessionService(
    private val tableSessionRepository: TableSessionRepository,
    private val tableRepository: TableRepository
) {
    @Transactional
    fun openSession(dto: OpenSessionDto): TableSession {
        val table = tableRepository.findById(dto.tableId)
            .orElseThrow { IllegalArgumentException("Table not found") }

        if (!table.isActive) {
            throw IllegalStateException("Table is inactive and cannot be opened")
        }

        val existing = tableSessionRepository.findByTableIdAndStatus(dto.tableId, "ACTIVE")
        if (existing.isPresent) {
            throw IllegalStateException("Table is already occupied with an active session")
        }

        val session = TableSession(
            branchId = dto.branchId,
            tableId = dto.tableId,
            status = "ACTIVE",
            openedBy = dto.openedBy
        )
        val savedSession = tableSessionRepository.save(session)

        table.status = "OCCUPIED"
        tableRepository.save(table)

        return savedSession
    }

    @Transactional
    fun closeSession(sessionId: String, closedBy: String? = null): TableSession {
        val session = tableSessionRepository.findById(sessionId)
            .orElseThrow { IllegalArgumentException("Session not found") }

        if (session.status != "ACTIVE") {
            throw IllegalStateException("Session is not active")
        }

        session.status = "CLOSED"
        session.closedAt = Instant.now()
        session.closedBy = closedBy
        val savedSession = tableSessionRepository.save(session)

        val table = tableRepository.findById(session.tableId)
            .orElseThrow { IllegalArgumentException("Table not found") }
        table.status = "AVAILABLE"
        tableRepository.save(table)

        return savedSession
    }
}

@RestController
@RequestMapping("/api/v1/tables", "/api/v1/table")
class TableController(
    private val tableService: TableService,
    private val tableSessionService: TableSessionService
) {
    @GetMapping("/zones")
    fun getZones(@RequestParam(required = false) branchId: String?): ApiResponse<List<Zone>> {
        return ApiResponse.success(tableService.listZones(branchId))
    }

    @PostMapping("/zones")
    @PreAuthorize("hasAuthority('TABLE_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun createZone(@RequestBody dto: ZoneCreateDto): ApiResponse<Zone> {
        return ApiResponse.success(tableService.createZone(dto), "Zone created successfully")
    }

    @PutMapping("/zones/{id}")
    @PreAuthorize("hasAuthority('TABLE_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun updateZone(@PathVariable id: String, @RequestBody dto: ZoneUpdateDto): ApiResponse<Zone> {
        return ApiResponse.success(tableService.updateZone(id, dto), "Zone updated successfully")
    }

    @DeleteMapping("/zones/{id}")
    @PreAuthorize("hasAuthority('TABLE_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun deleteZone(@PathVariable id: String): ApiResponse<Boolean> {
        tableService.deleteZone(id)
        return ApiResponse.success(true, "Zone deleted successfully")
    }

    @GetMapping("/table-types")
    fun getTableTypes(@RequestParam(required = false) branchId: String?): ApiResponse<List<TableType>> {
        return ApiResponse.success(tableService.listTableTypes(branchId))
    }

    @PostMapping("/table-types")
    @PreAuthorize("hasAuthority('TABLE_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun createTableType(@RequestBody tableType: TableType): ApiResponse<TableType> {
        return ApiResponse.success(tableService.createTableType(tableType), "Table type created successfully")
    }

    @GetMapping("", "/list")
    fun getTables(
        @RequestParam(required = false) branchId: String?,
        @RequestParam(required = false) zoneId: String?
    ): ApiResponse<List<RestaurantTable>> {
        return ApiResponse.success(tableService.listTables(branchId, zoneId))
    }

    @PostMapping("", "/create")
    @PreAuthorize("hasAuthority('TABLE_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun createTable(@RequestBody dto: TableCreateDto): ApiResponse<RestaurantTable> {
        return ApiResponse.success(tableService.createTable(dto), "Table created successfully")
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('TABLE_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun updateTable(@PathVariable id: String, @RequestBody dto: TableUpdateDto): ApiResponse<RestaurantTable> {
        return ApiResponse.success(tableService.updateTable(id, dto), "Table updated successfully")
    }

    @PatchMapping("/{id}/toggle-active")
    @PreAuthorize("hasAuthority('TABLE_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun toggleTableActive(@PathVariable id: String, @RequestParam isActive: Boolean): ApiResponse<RestaurantTable> {
        return ApiResponse.success(tableService.toggleTableActive(id, isActive), "Table active status updated")
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('TABLE_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun deleteTable(@PathVariable id: String): ApiResponse<Boolean> {
        tableService.deleteTable(id)
        return ApiResponse.success(true, "Table deleted successfully")
    }

    @PostMapping("/sessions/open")
    fun openSession(@RequestBody dto: OpenSessionDto): ApiResponse<TableSession> {
        return ApiResponse.success(tableSessionService.openSession(dto), "Table session opened successfully")
    }

    @PostMapping("/sessions/{id}/close")
    fun closeSession(@PathVariable id: String, @RequestParam(required = false) closedBy: String?): ApiResponse<TableSession> {
        return ApiResponse.success(tableSessionService.closeSession(id, closedBy), "Table session closed successfully")
    }
}
