package com.urlshortener.util.shortcode;

/**
 * Strategy interface for short-code generation. Concrete strategies are registered as
 * named Spring beans and selected at runtime by {@link ShortCodeGeneratorFactory}
 * via the {@code app.short-code.strategy} property.
 */
public interface ShortCodeGenerator {

    /**
     * @param length      desired code length
     * @param originalUrl the URL being shortened (available so deterministic strategies can derive from it)
     * @param attempt     0 for the first attempt, incremented by the caller on collision retries
     */
    String generate(int length, String originalUrl, int attempt);
}
