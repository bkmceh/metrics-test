package com.testtask.metrics.security;

import com.testtask.metrics.service.JwtService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final ApiJsonErrorWriter jsonErrorWriter;

    public JwtAuthenticationFilter(JwtService jwtService, ApiJsonErrorWriter jsonErrorWriter) {
        this.jwtService = jwtService;
        this.jsonErrorWriter = jsonErrorWriter;
    }

    /**
     * Для {@code /metrics} — обязательный валидный {@code Authorization: Bearer &lt;JWT&gt;} с разбором ошибок.
     * Для остальных путей — при валидном Bearer опционально выставляет аутентификацию (чтобы лишний заголовок не ломал {@code /auth}).
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (isMetricsRequest(request)) {
            BearerExtraction extraction = BearerExtraction.parse(header);
            switch (extraction.type()) {
                case MISSING -> {
                    jsonErrorWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED,
                            "AUTHENTICATION_REQUIRED", "Missing Authorization header with Bearer token");
                    return;
                }
                case NOT_BEARER_SCHEME -> {
                    jsonErrorWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED,
                            "EXPECTED_BEARER", "Authorization must use scheme Bearer");
                    return;
                }
                case EMPTY_TOKEN -> {
                    jsonErrorWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED,
                            "INVALID_TOKEN", "Bearer token is empty");
                    return;
                }
                case OK -> {
                    try {
                        authenticateWithToken(extraction.token());
                    } catch (ExpiredJwtException ex) {
                        jsonErrorWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED,
                                "TOKEN_EXPIRED", "JWT has expired");
                        return;
                    } catch (JwtException ex) {
                        jsonErrorWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED,
                                "INVALID_TOKEN", "Invalid JWT");
                        return;
                    }
                }
            }
            filterChain.doFilter(request, response);
            return;
        }

        BearerExtraction extraction = BearerExtraction.parse(header);
        if (extraction.type() == BearerExtraction.Type.OK) {
            try {
                authenticateWithToken(extraction.token());
            } catch (JwtException ex) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    private void authenticateWithToken(String token) {
        String clientId = jwtService.extractClientId(token);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                clientId,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_CLIENT"))
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private boolean isMetricsRequest(HttpServletRequest request) {
        String path = request.getServletPath();
        String method = request.getMethod();
        return "/metrics".equals(path)
                && (HttpMethod.GET.matches(method) || HttpMethod.POST.matches(method));
    }

    private record BearerExtraction(Type type, String token) {

        enum Type {
            MISSING,
            NOT_BEARER_SCHEME,
            EMPTY_TOKEN,
            OK
        }

        static BearerExtraction parse(String header) {
            if (header == null || header.isBlank()) {
                return new BearerExtraction(Type.MISSING, null);
            }
            String trimmed = header.trim();
            if (trimmed.length() < BEARER_PREFIX.length()
                    || !trimmed.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
                return new BearerExtraction(Type.NOT_BEARER_SCHEME, null);
            }
            String token = trimmed.substring(BEARER_PREFIX.length()).trim();
            if (token.isEmpty()) {
                return new BearerExtraction(Type.EMPTY_TOKEN, null);
            }
            return new BearerExtraction(Type.OK, token);
        }
    }
}
