package com.urlshortener.dto.request;

import com.urlshortener.validation.NoScriptTag;
import com.urlshortener.validation.ValidHttpUrl;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateUrlRequest {

    @NotBlank(message = "url must not be blank")
    @ValidHttpUrl
    @NoScriptTag
    @Schema(example = "https://www.example.com/very/long/url", description = "The destination URL to shorten")
    private String url;

    @Schema(example = "2026-12-31T23:59:59Z", description = "Optional ISO-8601 expiry instant; omit for a non-expiring link")
    private Instant expiryDate;

    @Size(min = 4, max = 72, message = "password must be between 4 and 72 characters")
    @Schema(description = "Optional password to protect the link (secure-sharing feature); omit for a public link")
    private String password;
}
