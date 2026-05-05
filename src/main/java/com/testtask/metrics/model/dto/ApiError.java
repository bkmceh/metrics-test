package com.testtask.metrics.model.dto;

import java.util.List;

/** Унифицированное тело ошибки API. */
public record ApiError(String code, String message, List<ApiErrorDetail> details) {
    /** Детализация по полю (при ошибках валидации). */
    public record ApiErrorDetail(String field, String error) {
    }
}
