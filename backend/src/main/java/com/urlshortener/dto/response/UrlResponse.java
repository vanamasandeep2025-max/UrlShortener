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
public class UrlResponse {

    private UUID id;
    private String shortCode;
    private String shortUrl;
    private String originalUrl;
    private long clickCount;
    private boolean passwordProtected;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant expiresAt;
}
