package com.testtask.metrics.security;

import com.testtask.metrics.ratelimit.RateLimitFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    /**
     * Stateless JWT; публично {@code /auth}, {@code /sign-up}, health, OpenAPI; резервный entry point — JSON 401.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            RateLimitFilter rateLimitFilter,
            ApiJsonErrorWriter jsonErrorWriter
    ) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/auth", "/sign-up").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(HttpMethod.GET, "/metrics").authenticated()
                        .requestMatchers(HttpMethod.POST, "/metrics").authenticated()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) ->
                                writeUnauthorized(jsonErrorWriter, response, authException))
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            try {
                                jsonErrorWriter.write(response, HttpServletResponse.SC_FORBIDDEN,
                                        "FORBIDDEN", accessDeniedException.getMessage() == null
                                                ? "Forbidden"
                                                : accessDeniedException.getMessage());
                            } catch (Exception e) {
                                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                            }
                        })
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class)
                .build();
    }

    private static void writeUnauthorized(ApiJsonErrorWriter writer, HttpServletResponse response,
                                          AuthenticationException authException) {
        try {
            String message = authException != null && authException.getMessage() != null
                    ? authException.getMessage()
                    : "Authentication required";
            writer.write(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", message);
        } catch (Exception e) {
            try {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            } catch (Exception ignored) {
            }
        }
    }
}
