package com.testtask.metrics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class MetricsSecurityIntegrationTest {

    private static final String JWT_SECRET =
            "test-jwt-secret-must-be-at-least-256-bits-for-hs256-algorithm-test-xx";

    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> String.valueOf(redis.getMappedPort(6379)));
        registry.add("app.jwt.secret", () -> JWT_SECRET);
        registry.add("app.jwt.ttl-seconds", () -> "3600");
    }

    private String url(String path) {
        return "http://127.0.0.1:" + port + path;
    }

    private String authTokenFor(String clientId) throws Exception {
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> authResponse = restTemplate.postForEntity(
                url("/auth"),
                new HttpEntity<>("{\"clientId\":\"" + clientId + "\"}", authHeaders),
                String.class
        );
        assertThat(authResponse.getStatusCode().value()).isEqualTo(200);
        return objectMapper.readTree(authResponse.getBody()).path("jwt").asText();
    }

    @Test
    void postAuthDemo_returnsJwt() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>("{\"clientId\":\"demo\"}", headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url("/auth"), entity, String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("jwt").asText()).isNotBlank();
    }

    @Test
    void postAuth_unknownUser_returns401() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>("{\"clientId\":\"unknown-client\"}", headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url("/auth"), entity, String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("message").asText()).isEqualTo("Unknown client");
    }

    @Test
    void getMetrics_withoutAuthorization_returns401Json() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(
                url("/metrics?from=2026-01-01T00:00:00Z&to=2026-12-31T23:59:59Z"),
                String.class
        );

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("code").asText()).isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    void getMetrics_nonBearerScheme_returns401ExpectedBearer() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic dGVzdA==");
        ResponseEntity<String> response = restTemplate.exchange(
                url("/metrics?from=2026-01-01T00:00:00Z&to=2026-12-31T23:59:59Z"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("code").asText()).isEqualTo("EXPECTED_BEARER");
    }

    @Test
    void getMetrics_invalidJwt_returns401InvalidToken() throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth("not-a-valid-jwt");
        ResponseEntity<String> response = restTemplate.exchange(
                url("/metrics?from=2026-01-01T00:00:00Z&to=2026-12-31T23:59:59Z"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("code").asText()).isEqualTo("INVALID_TOKEN");
    }

    @Test
    void getMetrics_expiredJwt_returns401TokenExpired() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        Instant past = Instant.now().minusSeconds(120);
        String expired = Jwts.builder()
                .subject("demo")
                .issuedAt(Date.from(past))
                .expiration(Date.from(past.plusSeconds(60)))
                .signWith(key)
                .compact();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(expired);
        ResponseEntity<String> response = restTemplate.exchange(
                url("/metrics?from=2026-01-01T00:00:00Z&to=2026-12-31T23:59:59Z"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("code").asText()).isEqualTo("TOKEN_EXPIRED");
    }

    @Test
    void getMetrics_withValidJwt_returns200() throws Exception {
        String jwt = authTokenFor("demo");

        HttpHeaders metricsHeaders = new HttpHeaders();
        metricsHeaders.setBearerAuth(jwt);
        ResponseEntity<String> response = restTemplate.exchange(
                url("/metrics?from=2026-01-01T00:00:00Z&to=2026-12-31T23:59:59Z"),
                HttpMethod.GET,
                new HttpEntity<>(metricsHeaders),
                String.class
        );

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("count").isIntegralNumber()).isTrue();
    }

    @Test
    void postMetrics_withValidJwt_returns201() throws Exception {
        String jwt = authTokenFor("demo");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(jwt);
        HttpEntity<String> request = new HttpEntity<>(
                "{\"timestamp\":\"2026-05-01T10:15:30Z\",\"value\":12.34,\"payload\":{\"source\":\"test\"}}",
                headers
        );

        ResponseEntity<String> response = restTemplate.postForEntity(url("/metrics"), request, String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(201);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("status").asText()).isEqualTo("stored");
        assertThat(body.path("id").asText()).isNotBlank();
    }

    @Test
    void postMetrics_invalidPayload_returns400() throws Exception {
        String jwt = authTokenFor("demo");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(jwt);
        HttpEntity<String> request = new HttpEntity<>(
                "{\"timestamp\":\"2026-05-01T10:15:30Z\",\"value\":-1,\"payload\":{}}",
                headers
        );

        ResponseEntity<String> response = restTemplate.postForEntity(url("/metrics"), request, String.class);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("code").asText()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void getMetrics_invalidQueryParams_returns400() throws Exception {
        String jwt = authTokenFor("demo");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        ResponseEntity<String> response = restTemplate.exchange(
                url("/metrics?from=2026-12-31T23:59:59Z&to=2026-01-01T00:00:00Z"),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("code").asText()).isEqualTo("BAD_REQUEST");
    }
}
