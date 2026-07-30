package com.urlshortener.util.shortcode;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Factory that resolves the active {@link ShortCodeGenerator} strategy bean by name.
 * Spring injects every ShortCodeGenerator bean here keyed by its bean name (@Component
 * value), so adding a new strategy is a one-class change with no factory edits required.
 */
@Component
public class ShortCodeGeneratorFactory {

    private final Map<String, ShortCodeGenerator> strategies;
    private final String defaultStrategy;

    public ShortCodeGeneratorFactory(Map<String, ShortCodeGenerator> strategies,
                                      @Value("${app.short-code.strategy:random}") String defaultStrategy) {
        this.strategies = strategies;
        this.defaultStrategy = defaultStrategy;
    }

    public ShortCodeGenerator getGenerator() {
        return getGenerator(defaultStrategy);
    }

    public ShortCodeGenerator getGenerator(String strategyName) {
        ShortCodeGenerator generator = strategies.get(strategyName);
        if (generator == null) {
            throw new IllegalArgumentException("Unknown short-code strategy: " + strategyName);
        }
        return generator;
    }
}
