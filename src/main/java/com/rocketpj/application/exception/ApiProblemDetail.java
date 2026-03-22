package com.rocketpj.application.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;

/**
 * Extends Spring's ProblemDetail with additional metadata and null-safe helpers.
 * - RFC-7807 compliant
 * - Automatically omits null/empty fields in JSON
 * - Provides convenient builder-style API
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class ApiProblemDetail extends ProblemDetail {

    public ApiProblemDetail(HttpStatus status, String title, String code) {
        super(status.value());
        setTitle(title);
        setProperty("code", code);
        setProperty("timestamp", LocalDateTime.now().toString());
    }

    public ApiProblemDetail traceId(String traceId) {
        return addIfPresent("traceId", traceId);
    }

    public ApiProblemDetail path(String path) {
        return addIfPresent("path", path);
    }

    public ApiProblemDetail method(String method) {
        return addIfPresent("method", method);
    }

    public ApiProblemDetail detail(String detail) {
        setDetail(detail);
        return this;
    }

    public ApiProblemDetail type(URI type) {
        setType(type);
        return this;
    }

    /**
     * Adds a property to the ProblemDetail only if it is non-null and non-empty.
     * Supports String, Map, Collection and nested ProblemDetail types.
     */
    public ApiProblemDetail addIfPresent(String key, Object value) {
        if (value == null) return this;
        if (value instanceof CharSequence cs && cs.isEmpty()) return this;
        if (value instanceof Collection<?> c && c.isEmpty()) return this;
        if (value instanceof Map<?, ?> m && m.isEmpty()) return this;
        setProperty(key, value);
        return this;
    }
}
