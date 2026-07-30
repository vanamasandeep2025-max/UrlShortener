package com.urlshortener.util.shortcode;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Default strategy: a cryptographically random Base62 string. Collisions are possible
 * but astronomically unlikely at length >= 7 (62^7 ~= 3.5 trillion combinations); the
 * caller (UrlServiceImpl) retries with a fresh code on the rare unique-constraint hit.
 */
@Component("random")
public class RandomShortCodeGenerator implements ShortCodeGenerator {

    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generate(int length, String originalUrl, int attempt) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
