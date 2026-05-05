package com.testtask.metrics.logging;

import com.testtask.metrics.model.dto.ApiError;
import com.testtask.metrics.security.ApiJsonErrorWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Логирует все запросы, кроме Swagger/Prometheus:
 * "Получен запрос..." -> "Запрос обработан..." или "Произошла ошибка...".
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MetricsApiRequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(MetricsApiRequestLoggingFilter.class);

    private static final int CONTENT_CACHE_LIMIT_BYTES = 10 * 1024 * 1024;

    private final ApiJsonErrorWriter apiJsonErrorWriter;
    private final RequestLoggingProperties loggingProperties;

    public MetricsApiRequestLoggingFilter(ApiJsonErrorWriter apiJsonErrorWriter, RequestLoggingProperties loggingProperties) {
        this.apiJsonErrorWriter = apiJsonErrorWriter;
        this.loggingProperties = loggingProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return isSwaggerOrPrometheus(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String headerValue = request.getHeader(RequestIdConstants.REQUEST_ID);
        String id;
        if (headerValue != null && !headerValue.isBlank()) {
            try {
                id = UUID.fromString(headerValue.trim()).toString();
            } catch (IllegalArgumentException ex) {
                ApiError error = new ApiError(
                        "INVALID_REQUEST_ID",
                        "Заголовок request-id не в необходимом формате",
                        List.of()
                );
                apiJsonErrorWriter.write(response, HttpServletResponse.SC_BAD_REQUEST, error);
                return;
            }
        } else {
            id = UUID.randomUUID().toString();
        }

        response.setHeader(RequestIdConstants.REQUEST_ID, id);
        MDC.put(RequestIdConstants.REQUEST_ID, id);

        ContentCachingRequestWrapper wrapped = new ContentCachingRequestWrapper(request, CONTENT_CACHE_LIMIT_BYTES);
        long startNs = System.nanoTime();
        Throwable thrown = null;
        try {
            filterChain.doFilter(wrapped, response);
        } catch (Throwable ex) {
            thrown = ex;
            throw ex;
        } finally {
            String path = request.getServletPath();
            String method = request.getMethod();
            String phrase = incomingPhrase(path, method);
            String query = request.getQueryString();
            String queryParams = query == null || query.isBlank() ? "-" : query;
            String bodyRaw = bodyForLog(wrapped);
            long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
            int status = response.getStatus();

            log.info(
                    "Получен запрос на {}. request-id={} method={} path={} queryParams={} body={}",
                    phrase,
                    id,
                    method,
                    path,
                    queryParams,
                    bodyRaw
            );

            if (thrown == null && status < 400) {
                log.info("Запрос обработан. request-id={} status={} durationMs={}", id, status, durationMs);
            } else {
                String errorText = thrown == null ? ("HTTP " + status) : (thrown.getClass().getSimpleName() + ": " + thrown.getMessage());
                log.error("Произошла ошибка. request-id={} status={} durationMs={} error={}", id, status, durationMs, errorText);
            }

            MDC.remove(RequestIdConstants.REQUEST_ID);
        }
    }

    private static boolean isSwaggerOrPrometheus(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path == null) {
            return false;
        }
        if (path.startsWith("/swagger-ui") || "/swagger-ui.html".equals(path)) {
            return true;
        }
        if (path.startsWith("/v3/api-docs")) {
            return true;
        }
        return path.contains("prometheus");
    }

    private static String incomingPhrase(String path, String method) {
        if ("/sign-up".equals(path)) {
            return "регистрацию клиента";
        }
        if ("/auth".equals(path)) {
            return "получение JWT";
        }
        if ("/metrics".equals(path) && HttpMethod.POST.matches(method)) {
            return "приём метрики";
        }
        if ("/metrics".equals(path) && HttpMethod.GET.matches(method)) {
            return "получение статистики метрик";
        }
        return "операцию";
    }

    private String bodyForLog(ContentCachingRequestWrapper wrapped) {
        byte[] buf = wrapped.getContentAsByteArray();
        if (buf == null || buf.length == 0) {
            return "-";
        }
        String s = new String(buf, StandardCharsets.UTF_8);
        int max = loggingProperties.maxRequestBodyLogChars();
        if (max <= 0) {
            max = 2048;
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max) + " ...[truncated]";
    }
}
