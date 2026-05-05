package com.testtask.metrics.service;

import com.testtask.metrics.model.dto.MetricDtos;
import com.testtask.metrics.model.entity.MetricEntity;
import com.testtask.metrics.model.entity.MetricStatsResult;
import com.testtask.metrics.repository.MetricRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class MetricsService {

    private final MetricRepository metricRepository;

    public MetricsService(MetricRepository metricRepository) {
        this.metricRepository = metricRepository;
    }

    /**
     * Создаёт запись метрики от имени клиента из JWT (ТЗ: сохранение в БД).
     *
     * @param authentication principal — {@code clientId} из токена
     * @param request          timestamp, value, payload
     * @return id и статус {@code stored}
     */
    public MetricDtos.MetricCreateResponse createMetric(Authentication authentication, MetricDtos.MetricCreateRequest request) {
        MetricEntity metric = new MetricEntity();
        metric.setId(UUID.randomUUID());
        metric.setClientId(String.valueOf(authentication.getPrincipal()));
        metric.setTimestamp(request.timestamp());
        metric.setValue(request.value());
        metric.setPayload(request.payload());
        metricRepository.save(metric);
        return new MetricDtos.MetricCreateResponse(metric.getId(), "stored");
    }

    /**
     * Возвращает count/avg/min/max по всем метрикам за интервал (ТЗ: query {@code from}, {@code to}).
     *
     * @param from нижняя граница {@code ts}
     * @param to   верхняя граница {@code ts}
     */
    public MetricDtos.MetricsStatsResponse stats(OffsetDateTime from, OffsetDateTime to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must be before or equal to 'to'");
        }
        MetricStatsResult result = metricRepository.aggregate(from, to);
        return new MetricDtos.MetricsStatsResponse(
                result.count(),
                result.avg(),
                result.min(),
                result.max()
        );
    }
}
