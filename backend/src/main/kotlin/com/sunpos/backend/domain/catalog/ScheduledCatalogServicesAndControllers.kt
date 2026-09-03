package com.sunpos.backend.domain.catalog

import com.sunpos.backend.common.ApiResponse
import com.sunpos.backend.common.JdbcRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.time.Instant

@Repository
class ScheduledCatalogRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<ScheduledCatalog>(jdbcTemplate, "scheduled_catalogs", ScheduledCatalog::class.java) {
    fun findByBranchIdAndStatus(branchId: String, status: ScheduledCatalogStatus): List<ScheduledCatalog> =
        findByFields(mapOf("branchId" to branchId, "status" to status.name))
    fun findByMenuItemIdAndStatus(menuItemId: String, status: ScheduledCatalogStatus): List<ScheduledCatalog> =
        findByFields(mapOf("menuItemId" to menuItemId, "status" to status.name))
}

@Service
class ScheduledCatalogService(
    private val scheduledCatalogRepository: ScheduledCatalogRepository,
    private val menuItemRepository: MenuItemRepository
) {
    fun listScheduledCatalogs(branchId: String): List<ScheduledCatalog> {
        // Automatically sync and expire schedules on query
        val list = scheduledCatalogRepository.findAll().filter { it.branchId == branchId }
        val now = Instant.now()
        for (sc in list) {
            if (sc.status == ScheduledCatalogStatus.SCHEDULED && sc.startAt.isBefore(now) && sc.endAt.isAfter(now)) {
                sc.status = ScheduledCatalogStatus.ACTIVE
                scheduledCatalogRepository.save(sc)
            } else if ((sc.status == ScheduledCatalogStatus.SCHEDULED || sc.status == ScheduledCatalogStatus.ACTIVE) && sc.endAt.isBefore(now)) {
                sc.status = ScheduledCatalogStatus.EXPIRED
                scheduledCatalogRepository.save(sc)
            }
        }
        return scheduledCatalogRepository.findAll().filter { it.branchId == branchId }
    }

    @Transactional
    fun createScheduledCatalog(catalog: ScheduledCatalog): ScheduledCatalog {
        val item = menuItemRepository.findById(catalog.menuItemId)
            .orElseThrow { IllegalArgumentException("Menu Item not found") }
        
        // Validation timezone bounds
        if (catalog.startAt.isAfter(catalog.endAt)) {
            throw IllegalArgumentException("Start time must be before end time")
        }

        return scheduledCatalogRepository.save(catalog)
    }

    @Transactional
    fun cancelScheduledCatalog(id: String): ScheduledCatalog {
        val sc = scheduledCatalogRepository.findById(id)
            .orElseThrow { IllegalArgumentException("Scheduled catalog entry not found") }
        sc.status = ScheduledCatalogStatus.CANCELLED
        return scheduledCatalogRepository.save(sc)
    }
}

@RestController
@RequestMapping("/api/v1/scheduled-catalogs")
class ScheduledCatalogController(
    private val scheduledCatalogService: ScheduledCatalogService
) {
    @GetMapping
    fun getSchedules(@RequestParam branchId: String): ApiResponse<List<ScheduledCatalog>> {
        return ApiResponse.success(scheduledCatalogService.listScheduledCatalogs(branchId))
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MENU_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun createSchedule(@RequestBody catalog: ScheduledCatalog): ApiResponse<ScheduledCatalog> {
        return ApiResponse.success(scheduledCatalogService.createScheduledCatalog(catalog), "Schedule created successfully")
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('MENU_MANAGE') or hasAuthority('ROLE_SUPER_ADMIN')")
    fun cancelSchedule(@PathVariable id: String): ApiResponse<ScheduledCatalog> {
        return ApiResponse.success(scheduledCatalogService.cancelScheduledCatalog(id), "Schedule cancelled successfully")
    }
}
