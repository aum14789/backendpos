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
    var isActive: Boolean = true
)

/** DTO สำหรับ Backoffice + POS sync (รวมรายการหมวดอาหารที่เครื่องรับพิมพ์) */
data class PrinterDto(
    val id: String,
    val branchId: String,
    val name: String,
    val ipAddress: String,
    val port: Int,
    val isDocumentPrinter: Boolean,
    val isActive: Boolean,
    val menuCategoryIds: List<String> = emptyList()
)

fun Printer.toDto(menuCategoryIds: List<String> = emptyList()) = PrinterDto(
    id = id, branchId = branchId, name = name, ipAddress = ipAddress,
    port = port, isDocumentPrinter = isDocumentPrinter, isActive = isActive,
    menuCategoryIds = menuCategoryIds
)

data class UpsertPrinterRequest(
    val id: String? = null,
    val name: String,
    val ipAddress: String,
    val port: Int = 9100,
    val isDocumentPrinter: Boolean = false,
    val isActive: Boolean = true,
    val menuCategoryIds: List<String> = emptyList()
)

@Repository
class PrinterRepository(jdbcTemplate: JdbcTemplate) : JdbcRepository<Printer>(jdbcTemplate, "printers", Printer::class.java) {
    fun findByBranchId(branchId: String): List<Printer> = findByField("branchId", branchId)

    fun findByBranchIdAndIsActiveTrue(branchId: String): List<Printer> =
        findByFields(mapOf("branchId" to branchId, "isActive" to true))
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
