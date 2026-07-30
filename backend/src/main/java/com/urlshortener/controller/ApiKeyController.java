package com.urlshortener.controller;

import com.urlshortener.dto.request.CreateApiKeyRequest;
import com.urlshortener.dto.response.ApiKeyResponse;
import com.urlshortener.security.AuthenticatedUser;
import com.urlshortener.security.SecurityUtils;
import com.urlshortener.service.ApiKeyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "API Keys", description = "Programmatic access credentials for the X-API-Key header")
@RestController
@RequestMapping("/api/v1/api-keys")
@RequiredArgsConstructor
public class ApiKeyController {

    private final ApiKeyService apiKeyService;

    @Operation(summary = "Create a new API key (the plaintext key is returned once and never again)")
    @PostMapping
    public ResponseEntity<ApiKeyResponse> create(@Valid @RequestBody CreateApiKeyRequest request) {
        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.status(HttpStatus.CREATED).body(apiKeyService.create(currentUser.id(), request));
    }

    @Operation(summary = "List this user's active API keys")
    @GetMapping
    public ResponseEntity<List<ApiKeyResponse>> list() {
        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(apiKeyService.list(currentUser.id()));
    }

    @Operation(summary = "Revoke an API key")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> revoke(@PathVariable UUID id) {
        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        apiKeyService.revoke(currentUser.id(), id);
        return ResponseEntity.noContent().build();
    }
}
