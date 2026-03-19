package com.com.manasuniversityecosystem.api.v1.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * Universal response envelope for all /api/v1/** endpoints.
 *
 * Success:  { "status": 200, "timestamp": "...", "data": {...} }
 * Error:    { "status": 400, "timestamp": "...", "error_code": "VALIDATION_ERROR",
 *             "message": "...", "errors": [...] }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(

        @JsonProperty("status")
        int status,

        @JsonProperty("timestamp")
        Instant timestamp,

        @JsonProperty("data")
        T data,

        @JsonProperty("error_code")
        String errorCode,

        @JsonProperty("message")
        String message,

        @JsonProperty("errors")
        List<FieldError> errors

) {
    // ── Success factories ────────────────────────────────────────────────

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(200, Instant.now(), data, null, null, null);
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(201, Instant.now(), data, null, null, null);
    }

    public static ApiResponse<Void> noContent() {
        return new ApiResponse<>(204, Instant.now(), null, null, null, null);
    }

    // ── Error factories ──────────────────────────────────────────────────

    public static <T> ApiResponse<T> error(int status, ErrorCode code, String message) {
        return new ApiResponse<>(status, Instant.now(), null, code.name(), message, null);
    }

    public static <T> ApiResponse<T> validationError(List<FieldError> errors) {
        return new ApiResponse<>(400, Instant.now(), null,
                ErrorCode.VALIDATION_ERROR.name(), "Validation failed", errors);
    }

    // ── Nested types ─────────────────────────────────────────────────────

    public record FieldError(
            @JsonProperty("field")   String field,
            @JsonProperty("message") String message
    ) {}
}