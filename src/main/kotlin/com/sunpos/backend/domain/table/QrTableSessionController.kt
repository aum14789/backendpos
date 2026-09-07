package com.sunpos.backend.domain.table

import com.sunpos.backend.common.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/tables", "/api/v1/tables")
class QrTableSessionController(
    private val qrTableSessionService: QrTableSessionService,
    private val tableRepository: TableRepository
) {

    @PostMapping("/{tableId}/qr-session")
    fun openQrSession(
        @PathVariable tableId: String,
        @RequestBody(required = false) request: OpenQrSessionRequest?
    ): ResponseEntity<ApiResponse<QrSessionResponseDto>> {
        val table = tableRepository.findById(tableId).orElseThrow {
            IllegalArgumentException("Table not found with id: $tableId")
        }

        val session = qrTableSessionService.createSession(
            branchId = table.branchId,
            tableId = table.id,
            tableNumber = table.nameNumber,
            openedBy = request?.openedBy,
            expiresAt = request?.expiresAt
        )

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success(qrTableSessionService.toResponseDto(session), "QR session opened successfully"))
    }

    @GetMapping("/{tableId}/qr-session/active")
    fun getActiveQrSession(
        @PathVariable tableId: String
    ): ResponseEntity<ApiResponse<QrSessionResponseDto>> {
        val activeSessionOpt = qrTableSessionService.getActiveSessionByTableId(tableId)
        if (activeSessionOpt.isEmpty) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("NOT_FOUND", "No active QR session found for table: $tableId"))
        }

        return ResponseEntity.ok(
            ApiResponse.success(qrTableSessionService.toResponseDto(activeSessionOpt.get()))
        )
    }

    @PostMapping("/{tableId}/qr-session/reprint")
    fun reprintQrSession(
        @PathVariable tableId: String
    ): ResponseEntity<ApiResponse<QrSessionResponseDto>> {
        val session = qrTableSessionService.regenerateToken(tableId)
        return ResponseEntity.ok(
            ApiResponse.success(qrTableSessionService.toResponseDto(session), "QR session token regenerated successfully")
        )
    }

    @PostMapping("/{tableId}/qr-session/close")
    fun closeQrSession(
        @PathVariable tableId: String
    ): ResponseEntity<ApiResponse<List<QrSessionResponseDto>>> {
        val closed = qrTableSessionService.closeSession(tableId)
        val dtos = closed.map { qrTableSessionService.toResponseDto(it) }
        return ResponseEntity.ok(
            ApiResponse.success(dtos, "QR session closed successfully")
        )
    }
}
