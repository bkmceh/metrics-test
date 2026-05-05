package com.testtask.metrics.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.testtask.metrics.logging.ApiErrorLogger;
import com.testtask.metrics.model.dto.ApiError;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Пишет в ответ тело {@link ApiError} в JSON и логирует через {@link ApiErrorLogger}.
 */
@Component
public class ApiJsonErrorWriter {

    private final ObjectMapper objectMapper;
    private final ApiErrorLogger apiErrorLogger;

    public ApiJsonErrorWriter(ObjectMapper objectMapper, ApiErrorLogger apiErrorLogger) {
        this.objectMapper = objectMapper;
        this.apiErrorLogger = apiErrorLogger;
    }

    /**
     * @param response HTTP-ответ (буфер сбрасывается, если ещё не отправлен)
     * @param status   HTTP-код
     * @param code     машиночитаемый код ошибки
     * @param message  текст для клиента
     */
    public void write(HttpServletResponse response, int status, String code, String message) throws IOException {
        write(response, status, new ApiError(code, message, List.of()));
    }

    /**
     * Записывает JSON {@link ApiError} и логирует строку «Произошла ошибка…».
     */
    public void write(HttpServletResponse response, int status, ApiError apiError) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        apiErrorLogger.log(apiError, status);
        response.resetBuffer();
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), apiError);
        response.flushBuffer();
    }
}
