package com.observability.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.observability.config.ObservabilityProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class LoggingFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String MDC_KEY = "traceId";
    private static final String ERROR_READING_BODY = "[Erro ao ler body]";

    private final ObservabilityProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return properties.getHttp().getExcludedPaths().stream()
                .anyMatch(path::startsWith);
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
                    kv("requestBody", maskSensitiveData(parseJson(requestBody))),
                    kv("responseBody", maskSensitiveData(parseJson(responseBody)))
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

    @SuppressWarnings("unchecked")
    private Object maskSensitiveData(Object data) {
        if (!properties.getMasking().isEnabled() || data == null) {
            return data;
        }

        if (data instanceof Map) {
            Map<String, Object> map = new LinkedHashMap<>((Map<String, Object>) data);

            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = entry.getKey().toLowerCase();

                // Mascaramento total
                if (properties.getMasking().getFullMaskFields().contains(key)) {
                    map.put(entry.getKey(), properties.getMasking().getMaskValue());
                }
                // Mascaramento parcial (CPF/CNPJ)
                else if (properties.getMasking().getPartialMaskFields().contains(key)) {
                    Object value = entry.getValue();
                    if (value instanceof String) {
                        map.put(entry.getKey(), maskCpfCnpj((String) value));
                    }
                }
                // Recursão
                else if (entry.getValue() instanceof Map) {
                    map.put(entry.getKey(), maskSensitiveData(entry.getValue()));
                } else if (entry.getValue() instanceof List) {
                    map.put(entry.getKey(), maskSensitiveData(entry.getValue()));
                }
            }
            return map;
        }

        if (data instanceof List) {
            List<Object> list = (List<Object>) data;
            return list.stream().map(this::maskSensitiveData).toList();
        }

        return data;
    }

    private String maskCpfCnpj(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        String digitsOnly = value.replaceAll("\\D", "");

        if (digitsOnly.length() <= 4) {
            return value.replaceAll("\\d", "*");
        }

        StringBuilder result = new StringBuilder();
        int digitIndex = 0;
        int totalDigits = digitsOnly.length();

        for (char c : value.toCharArray()) {
            if (Character.isDigit(c)) {
                digitIndex++;
                if (digitIndex <= 2 || digitIndex > totalDigits - 2) {
                    result.append(c);
                } else {
                    result.append('*');
                }
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }
}