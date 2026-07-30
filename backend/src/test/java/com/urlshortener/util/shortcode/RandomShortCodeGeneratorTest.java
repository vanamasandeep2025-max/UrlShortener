package com.urlshortener.util.shortcode;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RandomShortCodeGeneratorTest {

    private final RandomShortCodeGenerator generator = new RandomShortCodeGenerator();

    @Test
    void generatesCodeOfRequestedLength() {
        String code = generator.generate(7, "https://example.com", 0);
        assertThat(code).hasSize(7);
    }

    @Test
    void onlyUsesBase62Alphabet() {
        String code = generator.generate(12, "https://example.com", 0);
        assertThat(code).matches("[0-9A-Za-z]+");
    }

    @Test
    void generatesDistinctCodesAcrossManyCalls() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            codes.add(generator.generate(7, "https://example.com", 0));
        }
        // With 62^7 possible codes, 1000 draws colliding would indicate a broken generator, not bad luck.
        assertThat(codes).hasSizeGreaterThan(990);
    }
}
