package com.testtask.metrics.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Параметры подписи JWT: секрет HMAC и время жизни access-токена (сек).
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, long ttlSeconds) {
}
