package com.urlshortener.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ValidHttpUrlValidatorTest {

    private final ValidHttpUrlValidator validator = new ValidHttpUrlValidator();

    @Test
    void allowsBlank() {
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid("", null)).isTrue();
    }

    @Test
    void acceptsHttpAndHttps() {
        assertThat(validator.isValid("http://example.com", null)).isTrue();
        assertThat(validator.isValid("https://example.com/a/b?c=1#frag", null)).isTrue();
    }

    @Test
    void rejectsNonHttpSchemes() {
        assertThat(validator.isValid("javascript:alert(1)", null)).isFalse();
        assertThat(validator.isValid("ftp://example.com/file", null)).isFalse();
        assertThat(validator.isValid("file:///etc/passwd", null)).isFalse();
        assertThat(validator.isValid("data:text/html,<script>1</script>", null)).isFalse();
    }

    @Test
    void rejectsMalformedUrl() {
        assertThat(validator.isValid("not a url", null)).isFalse();
    }

    @Test
    void rejectsOverlyLongUrl() {
        String longUrl = "https://example.com/" + "a".repeat(9000);
        assertThat(validator.isValid(longUrl, null)).isFalse();
    }
}
