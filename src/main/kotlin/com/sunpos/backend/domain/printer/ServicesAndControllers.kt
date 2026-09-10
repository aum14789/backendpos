package com.sunpos.backend.domain.printer

import com.sunpos.backend.common.ApiResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.*
import java.util.UUID

@Service
class PrinterService(
    private val printerRepository: PrinterRepository,
    private val printerMenuCategoryRepository: PrinterMenuCategoryRepository
) {
    fun listPrinters(branchId: String): List<PrinterDto> {
        return printerRepository.findByBranchId(branchId).map { p ->
            p.toDto(printerMenuCategoryRepository.findCategoryIdsByPrinterId(p.id))
        }
    }

    @Transactional
    fun upsertPrinter(branchId: String, req: UpsertPrinterRequest): PrinterDto {
        require(req.name.isNotBlank()) { " printer name is required" }
        require(req.ipAddress.isNotBlank()) { "Printer IP address is required" }
        require(req.port in 1..65535) { "Port must be 1-65535" }

        // กฎ: 1 สาขามี document printer ได้ 1 เครื่อง — ถ้าเครื่องใหม่เป็น doc printer
        // ให้ปลดเครื่องเดิมอัตโนมัติ
        if (req.isDocumentPrinter) {
            printerRepository.findByBranchId(branchId)
                .filter { it.isDocumentPrinter && it.id != req.id }
                .forEach { old ->
                    old.isDocumentPrinter = false
                    printerRepository.save(old)
                }
        }

        val printer = Printer(
            id = req.id ?: UUID.randomUUID().toString(),
            branchId = branchId,
            name = req.name.trim(),
            ipAddress = req.ipAddress.trim(),
            port = req.port,
            isDocumentPrinter = req.isDocumentPrinter,
            isActive = req.isActive
        )
        printerRepository.save(printer)
        printerMenuCategoryRepository.saveAll(printer.id, req.menuCategoryIds)

        return printer.toDto(req.menuCategoryIds)
    }

    @Transactional
    fun deletePrinter(branchId: String, printerId: String) {
        val printer = printerRepository.findById(printerId).orElse(null)
        require(printer != null && printer.branchId == branchId) { "Printer not found in this branch" }
        printerMenuCategoryRepository.deleteByPrinterId(printerId)
        printerRepository.deleteById(printerId)
    }
}

@RestController
@RequestMapping("/api/v1/branches/{branchId}/printers")
class PrinterController(
    private val printerService: PrinterService
) {
    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ADMIN') or hasAuthority('ROLE_BRANCH_MANAGER') or hasAuthority('POS_CONFIG_MANAGE') or hasAuthority('MENU_MANAGE')")
    fun list(@PathVariable branchId: String): ApiResponse<List<PrinterDto>> {
        return ApiResponse.success(printerService.listPrinters(branchId))
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ADMIN') or hasAuthority('POS_CONFIG_MANAGE') or hasAuthority('MENU_MANAGE')")
    fun create(@PathVariable branchId: String, @RequestBody req: UpsertPrinterRequest): ApiResponse<PrinterDto> {
        return ApiResponse.success(printerService.upsertPrinter(branchId, req), "Printer created")
    }

    @PutMapping("/{printerId}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ADMIN') or hasAuthority('POS_CONFIG_MANAGE') or hasAuthority('MENU_MANAGE')")
    fun update(
        @PathVariable branchId: String,
        @PathVariable printerId: String,
        @RequestBody req: UpsertPrinterRequest
    ): ApiResponse<PrinterDto> {
        return ApiResponse.success(
            printerService.upsertPrinter(branchId, req.copy(id = printerId)),
            "Printer updated"
        )
    }

    @DeleteMapping("/{printerId}")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN') or hasAuthority('ROLE_ADMIN') or hasAuthority('POS_CONFIG_MANAGE') or hasAuthority('MENU_MANAGE')")
    fun delete(@PathVariable branchId: String, @PathVariable printerId: String): ApiResponse<Boolean> {
        printerService.deletePrinter(branchId, printerId)
        return ApiResponse.success(true, "Printer deleted")
    }
}
