package com.urlshortener.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiKeyResponse {

    private UUID id;
    private String name;
    private String keyPrefix;

    /** Only populated once, in the response to the creation call - never persisted or shown again. */
    private String plaintextKey;

    private String scopes;
    private Instant lastUsedAt;
    private Instant expiresAt;
    private Instant createdAt;
}
