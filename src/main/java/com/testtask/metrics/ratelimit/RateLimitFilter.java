package com.testtask.metrics.ratelimit;

import com.testtask.metrics.security.ApiJsonErrorWriter;
import com.testtask.metrics.service.RedisRateLimiter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RedisRateLimiter rateLimiter;
    private final ApiJsonErrorWriter jsonErrorWriter;

    public RateLimitFilter(RedisRateLimiter rateLimiter, ApiJsonErrorWriter jsonErrorWriter) {
        this.rateLimiter = rateLimiter;
        this.jsonErrorWriter = jsonErrorWriter;
    }

    /**
     * Для {@code /metrics} — лимиты Redis; аутентификация уже выполнена {@link com.testtask.metrics.security.JwtAuthenticationFilter}.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/metrics")) {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                jsonErrorWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED,
                        "AUTHENTICATION_REQUIRED", "Authentication required");
                return;
            }
            String clientId = String.valueOf(authentication.getPrincipal());
            String method = request.getMethod();
            boolean withGlobalLimit = "POST".equalsIgnoreCase(method);
            String scope = method + ":" + path;
            if (!rateLimiter.isAllowed(clientId, scope, withGlobalLimit)) {
                jsonErrorWriter.write(response, HttpStatus.TOO_MANY_REQUESTS.value(),
                        "RATE_LIMIT_EXCEEDED", "Rate limit exceeded");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
