package com.urlshortener.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.urlshortener.cache.RateLimitResult;
import com.urlshortener.cache.RedisRateLimiter;
import com.urlshortener.dto.response.ErrorResponse;
import com.urlshortener.util.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Applies a distributed per-client rate limit ahead of the auth filters. Keys on the
 * API key header when present (so a key's limit follows it across IPs); otherwise on
 * client IP. The password-verification endpoint gets a much tighter limit, since it's
 * the brute-force target for the secure-sharing (password-protected link) feature.
 *
 * <p>defaultCapacity/defaultWindowSeconds are constructor-injected rather than {@code @Value}
 * fields: this filter is instantiated directly via {@code new} in SecurityConfig (not a
 * Spring-managed bean), so {@code @Value} field injection would silently never run, leaving
 * both at Java's default {@code int} value of 0 - which denies every single request (capacity
 * 0) and expires the Redis key instantly (TTL 0). Constructor injection avoids relying on a
 * container lifecycle this object never goes through.
 */
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();
    private static final String VERIFY_PASSWORD_PATTERN = "/api/v1/urls/*/verify-password";
    private static final int VERIFY_PASSWORD_CAPACITY = 5;
    private static final int VERIFY_PASSWORD_WINDOW_SECONDS = 60;

    private final RedisRateLimiter rateLimiter;
    private final ObjectMapper objectMapper;
    private final int defaultCapacity;
    private final int defaultWindowSeconds;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String clientKey = resolveClientKey(request);
        String path = request.getRequestURI();

        RateLimitResult result;
        if (PATH_MATCHER.match(VERIFY_PASSWORD_PATTERN, path)) {
            result = rateLimiter.tryConsume("ratelimit:verify:" + clientKey, VERIFY_PASSWORD_CAPACITY, VERIFY_PASSWORD_WINDOW_SECONDS);
        } else {
            result = rateLimiter.tryConsume("ratelimit:api:" + clientKey, defaultCapacity, defaultWindowSeconds);
        }

        if (!result.allowed()) {
            writeTooManyRequests(response, request, result.retryAfterSeconds());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String resolveClientKey(HttpServletRequest request) {
        String apiKey = request.getHeader("X-API-Key");
        if (StringUtils.hasText(apiKey)) {
            return "key:" + apiKey.substring(0, Math.min(apiKey.length(), 16));
        }
        return "ip:" + ClientIpResolver.resolve(request);
    }

    private void writeTooManyRequests(HttpServletResponse response, HttpServletRequest request, long retryAfterSeconds)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(Math.max(retryAfterSeconds, 1)));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = ErrorResponse.builder()
            .timestamp(Instant.now())
            .status(HttpStatus.TOO_MANY_REQUESTS.value())
            .error(HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase())
            .message("Rate limit exceeded. Try again later.")
            .path(request.getRequestURI())
            .correlationId(MDC.get("requestId"))
            .build();
        objectMapper.writeValue(response.getWriter(), body);
    }
}
