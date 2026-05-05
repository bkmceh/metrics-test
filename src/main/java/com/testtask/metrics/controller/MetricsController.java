package com.testtask.metrics.controller;

import com.testtask.metrics.model.dto.MetricDtos;
import com.testtask.metrics.service.MetricsService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
@SecurityRequirement(name = "bearer-jwt")
public class MetricsController {

    private final MetricsService metricsService;

    public MetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    /**
     * POST /metrics: приём метрики (ТЗ), клиент из JWT.
     *
     * @param authentication субъект — {@code clientId} из токена
     * @param request        timestamp, value, payload
     * @return HTTP 201 и {@link MetricDtos.MetricCreateResponse}
     */
    @PostMapping("/metrics")
    public ResponseEntity<MetricDtos.MetricCreateResponse> create(
            Authentication authentication,
            @Valid @RequestBody MetricDtos.MetricCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(metricsService.createMetric(authentication, request));
    }

    /**
     * GET /metrics: статистика по метрикам за {@code from}–{@code to} (ТЗ), JWT обязателен.
     *
     * @param from начало интервала (ISO 8601 с зоной)
     * @param to   конец интервала (ISO 8601 с зоной)
     * @return HTTP 200 и {@link MetricDtos.MetricsStatsResponse}
     */
    @GetMapping("/metrics")
    public ResponseEntity<MetricDtos.MetricsStatsResponse> getStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to
    ) {
        return ResponseEntity.ok(metricsService.stats(from, to));
    }
}
