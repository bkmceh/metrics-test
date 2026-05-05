package com.testtask.metrics.service;

import com.testtask.metrics.ratelimit.RateLimitProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

@Service
public class RedisRateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;

    public RedisRateLimiter(StringRedisTemplate redisTemplate, RateLimitProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    /**
     * Скользящий лимит RPS на клиента и (для POST) глобальный лимит за текущую секунду.
     *
     * @param clientId        идентификатор из JWT
     * @param scope           различение операций (метод + путь)
     * @param withGlobalLimit если true — учитывается также глобальный счётчик (используется для POST /metrics)
     * @return true, если запрос разрешён
     */
    public boolean isAllowed(String clientId, String scope, boolean withGlobalLimit) {
        long epochSecond = Instant.now().getEpochSecond();
        String clientKey = "rl:client:" + clientId + ":" + scope + ":" + epochSecond;
        String globalKey = "rl:global:" + epochSecond;

        Long clientCount = redisTemplate.opsForValue().increment(clientKey);
        redisTemplate.expire(clientKey, 2, TimeUnit.SECONDS);

        if (clientCount == null) {
            return false;
        }

        if (!withGlobalLimit) {
            return clientCount <= properties.perClientRps();
        }

        Long globalCount = redisTemplate.opsForValue().increment(globalKey);
        redisTemplate.expire(globalKey, 2, TimeUnit.SECONDS);

        if (globalCount == null) {
            return false;
        }

        return clientCount <= properties.perClientRps() && globalCount <= properties.globalRps();
    }
}
