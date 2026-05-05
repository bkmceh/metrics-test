package com.testtask.metrics.model.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class MetricDtos {
    private MetricDtos() {
    }

    /** POST /metrics: момент времени (с зоной), положительное значение, произвольный JSON payload. */
    public record MetricCreateRequest(
            @NotNull OffsetDateTime timestamp,
            @NotNull @DecimalMin(value = "0.0000001", inclusive = true) BigDecimal value,
            @NotNull JsonNode payload
    ) {
    }

    /** Ответ после сохранения метрики. */
    public record MetricCreateResponse(UUID id, String status) {
    }

    /** GET /metrics: агрегированная статистика за интервал. */
    public record MetricsStatsResponse(long count, Double avg, BigDecimal min, BigDecimal max) {
    }
}
