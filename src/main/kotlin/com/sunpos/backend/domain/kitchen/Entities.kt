package com.sunpos.backend.domain.kitchen

import com.sunpos.backend.common.JdbcRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

/**
 * สเตชันครัว / จุดเตรียมอาหาร (ADR 0018)
 * ใช้กำหนดจุดรับผิดชอบการปรุงอาหาร และสายงานพิมพ์ใบครัว (Kitchen Ticket)
 */
data class KitchenStation(
    val id: String = UUID.randomUUID().toString(),
    var brandId: String? = null,
    var branchId: String? = null,
    var code: String = "",
    var name: String = "",
    var description: String? = null,
    var sortOrder: Int = 0,
    var isActive: Boolean = true,
    val createdAt: Instant = Instant.now()
)

@Repository
class KitchenStationRepository(jdbcTemplate: JdbcTemplate) :
    JdbcRepository<KitchenStation>(jdbcTemplate, "kitchen_stations", KitchenStation::class.java) {

    fun findAllOrdered(): List<KitchenStation> =
        findAll().sortedBy { it.sortOrder }

    fun findByCode(code: String): KitchenStation? =
        findOneByField("code", code).orElse(null)
}
