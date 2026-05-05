package com.testtask.metrics.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.testtask.metrics.model.entity.MetricEntity;
import com.testtask.metrics.model.entity.MetricStatsResult;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;

@Repository
public class MetricRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public MetricRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Вставляет строку в {@code metrics}.
     *
     * @param entity заполненная сущность метрики
     */
    public void save(MetricEntity entity) {
        String sql = """
                INSERT INTO metrics (id, client_id, ts, value, payload, created_at, updated_at)
                VALUES (:id, :clientId, :ts, :value, CAST(:payload AS jsonb), now(), now())
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", entity.getId())
                .addValue("clientId", entity.getClientId())
                .addValue("ts", entity.getTimestamp())
                .addValue("value", entity.getValue())
                .addValue("payload", jsonToString(entity.getPayload()));
        jdbcTemplate.update(sql, params);
    }

    /**
     * Агрегирует метрики за интервал по всем клиентам (как в ТЗ: статистика по метрикам в диапазоне {@code from}–{@code to}).
     *
     * @param from включительно по {@code ts}
     * @param to   включительно по {@code ts}
     * @return count, avg, min, max
     */
    public MetricStatsResult aggregate(OffsetDateTime from, OffsetDateTime to) {
        String sql = """
                SELECT COUNT(*) AS count, AVG(value) AS avg, MIN(value) AS min, MAX(value) AS max
                FROM metrics
                WHERE ts >= :from AND ts <= :to
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("from", from)
                .addValue("to", to);
        return jdbcTemplate.queryForObject(sql, params, (rs, rowNum) ->
                new MetricStatsResult(
                        rs.getLong("count"),
                        rs.getObject("avg") == null ? null : rs.getDouble("avg"),
                        rs.getBigDecimal("min"),
                        rs.getBigDecimal("max")
                )
        );
    }

    private String jsonToString(JsonNode node) {
        return node == null ? "{}" : node.toString();
    }
}
