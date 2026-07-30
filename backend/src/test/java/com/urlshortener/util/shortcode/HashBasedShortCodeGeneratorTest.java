package com.urlshortener.util.shortcode;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HashBasedShortCodeGeneratorTest {

    private final HashBasedShortCodeGenerator generator = new HashBasedShortCodeGenerator();

    @Test
    void generatesCodeOfRequestedLength() {
        String code = generator.generate(8, "https://example.com/a/b/c", 0);
        assertThat(code).hasSize(8);
    }

    @Test
    void differentAttemptsProduceDifferentCodes() {
        String first = generator.generate(10, "https://example.com", 0);
        String second = generator.generate(10, "https://example.com", 1);
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void onlyUsesBase62Alphabet() {
        String code = generator.generate(10, "https://example.com/path?query=1", 3);
        assertThat(code).matches("[0-9A-Za-z]+");
    }
}
