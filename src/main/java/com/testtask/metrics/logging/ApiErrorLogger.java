package com.testtask.metrics.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.testtask.metrics.model.dto.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Единое логирование тел {@link ApiError} для фильтров и {@link com.testtask.metrics.controller.GlobalExceptionHandler}.
 */
@Component
public class ApiErrorLogger {

    private static final Logger log = LoggerFactory.getLogger(ApiErrorLogger.class);

    private final ObjectMapper objectMapper;

    public ApiErrorLogger(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Формат: {@code Произошла ошибка. request-id=... code=... message=... details=...}.
     * Для всех 4xx/5xx используется уровень {@code ERROR}.
     */
    public void log(ApiError apiError, int httpStatus) {
        String id = MDC.get(RequestIdConstants.REQUEST_ID);
        if (id == null) {
            id = "-";
        }
        String detailsJson = formatDetails(apiError);
        String line = String.format(
                "Произошла ошибка. request-id=%s code=%s message=%s details=%s",
                id,
                apiError.code(),
                apiError.message(),
                detailsJson
        );
        log.error(line);
    }

    private String formatDetails(ApiError apiError) {
        try {
            return objectMapper.writeValueAsString(apiError.details());
        } catch (JsonProcessingException e) {
            return apiError.details().toString();
        }
    }
}
