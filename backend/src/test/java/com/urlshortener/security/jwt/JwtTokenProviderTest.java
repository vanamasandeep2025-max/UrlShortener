package com.urlshortener.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.urlshortener.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

    private static final String SECRET = "test-secret-key-that-is-at-least-32-bytes-long!!";

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(SECRET, "url-shortener-platform", 3_600_000L, 86_400_000L);
    }

    @Test
    void accessTokenRoundTripsExpectedClaims() {
        UUID userId = UUID.randomUUID();
        String token = jwtTokenProvider.generateAccessToken(userId, "alice", "USER");

        Claims claims = jwtTokenProvider.parseClaims(token);

        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(jwtTokenProvider.getUserId(claims)).isEqualTo(userId);
        assertThat(jwtTokenProvider.getRole(claims)).isEqualTo("USER");
        assertThat(jwtTokenProvider.isAccessToken(claims)).isTrue();
        assertThat(jwtTokenProvider.isRefreshToken(claims)).isFalse();
    }

    @Test
    void refreshTokenIsDistinguishableFromAccessToken() {
        UUID userId = UUID.randomUUID();
        String refreshToken = jwtTokenProvider.generateRefreshToken(userId, "bob", "ADMIN");

        Claims claims = jwtTokenProvider.parseClaims(refreshToken);

        assertThat(jwtTokenProvider.isRefreshToken(claims)).isTrue();
        assertThat(jwtTokenProvider.isAccessToken(claims)).isFalse();
    }

    @Test
    void rejectsGarbageToken() {
        assertThatThrownBy(() -> jwtTokenProvider.parseClaims("not-a-valid-jwt"))
            .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        JwtTokenProvider otherProvider = new JwtTokenProvider(
            "a-completely-different-secret-key-of-32-bytes!!", "url-shortener-platform", 3_600_000L, 86_400_000L);
        String token = otherProvider.generateAccessToken(UUID.randomUUID(), "eve", "USER");

        assertThatThrownBy(() -> jwtTokenProvider.parseClaims(token))
            .isInstanceOf(InvalidTokenException.class);
    }
}
