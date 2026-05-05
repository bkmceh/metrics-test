package com.testtask.metrics.config;

import com.testtask.metrics.logging.RequestLoggingProperties;
import com.testtask.metrics.ratelimit.RateLimitProperties;
import com.testtask.metrics.security.JwtProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, RateLimitProperties.class, RequestLoggingProperties.class})
public class AppConfig {
}
