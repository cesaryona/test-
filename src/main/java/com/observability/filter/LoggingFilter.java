package com.observability.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Set;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LoggingFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String MDC_KEY = "traceId";
    private static final String ERROR_READING_BODY = "[Erro ao ler body]";

    private static final Set<String> EXCLUDED_PATHS = Set.of(
            "/actuator/health",
            "/actuator/metrics",
            "/favicon.ico"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return EXCLUDED_PATHS.stream().anyMatch(path::startsWith);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, traceId);

        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        try {
            logRequest(requestWrapper);
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            logResponse(requestWrapper, responseWrapper);
            responseWrapper.copyBodyToResponse();
            MDC.clear();
        }
    }

    private void logRequest(ContentCachingRequestWrapper request) {
        log.info("http",
                kv("type", "request"),
                kv("method", request.getMethod()),
                kv("path", request.getRequestURI()),
                kv("queryString", request.getQueryString()),
                kv("contentType", request.getContentType())
        );
    }

    private void logResponse(ContentCachingRequestWrapper request,
                             ContentCachingResponseWrapper response) {
        int status = response.getStatus();
        boolean hasError = status >= 400;

        if (hasError) {
            String requestBody = getBody(request.getContentAsByteArray(), request.getCharacterEncoding());
            String responseBody = getBody(response.getContentAsByteArray(), response.getCharacterEncoding());

            log.error("http",
                    kv("type", "response"),
                    kv("method", request.getMethod()),
                    kv("path", request.getRequestURI()),
                    kv("status", status),
                    kv("error", true),
                    kv("requestBody", parseJson(requestBody)),
                    kv("responseBody", parseJson(responseBody))
            );
        } else {
            log.info("http",
                    kv("type", "response"),
                    kv("method", request.getMethod()),
                    kv("path", request.getRequestURI()),
                    kv("status", status)
            );
        }
    }

    private String getBody(byte[] buf, String encoding) {
        if (buf.length > 0) {
            try {
                return new String(buf, encoding);
            } catch (UnsupportedEncodingException e) {
                return ERROR_READING_BODY;
            }
        }
        return null;
    }

    private Object parseJson(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(body, Object.class);
        } catch (JsonProcessingException e) {
            return body;
        }
    }
}