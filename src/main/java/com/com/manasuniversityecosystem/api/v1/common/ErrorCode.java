package com.com.manasuniversityecosystem.api.v1.common;

/**
 * Machine-readable error codes returned in every error response.
 * Mobile apps switch on these codes — never parse human-readable messages.
 */
public enum ErrorCode {

    // ── Auth ────────────────────────────────────────────────────────────
    INVALID_CREDENTIALS,
    TOKEN_EXPIRED,
    TOKEN_INVALID,
    ACCOUNT_PENDING,
    ACCOUNT_SUSPENDED,
    OBIS_UNAVAILABLE,
    OBIS_INVALID_CREDENTIALS,

    // ── Authorization ───────────────────────────────────────────────────
    ACCESS_DENIED,
    FORBIDDEN,

    // ── Validation ──────────────────────────────────────────────────────
    VALIDATION_ERROR,
    MISSING_PARAMETER,

    // ── Resource ────────────────────────────────────────────────────────
    NOT_FOUND,
    ALREADY_EXISTS,
    CONFLICT,

    // ── File Upload ─────────────────────────────────────────────────────
    FILE_TOO_LARGE,
    UNSUPPORTED_MEDIA_TYPE,
    UPLOAD_FAILED,

    // ── Business Logic ──────────────────────────────────────────────────
    ALREADY_APPLIED,
    ALREADY_REGISTERED,
    ALREADY_MEMBER,
    NOT_MEMBER,
    CAPACITY_FULL,
    OPERATION_NOT_ALLOWED,

    // ── Server ──────────────────────────────────────────────────────────
    INTERNAL_ERROR
}