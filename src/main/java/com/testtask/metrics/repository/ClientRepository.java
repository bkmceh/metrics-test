package com.testtask.metrics.repository;

import com.testtask.metrics.model.entity.ClientEntity;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ClientRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ClientRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Ищет клиента по уникальному {@code client_id}.
     *
     * @param clientId строковый идентификатор
     * @return сущность или пусто
     */
    public Optional<ClientEntity> findByClientId(String clientId) {
        String sql = """
                SELECT id, client_id, secret_hash, enabled, created_at, updated_at
                FROM clients
                WHERE client_id = :clientId
                """;
        List<ClientEntity> rows = jdbcTemplate.query(sql, new MapSqlParameterSource("clientId", clientId), (rs, rowNum) -> {
            ClientEntity entity = new ClientEntity();
            entity.setId(rs.getObject("id", java.util.UUID.class));
            entity.setClientId(rs.getString("client_id"));
            entity.setSecretHash(rs.getString("secret_hash"));
            entity.setEnabled(rs.getBoolean("enabled"));
            entity.setCreatedAt(rs.getObject("created_at", java.time.OffsetDateTime.class));
            entity.setUpdatedAt(rs.getObject("updated_at", java.time.OffsetDateTime.class));
            return entity;
        });
        return rows.stream().findFirst();
    }

    /**
     * Вставляет нового клиента в таблицу {@code clients}.
     *
     * @param entity заполненная сущность (id, clientId, secretHash, enabled)
     */
    public void save(ClientEntity entity) {
        String sql = """
                INSERT INTO clients (id, client_id, secret_hash, enabled, created_at, updated_at)
                VALUES (:id, :clientId, :secretHash, :enabled, now(), now())
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", entity.getId())
                .addValue("clientId", entity.getClientId())
                .addValue("secretHash", entity.getSecretHash())
                .addValue("enabled", entity.isEnabled());
        jdbcTemplate.update(sql, params);
    }
}
