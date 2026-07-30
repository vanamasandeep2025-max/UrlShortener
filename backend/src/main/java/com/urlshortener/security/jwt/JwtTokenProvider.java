package com.urlshortener.security.jwt;

import com.urlshortener.exception.InvalidTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_ROLE = "role";
    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final String issuer;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    public JwtTokenProvider(@Value("${app.jwt.secret}") String secret,
                             @Value("${app.jwt.issuer}") String issuer,
                             @Value("${app.jwt.expiration-ms}") long accessExpirationMs,
                             @Value("${app.jwt.refresh-expiration-ms}") long refreshExpirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public String generateAccessToken(UUID userId, String username, String role) {
        return buildToken(userId, username, role, TYPE_ACCESS, accessExpirationMs);
    }

    public String generateRefreshToken(UUID userId, String username, String role) {
        return buildToken(userId, username, role, TYPE_REFRESH, refreshExpirationMs);
    }

    public long getAccessExpirationMs() {
        return accessExpirationMs;
    }

    private String buildToken(UUID userId, String username, String role, String tokenType, long expirationMs) {
        Instant now = Instant.now();
        return Jwts.builder()
            .issuer(issuer)
            .subject(username)
            .claim(CLAIM_USER_ID, userId.toString())
            .claim(CLAIM_ROLE, role)
            .claim(CLAIM_TYPE, tokenType)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plusMillis(expirationMs)))
            .signWith(key)
            .compact();
    }

    public Claims parseClaims(String token) {
        try {
            return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("Invalid or expired token");
        }
    }

    public UUID getUserId(Claims claims) {
        return UUID.fromString(claims.get(CLAIM_USER_ID, String.class));
    }

    public String getRole(Claims claims) {
        return claims.get(CLAIM_ROLE, String.class);
    }

    public boolean isAccessToken(Claims claims) {
        return TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class));
    }

    public boolean isRefreshToken(Claims claims) {
        return TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class));
    }
}
