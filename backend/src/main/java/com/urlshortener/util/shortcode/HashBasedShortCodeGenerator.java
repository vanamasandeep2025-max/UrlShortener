package com.urlshortener.util.shortcode;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.stereotype.Component;

/**
 * Alternative strategy: derives the code from a SHA-256 digest of the destination URL
 * salted with the retry attempt and the current time, then Base62-encodes it. Unlike
 * {@link RandomShortCodeGenerator} this ties the code to the URL's content rather than
 * pure entropy - useful where reproducibility/traceability of code generation matters
 * more than uniform randomness. Selected via app.short-code.strategy=hash.
 */
@Component("hash")
public class HashBasedShortCodeGenerator implements ShortCodeGenerator {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = ALPHABET.length();

    @Override
    public String generate(int length, String originalUrl, int attempt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String salted = originalUrl + "|" + attempt + "|" + System.nanoTime();
            byte[] hash = digest.digest(salted.getBytes(StandardCharsets.UTF_8));
            return toBase62(hash, length);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }

    private String toBase62(byte[] bytes, int length) {
        BigInteger number = new BigInteger(1, bytes);
        StringBuilder sb = new StringBuilder();
        BigInteger base = BigInteger.valueOf(BASE);
        while (number.signum() > 0 && sb.length() < length) {
            BigInteger[] divRem = number.divideAndRemainder(base);
            sb.append(ALPHABET.charAt(divRem[1].intValue()));
            number = divRem[0];
        }
        while (sb.length() < length) {
            sb.append(ALPHABET.charAt(0));
        }
        return sb.toString();
    }
}
