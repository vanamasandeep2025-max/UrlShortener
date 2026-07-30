package com.urlshortener.dto.request;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * expiresAt == null means "clear the expiry" (link becomes non-expiring);
 * the field is intentionally not validated as @NotNull for that reason.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateExpiryRequest {

    private Instant expiresAt;
}
