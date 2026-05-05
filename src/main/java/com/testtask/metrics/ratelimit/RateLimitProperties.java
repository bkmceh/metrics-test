package com.testtask.metrics.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Лимиты Redis: RPS на клиента и глобальный RPS для записи метрик.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(int perClientRps, int globalRps) {
}
