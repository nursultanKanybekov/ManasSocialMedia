package com.com.manasuniversityecosystem.api.v1.exception;

import com.com.manasuniversityecosystem.api.v1.common.ApiResponse;
import com.com.manasuniversityecosystem.api.v1.common.ErrorCode;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;

@RestControllerAdvice(basePackages = "com.com.manasuniversityecosystem.api.v1")
@Slf4j
public class ApiGlobalExceptionHandler {

    // ── Validation: @Valid on request body ──────────────────────────────
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<?>> handleValidation(MethodArgumentNotValidException ex) {
        List<ApiResponse.FieldError> errors = ex.getBindingResult()
                .getFieldErrors().stream()
                .map(fe -> new ApiResponse.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(ApiResponse.validationError(errors));
    }

    // ── Validation: @Validated on path/query params ──────────────────────
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<?>> handleConstraint(ConstraintViolationException ex) {
        List<ApiResponse.FieldError> errors = ex.getConstraintViolations().stream()
                .map(cv -> new ApiResponse.FieldError(
                        cv.getPropertyPath().toString(), cv.getMessage()))
                .toList();
        return ResponseEntity.badRequest().body(ApiResponse.validationError(errors));
    }

    // ── Auth: wrong email/password ───────────────────────────────────────
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<?>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(401, ErrorCode.INVALID_CREDENTIALS,
                        "Invalid email or password."));
    }

    // ── Auth: account disabled/pending ───────────────────────────────────
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiResponse<?>> handleDisabled(DisabledException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(403, ErrorCode.ACCOUNT_SUSPENDED,
                        "Your account is suspended or pending approval."));
    }

    // ── JWT: expired token ───────────────────────────────────────────────
    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<ApiResponse<?>> handleExpiredJwt(ExpiredJwtException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(401, ErrorCode.TOKEN_EXPIRED,
                        "Access token has expired. Please refresh."));
    }

    // ── JWT: invalid token ───────────────────────────────────────────────
    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ApiResponse<?>> handleInvalidJwt(JwtException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(401, ErrorCode.TOKEN_INVALID, "Invalid token."));
    }

    // ── Access denied ────────────────────────────────────────────────────
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<?>> handleAccess(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(403, ErrorCode.FORBIDDEN,
                        "You do not have permission to perform this action."));
    }

    // ── Resource not found ───────────────────────────────────────────────
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<?>> handleNotFound(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(404, ErrorCode.NOT_FOUND, ex.getMessage()));
    }

    // ── Business logic violations ────────────────────────────────────────
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<?>> handleSecurity(SecurityException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(403, ErrorCode.OPERATION_NOT_ALLOWED, ex.getMessage()));
    }

    // ── File too large ───────────────────────────────────────────────────
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<?>> handleFileSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error(413, ErrorCode.FILE_TOO_LARGE,
                        "File exceeds maximum allowed size (5 MB)."));
    }

    // ── Catch-all ────────────────────────────────────────────────────────
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<?>> handleGeneric(Exception ex) {
        log.error("Unhandled API error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(500, ErrorCode.INTERNAL_ERROR,
                        "An unexpected error occurred. Please try again."));
    }
}