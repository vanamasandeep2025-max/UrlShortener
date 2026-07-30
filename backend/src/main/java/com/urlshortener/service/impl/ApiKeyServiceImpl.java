package com.urlshortener.service.impl;

import com.urlshortener.audit.AuditService;
import com.urlshortener.dto.request.CreateApiKeyRequest;
import com.urlshortener.dto.response.ApiKeyResponse;
import com.urlshortener.entity.ActorType;
import com.urlshortener.entity.ApiKey;
import com.urlshortener.entity.User;
import com.urlshortener.exception.ResourceNotFoundException;
import com.urlshortener.mapper.ApiKeyMapper;
import com.urlshortener.repository.ApiKeyRepository;
import com.urlshortener.repository.UserRepository;
import com.urlshortener.security.apikey.ApiKeyHasher;
import com.urlshortener.service.ApiKeyService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApiKeyServiceImpl implements ApiKeyService {

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final ApiKeyHasher apiKeyHasher;
    private final ApiKeyMapper apiKeyMapper;
    private final AuditService auditService;

    @Override
    @Transactional
    public ApiKeyResponse create(UUID userId, CreateApiKeyRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        String plaintextKey = apiKeyHasher.generatePlaintextKey();
        ApiKey apiKey = ApiKey.builder()
            .user(user)
            .name(request.getName())
            .keyPrefix(apiKeyHasher.extractPrefix(plaintextKey))
            .keyHash(apiKeyHasher.hash(plaintextKey))
            .expiresAt(request.getExpiresAt())
            .build();
        apiKey = apiKeyRepository.save(apiKey);

        auditService.log(ActorType.USER, userId, "API_KEY_CREATED", "API_KEY", apiKey.getId().toString(),
            Map.of("name", apiKey.getName(), "prefix", apiKey.getKeyPrefix()), null);

        ApiKeyResponse response = apiKeyMapper.toResponse(apiKey);
        response.setPlaintextKey(plaintextKey);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ApiKeyResponse> list(UUID userId) {
        return apiKeyRepository.findByUserIdAndRevokedAtIsNullOrderByCreatedAtDesc(userId).stream()
            .map(apiKeyMapper::toResponse)
            .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void revoke(UUID userId, UUID apiKeyId) {
        ApiKey apiKey = apiKeyRepository.findById(apiKeyId)
            .orElseThrow(() -> new ResourceNotFoundException("API key not found: " + apiKeyId));
        if (!apiKey.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("You do not have access to this API key");
        }
        apiKey.setRevokedAt(Instant.now());
        apiKeyRepository.save(apiKey);

        auditService.log(ActorType.USER, userId, "API_KEY_REVOKED", "API_KEY", apiKey.getId().toString(), null, null);
    }
}
