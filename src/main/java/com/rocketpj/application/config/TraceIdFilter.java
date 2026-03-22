package com.rocketpj.application.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

@Component
@Slf4j
public class TraceIdFilter implements Filter {

    public static final String TRACE_ID = "traceId";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        try {
            // Genera o reutiliza traceId si viene desde header (por ejemplo X-Trace-Id)
            String traceId = extractOrGenerateTraceId(request);
            MDC.put(TRACE_ID, traceId);

            log.debug("Assigned traceId: {}", traceId);

            chain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID);
        }
    }

    private String extractOrGenerateTraceId(ServletRequest request) {
        if (request instanceof HttpServletRequest httpRequest) {
            String incomingTraceId = httpRequest.getHeader("X-Trace-Id");
            if (incomingTraceId != null && !incomingTraceId.isBlank()) {
                // SECURITY: Sanitizar traceId para prevenir log injection
                String sanitized = sanitizeTraceId(incomingTraceId);
                if (sanitized != null && !sanitized.isBlank()) {
                    return sanitized;
                }
            }
        }
        return UUID.randomUUID().toString();
    }

    /**
     * Sanitiza el traceId para prevenir log injection.
     * Solo permite UUIDs válidos o strings alfanuméricos con guiones.
     */
    private String sanitizeTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            return null;
        }

        // Validar formato UUID o alfanumérico simple
        if (traceId.matches("^[a-fA-F0-9\\-]{36}$") || traceId.matches("^[a-zA-Z0-9\\-]{1,128}$")) {
            return traceId;
        }

        // Si no es válido, generar uno nuevo
        log.warn("Invalid traceId format received, generating new one. Original: {}", traceId);
        return UUID.randomUUID().toString();
    }
}
