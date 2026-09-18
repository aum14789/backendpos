package com.sunpos.backend.common

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException::class)
    fun handleAccessDenied(ex: AccessDeniedException): ResponseEntity<ApiResponse<Nothing>> {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error("INSUFFICIENT_PERMISSIONS", ex.message ?: "Access Denied"))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationErrors(ex: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Nothing>> {
        val details = ex.bindingResult.fieldErrors.map {
            mapOf("field" to it.field, "issue" to (it.defaultMessage ?: "ค่าของฟิลด์นี้ไม่ถูกต้อง"))
        }
        val firstErrorMessage = ex.bindingResult.fieldErrors.firstOrNull()?.defaultMessage
            ?: "ข้อมูลที่ส่งมาไม่ถูกต้องตามเงื่อนไข"
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error("VALIDATION_FAILED", firstErrorMessage, details))
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadable(ex: org.springframework.http.converter.HttpMessageNotReadableException): ResponseEntity<ApiResponse<Nothing>> {
        val rootCause = ex.rootCause
        val message = when (rootCause) {
            is com.fasterxml.jackson.databind.exc.MismatchedInputException -> {
                val fieldName = rootCause.path.joinToString(".") { it.fieldName ?: "[${it.index}]" }
                "รูปแบบหรือประเภทข้อมูลของฟิลด์ '$fieldName' ไม่ถูกต้อง"
            }
            is com.fasterxml.jackson.core.JsonParseException -> "รูปแบบข้อมูล JSON ไม่ถูกต้อง"
            else -> "ข้อมูลที่ส่งมาไม่ถูกต้องหรือไม่สามารถประมวลผลได้"
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error("MALFORMED_REQUEST", message))
    }

    @ExceptionHandler(NoSuchElementException::class)
    fun handleNoSuchElement(ex: NoSuchElementException): ResponseEntity<ApiResponse<Nothing>> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ApiResponse.error("NOT_FOUND", ex.message ?: "Resource not found"))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ApiResponse<Nothing>> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error("INVALID_ARGUMENT", ex.message ?: "Invalid Argument"))
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(ex: IllegalStateException): ResponseEntity<ApiResponse<Nothing>> {
        val msg = ex.message ?: "Invalid state"
        val status = if (
            msg.contains("โต๊ะนี้ปิดแล้ว") ||
            msg.contains("งดให้บริการ") ||
            msg.contains("QR")
        ) HttpStatus.FORBIDDEN else HttpStatus.CONFLICT
        return ResponseEntity.status(status)
            .body(ApiResponse.error("ILLEGAL_STATE", msg))
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(ex: org.springframework.dao.DataIntegrityViolationException): ResponseEntity<ApiResponse<Nothing>> {
        val msg = if (ex.message?.contains("is not present in table") == true) {
            "ข้อมูลที่เชื่อมโยง (เช่น สาขา หรือ หมวดหมู่) ไม่ถูกต้องหรือไม่พบในระบบ: ${ex.rootCause?.message ?: ex.message}"
        } else if (ex.message?.contains("violates foreign key constraint") == true) {
            "ไม่สามารถดำเนินการได้เนื่องจากข้อมูลยังถูกอ้างอิงอยู่โดยรายการอื่นในระบบ (Foreign Key Constraint)"
        } else {
            "ข้อมูลขัดแย้งกับข้อจำกัดความสมบูรณ์ของฐานข้อมูล: ${ex.rootCause?.message ?: ex.message}"
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ApiResponse.error("DATA_INTEGRITY_VIOLATION", msg))
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception): ResponseEntity<ApiResponse<Nothing>> {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("INTERNAL_ERROR", ex.message ?: "An unexpected error occurred"))
    }
}

