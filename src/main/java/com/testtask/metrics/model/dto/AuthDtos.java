package com.testtask.metrics.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public final class AuthDtos {
    private AuthDtos() {
    }

    /**
     * Тело POST /auth по ТЗ: поле {@code clientID}.
     */
    public record AuthRequest(@NotBlank @JsonProperty("clientID") String clientID) {
    }

    /**
     * Ответ POST /auth по ТЗ: JWT.
     */
    public record AuthResponse(@JsonProperty("jwt") String jwt) {
    }

    /**
     * Тело POST /sign-up: уникальный {@code clientId} нового клиента.
     */
    public record SignUpRequest(@NotBlank @JsonProperty("clientId") String clientId) {
    }

    /**
     * Ответ POST /sign-up после сохранения клиента в БД.
     */
    public record SignUpResponse(@JsonProperty("clientId") String clientId, String status) {
    }
}
