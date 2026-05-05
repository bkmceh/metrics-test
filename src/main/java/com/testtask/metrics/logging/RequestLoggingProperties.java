package com.testtask.metrics.logging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code app.logging.max-request-body-log-chars} — максимум символов тела в логе (0 или отсутствие → 2048).
 */
@ConfigurationProperties(prefix = "app.logging")
public record RequestLoggingProperties(int maxRequestBodyLogChars) {
}
