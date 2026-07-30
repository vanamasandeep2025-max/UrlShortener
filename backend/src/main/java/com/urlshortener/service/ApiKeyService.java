package com.urlshortener.service;

import com.urlshortener.dto.request.CreateApiKeyRequest;
import com.urlshortener.dto.response.ApiKeyResponse;
import java.util.List;
import java.util.UUID;

public interface ApiKeyService {

    ApiKeyResponse create(UUID userId, CreateApiKeyRequest request);

    List<ApiKeyResponse> list(UUID userId);

    void revoke(UUID userId, UUID apiKeyId);
}
