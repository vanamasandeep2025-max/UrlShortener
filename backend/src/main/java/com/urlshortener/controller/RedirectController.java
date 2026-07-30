package com.urlshortener.controller;

import com.urlshortener.dto.response.UrlRedirectTarget;
import com.urlshortener.events.UrlClickedEvent;
import com.urlshortener.exception.UrlExpiredException;
import com.urlshortener.service.UrlService;
import com.urlshortener.util.ClientIpResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * The hot path: cache-first lookup, then an immediate 302 with click tracking pushed off
 * onto Kafka (via an in-process domain event) rather than written synchronously, so the
 * redirect response doesn't wait on Postgres. See docs/ARCHITECTURE.md for the full flow.
 */
@Slf4j
@Tag(name = "Redirect", description = "Public short-link redirection")
@RestController
@RequiredArgsConstructor
public class RedirectController {

    private final UrlService urlService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.base-url}")
    private String baseUrl;

    @Operation(summary = "Resolve a short code and redirect (302) to its destination")
    @GetMapping("/{shortCode}")
    public ResponseEntity<Void> redirect(@PathVariable String shortCode, HttpServletRequest request) {
        UrlRedirectTarget target = urlService.resolveForRedirect(shortCode);

        if (target.isExpired()) {
            log.info("Redirect blocked (expired): shortCode={} expiresAt={}", shortCode, target.expiresAt());
            throw new UrlExpiredException("This link expired on " + target.expiresAt());
        }

        if (target.isPasswordProtected()) {
            log.info("Redirect deferred to password gate: shortCode={}", shortCode);
            URI promptPage = URI.create(baseUrl + "/protected.html?code=" + shortCode);
            return ResponseEntity.status(HttpStatus.FOUND).location(promptPage).build();
        }

        publishClickEvent(target, request);
        log.info("Redirect served: shortCode={} urlId={}", shortCode, target.urlId());

        return ResponseEntity.status(HttpStatus.FOUND)
            .location(URI.create(target.originalUrl()))
            .build();
    }

    private void publishClickEvent(UrlRedirectTarget target, HttpServletRequest request) {
        UrlClickedEvent event = UrlClickedEvent.builder()
            .eventId(UUID.randomUUID())
            .urlId(target.urlId())
            .shortCode(target.shortCode())
            .ipAddress(ClientIpResolver.resolve(request))
            .userAgent(request.getHeader(HttpHeaders.USER_AGENT))
            .referrer(request.getHeader(HttpHeaders.REFERER))
            .occurredAt(Instant.now())
            .correlationId(MDC.get("requestId"))
            .build();
        eventPublisher.publishEvent(event);
    }
}
