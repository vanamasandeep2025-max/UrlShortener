package com.urlshortener.service;

import com.urlshortener.dto.request.CreateUrlRequest;
import com.urlshortener.dto.request.UpdateExpiryRequest;
import com.urlshortener.dto.response.AnalyticsResponse;
import com.urlshortener.dto.response.PageResponse;
import com.urlshortener.dto.response.UrlRedirectTarget;
import com.urlshortener.dto.response.UrlResponse;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;

public interface UrlService {

    UrlResponse createUrl(CreateUrlRequest request, UUID currentUserId);

    /**
     * Cache-first lookup for the redirect path. Throws ResourceNotFoundException if no
     * live (non-deleted) URL exists for the code. Expiry is deliberately NOT checked here:
     * the result may come straight from cache, so expiry must be re-evaluated by the caller
     * via {@link UrlRedirectTarget#isExpired()} on every call, cached or not.
     */
    UrlRedirectTarget resolveForRedirect(String shortCode);

    PageResponse<UrlResponse> listUrls(UUID currentUserId, boolean isAdmin, String search, String status, Pageable pageable);

    void softDelete(UUID id, UUID currentUserId, boolean isAdmin);

    UrlResponse updateExpiry(UUID id, UpdateExpiryRequest request, UUID currentUserId, boolean isAdmin);

    /** Returns the destination URL if the password matches; empty if it doesn't. */
    Optional<String> verifyPasswordAndGetDestination(String shortCode, String rawPassword);

    AnalyticsResponse getAnalytics(String shortCode, UUID currentUserId, boolean isAdmin);
}
