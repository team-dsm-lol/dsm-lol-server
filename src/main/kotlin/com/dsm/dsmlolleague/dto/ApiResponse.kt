package com.dsm.dsmlolleague.dto

data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T? = null
) {
    companion object {
        fun <T> success(data: T, message: String = "성공"): ApiResponse<T> {
            return ApiResponse(true, message, data)
        }
        
        fun <T> success(message: String = "성공"): ApiResponse<T> {
            return ApiResponse(true, message, null)
        }
        
        fun <T> error(message: String): ApiResponse<T> {
            return ApiResponse(false, message, null)
        }
    }
}

data class ErrorResponse(
    val timestamp: String,
    val status: Int,
    val error: String,
    val message: String,
    val path: String
)

data class ValidationErrorResponse(
    val timestamp: String,
    val status: Int,
    val error: String,
    val message: String,
    val path: String,
    val validationErrors: Map<String, String>
) 