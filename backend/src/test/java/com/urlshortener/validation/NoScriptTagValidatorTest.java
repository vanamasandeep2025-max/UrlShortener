package com.urlshortener.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NoScriptTagValidatorTest {

    private final NoScriptTagValidator validator = new NoScriptTagValidator();

    @Test
    void allowsBlankAndNormalValues() {
        assertThat(validator.isValid(null, null)).isTrue();
        assertThat(validator.isValid("", null)).isTrue();
        assertThat(validator.isValid("https://example.com/path?q=1", null)).isTrue();
    }

    @Test
    void rejectsScriptTag() {
        assertThat(validator.isValid("<script>alert(1)</script>", null)).isFalse();
    }

    @Test
    void rejectsJavascriptUri() {
        assertThat(validator.isValid("javascript:alert(1)", null)).isFalse();
    }

    @Test
    void rejectsInlineEventHandler() {
        assertThat(validator.isValid("<img src=x onerror=alert(1)>", null)).isFalse();
    }

    @Test
    void rejectsIframeTag() {
        assertThat(validator.isValid("<iframe src='evil'></iframe>", null)).isFalse();
    }
}
