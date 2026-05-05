package com.testtask.metrics.model.entity;

import java.math.BigDecimal;

/** Результат SQL-агрегации COUNT/AVG/MIN/MAX по метрикам. */
public record MetricStatsResult(long count, Double avg, BigDecimal min, BigDecimal max) {
}
