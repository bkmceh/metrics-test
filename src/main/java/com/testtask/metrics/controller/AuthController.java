package com.testtask.metrics.controller;

import com.testtask.metrics.model.dto.AuthDtos;
import com.testtask.metrics.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /auth: вход по {@code clientId}, ответ с JWT (ТЗ).
     *
     * @param request JSON с полем {@code clientId}
     * @return HTTP 200 и {@link AuthDtos.AuthResponse}
     */
    @PostMapping("/auth")
    public ResponseEntity<AuthDtos.AuthResponse> auth(@Valid @RequestBody AuthDtos.AuthRequest request) {
        return ResponseEntity.ok(authService.authenticate(request));
    }

    /**
     * POST /sign-up: регистрация клиента по {@code clientId} (сохранение в БД для последующего POST /auth).
     *
     * @param request JSON с полем {@code clientId}
     * @return HTTP 201 и {@link AuthDtos.SignUpResponse}
     */
    @PostMapping("/sign-up")
    public ResponseEntity<AuthDtos.SignUpResponse> signUp(@Valid @RequestBody AuthDtos.SignUpRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signUp(request));
    }
}
