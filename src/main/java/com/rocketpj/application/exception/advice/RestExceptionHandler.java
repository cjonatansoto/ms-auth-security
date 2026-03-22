package com.rocketpj.application.exception.advice;

import com.rocketpj.application.config.MessageResolver;
import com.rocketpj.application.config.TraceIdFilter;
import com.rocketpj.application.exception.ApiProblemDetail;
import com.rocketpj.application.exception.BusinessException;
import com.rocketpj.application.exception.DatabaseConstraintRegistry;
import com.rocketpj.application.exception.EnumError;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Centralized Exception Handler with full i18n support:
 * - RFC-7807 Problem Detail format (extended via ApiProblemDetail)
 * - Traceability with traceId
 * - i18n (English / Spanish with fallback)
 * - Observability (Micrometer)
 * - Message sanitization
 */
@RestControllerAdvice
@Slf4j
@RequiredArgsConstructor
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    private final MessageResolver messageResolver;
    private final DatabaseConstraintRegistry databaseConstraintRegistry;
    private final Optional<MeterRegistry> meterRegistry;

    // ----------------------
    // VALIDATION ERRORS
    // ----------------------

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        var errors = extractFieldErrors(ex.getBindingResult());
        String detail = messageResolver.resolve("error.validation.failed", "Validation failed");
        return buildAndLogProblem(HttpStatus.BAD_REQUEST,
                EnumError.INVALID_ARGS,
                detail,
                Map.of("invalid-params", errors),
                "Validation failed: " + errors,
                ex,
                "VALIDATION");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Object> handleConstraintViolationException(ConstraintViolationException ex) {
        var errors = ex.getConstraintViolations().stream()
                .collect(Collectors.toMap(
                        v -> v.getPropertyPath().toString(),
                        ConstraintViolation::getMessage,
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ));

        String detail = messageResolver.resolve("error.constraint.violations", "Constraint violations");
        return buildAndLogProblem(HttpStatus.BAD_REQUEST,
                EnumError.INVALID_ARGS,
                detail,
                Map.of("invalid-params", errors),
                "Constraint violations: " + errors,
                ex,
                "VALIDATION");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Object> handleArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        Map<String, String> error = Map.of(ex.getName(),
                messageResolver.resolve("error.typeMismatch", "Invalid type"));
        String detail = messageResolver.resolve("error.argument.typeMismatch",
                "Invalid argument type: " + ex.getName(), new Object[]{ex.getName()});
        return buildAndLogProblem(HttpStatus.BAD_REQUEST,
                EnumError.INVALID_ARGS,
                detail,
                Map.of("invalid-params", error),
                "Type mismatch param=%s expected=%s value=%s".formatted(ex.getName(), ex.getRequiredType(), ex.getValue()),
                ex,
                "VALIDATION");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Object> handleIllegalArgument(IllegalArgumentException ex) {
        // Aquí es donde caerá el error "Missing required header: X-Device-Id"
        String detail = ex.getMessage(); // O usar messageResolver con una llave

        return buildAndLogProblem(HttpStatus.BAD_REQUEST,
                EnumError.INVALID_ARGS,
                detail,
                null,
                "Illegal argument: " + detail,
                ex,
                "SECURITY_VALIDATION");
    }

    // ----------------------
    // HTTP ERRORS
    // ----------------------

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        String detail = messageResolver.resolve("error.invalid.body", "Invalid body");
        return buildAndLogProblem(HttpStatus.BAD_REQUEST,
                EnumError.INVALID_BODY,
                detail,
                Map.of("invalid-params", Collections.emptyMap()),
                "Unreadable HTTP message",
                ex,
                "SYSTEM");
    }

    @Override
    protected ResponseEntity<Object> handleServletRequestBindingException(ServletRequestBindingException ex,
                                                                          HttpHeaders headers,
                                                                          HttpStatusCode status,
                                                                          WebRequest request) {
        String detail = messageResolver.resolve("error.binding", "Request binding error");
        return buildAndLogProblem(HttpStatus.BAD_REQUEST,
                EnumError.INVALID_ARGS,
                detail,
                Map.of("invalid-params", Collections.emptyMap()),
                "Servlet request binding error",
                ex,
                "SYSTEM");
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                                         HttpHeaders headers,
                                                                         HttpStatusCode status,
                                                                         WebRequest request) {
        String detail = messageResolver.resolve("error.method.not.allowed",
                "Method not allowed: " + ex.getMethod(), new Object[]{ex.getMethod()});
        return buildAndLogProblem(HttpStatus.METHOD_NOT_ALLOWED,
                EnumError.NO_SUPPORT,
                detail,
                Map.of("invalid-params", Collections.emptyMap()),
                "Method not allowed: " + ex.getMethod(),
                ex,
                "SYSTEM");
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(NoResourceFoundException ex,
                                                                    HttpHeaders headers,
                                                                    HttpStatusCode status,
                                                                    WebRequest request) {
        String detail = messageResolver.resolve("error.not.found", "Resource not found");
        return buildAndLogProblem(HttpStatus.NOT_FOUND,
                EnumError.NO_RESOURCE_FOUND,
                detail,
                Map.of("invalid-params", Collections.emptyMap()),
                "Resource not found",
                ex,
                "SYSTEM");
    }

    // ----------------------
    // BUSINESS & DATABASE
    // ----------------------

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Object> handleBusinessException(BusinessException ex) {
        HttpStatus status = ex.getStatus();
        String detail = messageResolver.resolve(ex.getMessageKey(), "Business error");
        return buildAndLogProblem(
                status,
                ex.getErrorEnum(),
                detail,
                Map.of("invalid-params", Collections.emptyMap()),
                "Business exception: [%d - %s] %s"
                        .formatted(status.value(), ex.getCode(), ex.getMessageKey()),
                ex,
                "BUSINESS"
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Object> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String mappedField = "unknown";
        if (ex.getCause() instanceof org.hibernate.exception.ConstraintViolationException cve) {
            mappedField = Optional.ofNullable(databaseConstraintRegistry.getFieldByConstraint(cve.getConstraintName()))
                    .orElse("unknown");
        }

        Map<String, String> errors = Map.of(mappedField,
                messageResolver.resolve("error.duplicate", "Duplicate value"));

        String detail = messageResolver.resolve("error.uniqueConstraint.detail",
                "Unique constraint violation on " + mappedField, new Object[]{mappedField});
        return buildAndLogProblem(HttpStatus.CONFLICT,
                EnumError.UNIQUE_CONSTRAINT,
                detail,
                Map.of("invalid-params", errors),
                "Data integrity violation on field " + mappedField,
                ex,
                "BUSINESS");
    }

    // ----------------------
    // FALLBACK
    // ----------------------

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleAllExceptions(Exception ex) {
        String detail = messageResolver.resolve("error.default", "Unexpected error");
        return buildAndLogProblem(HttpStatus.INTERNAL_SERVER_ERROR,
                EnumError.DEFAULT,
                detail,
                Map.of("invalid-params", Collections.emptyMap(), "timestamp", LocalDateTime.now().toString()),
                "Unhandled exception: " + ex.getClass().getName(),
                ex,
                "SYSTEM");
    }

    // ----------------------
    // HELPERS
    // ----------------------

    private ResponseEntity<Object> buildAndLogProblem(HttpStatus status,
                                                      EnumError error,
                                                      @Nullable String detail,
                                                      @Nullable Map<String, ?> additional,
                                                      String logMessage,
                                                      Exception ex,
                                                      String category) {

        RequestInfo ri = getRequestInfo();
        String traceId = ri.traceId();

        // SECURITY: Sanitizar detail para evitar fuga de información
        String sanitizedDetail = sanitizeDetail(detail, status);

        ApiProblemDetail pd = new ApiProblemDetail(status,
                messageResolver.resolve(error.getMessageKey(), error.name()),
                error.getCode())
                .traceId(traceId)
                .path(ri.path())
                .method(ri.method())
                .detail(sanitizedDetail);

        if (additional != null && !additional.isEmpty()) {
            additional.forEach((k, v) -> {
                if (v == null) return;
                if (v instanceof Map<?, ?> map && map.isEmpty()) return;
                if (v instanceof Collection<?> col && col.isEmpty()) return;
                if (v instanceof CharSequence cs && cs.isEmpty()) return;
                pd.setProperty(k, v);
            });
        }

        pd.getProperties().entrySet().removeIf(e -> {
            Object v = e.getValue();
            if (v == null) return true;
            if (v instanceof Map<?, ?> m && m.isEmpty()) return true;
            if (v instanceof Collection<?> c && c.isEmpty()) return true;
            if (v instanceof CharSequence cs && cs.isEmpty()) return true;
            return false;
        });

        recordMetric("app.error", error.name(), status, ri.path());

        // SECURITY: No exponer stack traces en producción
        // Log completo solo en DEBUG, sanitizado en producción
        if (status.is5xxServerError()) {
            if (log.isDebugEnabled()) {
                log.error("[{}] {} traceId={}", category, logMessage, traceId, ex);
            } else {
                // En producción: solo mensaje, sin stack trace
                log.error("[{}] {} traceId={} exception={}",
                        category, logMessage, traceId, ex.getClass().getSimpleName());
            }
        } else if (log.isDebugEnabled()) {
            log.debug("[{}] {} traceId={}", category, logMessage, traceId, ex);
        } else {
            log.warn("[{}] {} traceId={}", category, logMessage, traceId);
        }

        return ResponseEntity.status(status)
                .header("X-Trace-Id", traceId != null ? traceId : "")
                .body(pd);
    }


    private LinkedHashMap<String, String> extractFieldErrors(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        FieldError::getDefaultMessage,
                        (existing, replacement) -> existing,
                        LinkedHashMap::new
                ));
    }

    private RequestInfo getRequestInfo() {
        String path = null, method = null, traceId = MDC.get(TraceIdFilter.TRACE_ID);

        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest request = servletAttributes.getRequest();
            path = request.getRequestURI();
            method = request.getMethod();
        }
        return new RequestInfo(path, method, traceId);
    }

    private void recordMetric(String name, String tagValue, HttpStatus status, String path) {
        meterRegistry.ifPresent(reg -> {
            try {
                reg.counter(name,
                        "type", tagValue,
                        "status", String.valueOf(status.value()),
                        "path", path != null ? path : "unknown").increment();
            } catch (Exception e) {
                log.debug("Metric recording failed: {}", e.getMessage());
            }
        });
    }

    /**
     * Sanitiza el mensaje de detalle para evitar fuga de información sensible.
     * En producción, remueve stack traces, paths de archivos, y otros datos internos.
     */
    private String sanitizeDetail(String detail, HttpStatus status) {
        if (detail == null) {
            return null;
        }

        // En producción (no DEBUG), sanitizar mensajes de error 5xx
        if (status.is5xxServerError() && !log.isDebugEnabled()) {
            // Remover paths de archivos, stack traces, etc.
            return detail
                    .replaceAll("at \\w+\\.\\w+\\.\\w+\\([^)]+\\)", "")
                    .replaceAll("Caused by:.*", "")
                    .replaceAll("java\\.(io|lang|sql|util)\\.", "")
                    .replaceAll("/[^\\s]+\\.java:\\d+", "")
                    .trim();
        }

        return detail;
    }

    private record RequestInfo(String path, String method, String traceId) {}
}
