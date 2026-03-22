package com.rocketpj.application.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum EnumError {
    // --- General ---
    DEFAULT("E_UNKNOWN_ERROR", "error.default"),
    SECURITY_VIOLATION("E_SECURITY_VIOLATION", "auth.session.compromised"),
    // --- Validation ---
    INVALID_ARGS("E_INVALID_PARAMETERS", "error.invalid.args"),
    INVALID_BODY("E_INVALID_REQUEST_BODY", "error.invalid.body"),
    CONVERSION_ERROR("E_CONVERSION_ERROR", "error.conversion.failed"),

    EMAIL_ALREADY_EXISTS("E_EMAIL_EXISTS", "auth.email.already_registered"),
    SAME_EMAIL_PROVIDED("E_SAME_EMAIL", "auth.email.change.same"),
    INVALID_VERIFICATION_CODE("E_INVALID_CODE", "auth.verification.invalid_code"),
    // --- Resource ---
    NO_CONTENT("E_NO_RECORDS_FOUND", "error.no.content"),
    NO_RESOURCE_FOUND("E_RESOURCE_NOT_FOUND", "error.no.resource"),

    // --- Conflict / Uniqueness ---
    UNIQUE_CONSTRAINT("E_CONFLICT_RESOURCE", "error.duplicate"),

    // --- Provider Hub specific ---
    CAT_NOT_FOUND("E_CAT_NOT_FOUND", "error.category.not.found"),
    PRV_NOT_FOUND("E_PRV_NOT_FOUND", "error.provider.not.found"),
    PRESENCE_NOT_FOUND("E_PRESENCE_NOT_FOUND", "error.presence.not.found"),
    STATUS_INVALID("E_STATUS_INVALID", "error.status.invalid"),
    DUPLICATE_CODE("E_DUPLICATE_CODE", "error.duplicate.code"),
    DUPLICATE_PROVIDER_COUNTRY("E_DUPLICATE_PROVIDER_COUNTRY", "error.duplicate.provider.country"),
    CATEGORY_HAS_PROVIDERS("E_CATEGORY_HAS_PROVIDERS", "error.category.has.providers"),
    PROVIDER_HAS_PRESENCES("E_PROVIDER_HAS_PRESENCES", "error.provider.has.presences"),
    VALIDATION_ERROR("E_VALIDATION_ERROR", "error.validation.failed"),
    MEDIA_UPLOAD_FAILED("E_MEDIA_UPLOAD_FAILED", "error.media.upload.failed"),
    MEDIA_DELETE_FAILED("E_MEDIA_DELETE_FAILED", "error.media.delete.failed"),

    // --- Authentication & Authorization ---
    INVALID_CREDENTIALS("E_INVALID_CREDENTIALS", "auth.login.invalid_credentials"),
    ACCOUNT_NOT_ACTIVE("E_ACCOUNT_NOT_ACTIVE", "auth.login.disabled"),
    INVALID_TOKEN("E_INVALID_TOKEN", "auth.token.invalid"),
    TOKEN_REVOKED("E_TOKEN_REVOKED", "auth.token.invalid"),
    TOKEN_EXPIRED("E_TOKEN_EXPIRED", "auth.token.expired"),

    // --- HTTP 4xx ---
    NO_SUPPORT("E_METHOD_NOT_SUPPORTED", "error.method.not.allowed"),
    BAD_REQUEST("E_BAD_REQUEST", "error.bad_request"),
    UNAUTHORIZED("E_UNAUTHORIZED", "error.unauthorized"),
    FORBIDDEN("E_FORBIDDEN", "error.forbidden"),
    NOT_FOUND("E_NOT_FOUND", "error.not.found"),
    METHOD_NOT_ALLOWED("E_METHOD_NOT_ALLOWED", "error.method.not.allowed"),
    NOT_ACCEPTABLE("E_NOT_ACCEPTABLE", "error.not.acceptable"),
    GONE("E_GONE", "error.gone"),
    UNSUPPORTED_MEDIA_TYPE("E_UNSUPPORTED_MEDIA_TYPE", "error.unsupported.media"),
    UNPROCESSABLE_ENTITY("E_UNPROCESSABLE_ENTITY", "error.unprocessable.entity"),
    TOO_MANY_REQUESTS("E_TOO_MANY_REQUESTS", "error.too_many_requests"),

    // --- HTTP 5xx ---
    INTERNAL_SERVER_ERROR("E_INTERNAL_SERVER_ERROR", "error.internal_server_error"),
    NOT_IMPLEMENTED("E_NOT_IMPLEMENTED", "error.not.implemented"),
    BAD_GATEWAY("E_BAD_GATEWAY", "error.bad.gateway"),
    SERVICE_UNAVAILABLE("E_SERVICE_UNAVAILABLE", "error.service.unavailable"),
    GATEWAY_TIMEOUT("E_GATEWAY_TIMEOUT", "error.gateway.timeout"),
    HTTP_VERSION_NOT_SUPPORTED("E_HTTP_VERSION_NOT_SUPPORTED", "error.http.version");

    private final String code;
    private final String messageKey;
}
