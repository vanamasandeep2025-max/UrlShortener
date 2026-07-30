package com.urlshortener.controller;

import com.urlshortener.dto.request.CreateUrlRequest;
import com.urlshortener.dto.request.UpdateExpiryRequest;
import com.urlshortener.dto.request.VerifyUrlPasswordRequest;
import com.urlshortener.dto.response.AnalyticsResponse;
import com.urlshortener.dto.response.PageResponse;
import com.urlshortener.dto.response.UrlResponse;
import com.urlshortener.exception.InvalidUrlPasswordException;
import com.urlshortener.security.AuthenticatedUser;
import com.urlshortener.security.SecurityUtils;
import com.urlshortener.service.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "URLs", description = "Create, list, update, delete short URLs and view their analytics")
@RestController
@RequestMapping("/api/v1/urls")
@RequiredArgsConstructor
public class UrlController {

    private final UrlService urlService;

    @Operation(summary = "Shorten a URL")
    @PostMapping
    public ResponseEntity<UrlResponse> createUrl(@Valid @RequestBody CreateUrlRequest request) {
        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        UrlResponse response = urlService.createUrl(request, currentUser.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(summary = "List URLs (paginated, sortable, filterable, searchable)")
    @GetMapping
    public ResponseEntity<PageResponse<UrlResponse>> listUrls(
            @RequestParam(required = false) String search,
            @RequestParam(required = false, defaultValue = "ALL") String status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        PageResponse<UrlResponse> page = urlService.listUrls(currentUser.id(), currentUser.isAdmin(), search, status, pageable);
        return ResponseEntity.ok(page);
    }

    @Operation(summary = "Soft-delete a URL")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUrl(@PathVariable UUID id) {
        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        urlService.softDelete(id, currentUser.id(), currentUser.isAdmin());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Update (or clear) a URL's expiry date")
    @PatchMapping("/{id}")
    public ResponseEntity<UrlResponse> updateExpiry(@PathVariable UUID id, @Valid @RequestBody UpdateExpiryRequest request) {
        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        UrlResponse response = urlService.updateExpiry(id, request, currentUser.id(), currentUser.isAdmin());
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get click analytics for a short URL")
    @GetMapping("/{shortCode}/analytics")
    public ResponseEntity<AnalyticsResponse> getAnalytics(@PathVariable String shortCode) {
        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        return ResponseEntity.ok(urlService.getAnalytics(shortCode, currentUser.id(), currentUser.isAdmin()));
    }

    @Operation(summary = "Verify the password for a password-protected link and receive its destination")
    @PostMapping("/{shortCode}/verify-password")
    public ResponseEntity<Map<String, String>> verifyPassword(
            @PathVariable String shortCode, @Valid @RequestBody VerifyUrlPasswordRequest request) {
        String destination = urlService.verifyPasswordAndGetDestination(shortCode, request.getPassword())
            .orElseThrow(() -> new InvalidUrlPasswordException("Incorrect password"));
        return ResponseEntity.ok(Map.of("originalUrl", destination));
    }
}
