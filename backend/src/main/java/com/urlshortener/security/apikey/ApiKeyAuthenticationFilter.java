package com.urlshortener.security.apikey;

import com.urlshortener.entity.ApiKey;
import com.urlshortener.repository.ApiKeyRepository;
import com.urlshortener.security.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates programmatic clients via the X-API-Key header, as an alternative to JWT.
 * Like the JWT filter, an absent/invalid key is not rejected here - it just leaves the
 * request anonymous, so endpoints without an auth requirement are unaffected.
 */
@Slf4j
@RequiredArgsConstructor
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER_NAME = "X-API-Key";
    private static final Duration LAST_USED_UPDATE_THRESHOLD = Duration.ofMinutes(1);

    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyHasher apiKeyHasher;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String rawKey = request.getHeader(HEADER_NAME);
        if (StringUtils.hasText(rawKey)) {
            authenticate(rawKey);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String rawKey) {
        String hash = apiKeyHasher.hash(rawKey);
        Optional<ApiKey> found = apiKeyRepository.findByKeyHashFetchUser(hash);
        if (found.isEmpty()) {
            log.debug("Rejected unknown API key");
            return;
        }
        ApiKey apiKey = found.get();
        if (!apiKey.isActive()) {
            log.debug("Rejected inactive API key: {}", apiKey.getKeyPrefix());
            return;
        }
        touchLastUsed(apiKey);

        AuthenticatedUser principal = new AuthenticatedUser(
            apiKey.getUser().getId(), apiKey.getUser().getUsername(), apiKey.getUser().getRole());
        var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + principal.role()));
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void touchLastUsed(ApiKey apiKey) {
        Instant now = Instant.now();
        if (apiKey.getLastUsedAt() == null || apiKey.getLastUsedAt().isBefore(now.minus(LAST_USED_UPDATE_THRESHOLD))) {
            apiKey.setLastUsedAt(now);
            apiKeyRepository.save(apiKey);
        }
    }
}
