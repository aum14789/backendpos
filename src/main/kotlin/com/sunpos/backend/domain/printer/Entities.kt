package com.sunpos.backend.domain.printer

import com.sunpos.backend.common.JdbcRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

/**
 * เครื่องปริ้นความร้อน 80มม. ประจำสาขา (ADR 0005)
 * config จาก Backoffice → sync ไป POS ผ่าน /api/v1/sync/pull
 * 1 เครื่องพิมพ์ได้หลายหมวดอาหาร (printer_menu_categories)
 * 1 สาขามีได้ 1 เครื่อง isDocumentPrinter = true (ใบเสร็จ/ใบแจ้งรายการ/ใบกำกับภาษี)
 *
 * หมายเหตุ: entity นี้จับคอลัมน์กับตาราง printers แบบ reflection 1:1
 * ห้ามเพิ่ม field ที่ไม่มีคอลัมน์จริง (การแสดงหมวดอาหารใช้ PrinterDto แทน)
 */
data class Printer(
    val id: String = "",
    val branchId: String = "",
    val name: String = "",
    val ipAddress: String = "",
    val port: Int = 9100,
    var isDocumentPrinter: Boolean = false,
    var isActive: Boolean = true,
    var kitchenStationId: String? = null
)

/** DTO สำหรับ Backoffice + POS sync (รวม kitchenStationIds และ menuCategoryIds) */
data class PrinterDto(
    val id: String,
    val branchId: String,
    val name: String,
    val ipAddress: String,
    val port: Int,
    val isDocumentPrinter: Boolean,
    val isActive: Boolean,
    val menuCategoryIds: List<String> = emptyList(),
    /** @deprecated ใช้ kitchenStationIds แทน (backward compat กับ POS รุ่นเก่า) */
    val kitchenStationId: String? = null,
    val kitchenStationIds: List<String> = emptyList()
)

fun Printer.toDto(
    menuCategoryIds: List<String> = emptyList(),
    kitchenStationIds: List<String> = emptyList()
) = PrinterDto(
    id = id, branchId = branchId, name = name, ipAddress = ipAddress,
    port = port, isDocumentPrinter = isDocumentPrinter, isActive = isActive,
    menuCategoryIds = menuCategoryIds,
    kitchenStationId = kitchenStationIds.firstOrNull() ?: kitchenStationId,
    kitchenStationIds = kitchenStationIds
)

data class UpsertPrinterRequest(
    val id: String? = null,
    val name: String,
    val ipAddress: String,
    val port: Int = 9100,
    val isDocumentPrinter: Boolean = false,
    val isActive: Boolean = true,
    val menuCategoryIds: List<String> = emptyList(),
    /** @deprecated ใช้ kitchenStationIds แทน */
    val kitchenStationId: String? = null,
    val kitchenStationIds: List<String> = emptyList()
) {
    /** รวม kitchenStationId เดิม + kitchenStationIds ให้ไม่ซ้ำ */
    fun resolvedStationIds(): List<String> {
        val merged = kitchenStationIds.toMutableList()
        if (!kitchenStationId.isNullOrBlank() && !merged.contains(kitchenStationId)) {
            merged.add(0, kitchenStationId)
        }
        return merged.filter { it.isNotBlank() }.distinct()
    }
}

@Repository
class PrinterRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<Printer>(jdbcTemplate, "printers", Printer::class.java) {
    fun findByBranchId(branchId: String): List<Printer> = findByField("branchId", branchId)

    fun findByBranchIdAndIsActiveTrue(branchId: String): List<Printer> =
        findByFields(mapOf("branchId" to branchId, "isActive" to true))
}

@Repository
class PrinterKitchenStationRepository(private val jdbcTemplate: JdbcTemplate) {

    fun findStationIdsByPrinterId(printerId: String): List<String> =
        jdbcTemplate.queryForList(
            "SELECT station_id FROM printer_kitchen_stations WHERE printer_id = ?",
            String::class.java,
            printerId
        )

    fun deleteByPrinterId(printerId: String) {
        jdbcTemplate.update("DELETE FROM printer_kitchen_stations WHERE printer_id = ?", printerId)
    }

    fun saveAll(printerId: String, stationIds: List<String>) {
        deleteByPrinterId(printerId)
        for (sid in stationIds) {
            jdbcTemplate.update(
                "INSERT INTO printer_kitchen_stations (printer_id, station_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                printerId, sid
            )
        }
    }
}

@Repository
class PrinterMenuCategoryRepository(private val jdbcTemplate: JdbcTemplate) {

    fun findCategoryIdsByPrinterId(printerId: String): List<String> =
        jdbcTemplate.queryForList(
            "SELECT category_id FROM printer_menu_categories WHERE printer_id = ?",
            String::class.java,
            printerId
        )

    fun deleteByPrinterId(printerId: String) {
        jdbcTemplate.update("DELETE FROM printer_menu_categories WHERE printer_id = ?", printerId)
    }

    fun saveAll(printerId: String, categoryIds: List<String>) {
        deleteByPrinterId(printerId)
        for (cid in categoryIds) {
            jdbcTemplate.update(
                "INSERT INTO printer_menu_categories (printer_id, category_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                printerId, cid
            )
        }
    }
}
