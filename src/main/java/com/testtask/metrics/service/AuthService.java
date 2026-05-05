package com.testtask.metrics.service;

import com.testtask.metrics.model.dto.AuthDtos;
import com.testtask.metrics.model.entity.ClientEntity;
import com.testtask.metrics.repository.ClientRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class AuthService {

    private final ClientRepository clientRepository;
    private final JwtService jwtService;

    public AuthService(ClientRepository clientRepository, JwtService jwtService) {
        this.clientRepository = clientRepository;
        this.jwtService = jwtService;
    }

    /**
     * Выдаёт JWT по {@code clientID}, если клиент существует и активен (см. ТЗ).
     *
     * @param request тело с полем {@code clientID}
     * @return обёртка с полем {@code jwt}
     */
    public AuthDtos.AuthResponse authenticate(AuthDtos.AuthRequest request) {
        ClientEntity client = clientRepository.findByClientId(request.clientID())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown client"));

        if (!client.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Client is disabled");
        }

        String token = jwtService.issueToken(client.getClientId());
        return new AuthDtos.AuthResponse(token);
    }

    /**
     * Регистрирует клиента с уникальным {@code clientId} в таблице {@code clients} (дальше токен — через POST /auth).
     *
     * @param request тело с полем {@code clientId}
     * @return подтверждение с {@code clientId} и статусом {@code created}
     */
    public AuthDtos.SignUpResponse signUp(AuthDtos.SignUpRequest request) {
        if (clientRepository.findByClientId(request.clientId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "clientId already exists");
        }

        ClientEntity client = new ClientEntity();
        client.setId(UUID.randomUUID());
        client.setClientId(request.clientId());
        client.setSecretHash("not-used");
        client.setEnabled(true);
        clientRepository.save(client);

        return new AuthDtos.SignUpResponse(client.getClientId(), "created");
    }
}
