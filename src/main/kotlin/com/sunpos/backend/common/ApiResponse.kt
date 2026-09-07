package com.sunpos.backend.common

import java.time.Instant

data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
    val error: ApiError? = null,
    val timestamp: Instant = Instant.now()
) {
    companion object {
        fun <T> success(data: T, message: String? = "Success"): ApiResponse<T> = ApiResponse(
            success = true,
            data = data,
            message = message
        )

        fun <T> error(code: String, message: String, details: List<Map<String, String>>? = null): ApiResponse<T> = ApiResponse(
            success = false,
            error = ApiError(code = code, message = message, details = details)
        )
    }
}

data class ApiError(
    val code: String,
    val message: String,
    val details: List<Map<String, String>>? = null
)
