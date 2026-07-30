package com.urlshortener.security.apikey;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.stereotype.Component;

/**
 * API keys are hashed (not encrypted) at rest, mirroring how passwords are handled:
 * the plaintext key is shown to the user exactly once at creation and is unrecoverable
 * afterward. SHA-256 (not BCrypt) is used here because API keys already carry >= 256
 * bits of entropy from SecureRandom, so a slow adaptive hash buys nothing but latency
 * on every authenticated request.
 */
@Component
public class ApiKeyHasher {

    private static final String PREFIX = "usk_";
    private static final int SECRET_BYTES = 32;
    private final SecureRandom secureRandom = new SecureRandom();

    public String generatePlaintextKey() {
        byte[] bytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(bytes);
        return PREFIX + HexFormat.of().formatHex(bytes);
    }

    public String extractPrefix(String plaintextKey) {
        int visibleChars = PREFIX.length() + 6;
        return plaintextKey.substring(0, Math.min(visibleChars, plaintextKey.length()));
    }

    public String hash(String plaintextKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(plaintextKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
