package com.testtask.metrics.controller;

import com.testtask.metrics.logging.ApiErrorLogger;
import com.testtask.metrics.model.dto.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ApiErrorLogger apiErrorLogger;

    public GlobalExceptionHandler(ApiErrorLogger apiErrorLogger) {
        this.apiErrorLogger = apiErrorLogger;
    }

    /**
     * Обрабатывает ошибки Bean Validation (@Valid): возвращает 400 и список полей.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
        List<ApiError.ApiErrorDetail> details = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toDetail)
                .toList();
        ApiError body = new ApiError("VALIDATION_ERROR", "Request validation failed", details);
        apiErrorLogger.log(body, HttpStatus.BAD_REQUEST.value());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Обрабатывает {@link IllegalArgumentException} как 400 с текстом причины.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> badRequest(IllegalArgumentException ex) {
        ApiError body = new ApiError("BAD_REQUEST", ex.getMessage(), List.of());
        apiErrorLogger.log(body, HttpStatus.BAD_REQUEST.value());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Некорректный JSON тела запроса (например обрезанный ввод) → 400.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> notReadable(HttpMessageNotReadableException ex) {
        ApiError body = new ApiError("BAD_REQUEST", "Malformed JSON request body", List.of());
        apiErrorLogger.log(body, HttpStatus.BAD_REQUEST.value());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Spring Security: доступ запрещён → 403.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> forbidden(AccessDeniedException ex) {
        ApiError body = new ApiError("FORBIDDEN", ex.getMessage(), List.of());
        apiErrorLogger.log(body, HttpStatus.FORBIDDEN.value());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    /**
     * Явные HTTP-статусы из {@link ResponseStatusException} (например 401/409 от сервисов).
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> responseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.getStatusCode().value());
        ApiError body = new ApiError(status.name(), ex.getReason() == null ? status.getReasonPhrase() : ex.getReason(), List.of());
        apiErrorLogger.log(body, status.value());
        return ResponseEntity.status(status).body(body);
    }

    /**
     * Прочие исключения → 500 без утечки деталей в сообщении (URI для диагностики).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> fallback(Exception ex, HttpServletRequest request) {
        log.error("Произошла ошибка (необработанное исключение) на {}: {}", request.getRequestURI(), ex.toString(), ex);
        ApiError body = new ApiError("INTERNAL_ERROR", "Unexpected error on " + request.getRequestURI(), List.of());
        apiErrorLogger.log(body, HttpStatus.INTERNAL_SERVER_ERROR.value());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private ApiError.ApiErrorDetail toDetail(FieldError fieldError) {
        return new ApiError.ApiErrorDetail(fieldError.getField(), fieldError.getDefaultMessage());
    }
}
