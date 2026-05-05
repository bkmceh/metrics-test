package com.testtask.metrics.service;

import com.testtask.metrics.security.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final JwtProperties properties;
    private final SecretKey secretKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.secretKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Собирает подписанный JWT с subject = {@code clientId}.
     *
     * @param clientId идентификатор клиента
     * @return строка JWT
     */
    public String issueToken(String clientId) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(properties.ttlSeconds());
        return Jwts.builder()
                .subject(clientId)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(secretKey)
                .compact();
    }

    /**
     * Парсит и проверяет подпись JWT, возвращает subject (clientId).
     *
     * @param token полный JWT без префикса Bearer
     * @return clientId из поля subject
     */
    public String extractClientId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }
}
