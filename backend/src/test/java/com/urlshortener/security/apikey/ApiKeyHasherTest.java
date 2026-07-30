package com.urlshortener.security.apikey;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ApiKeyHasherTest {

    private final ApiKeyHasher apiKeyHasher = new ApiKeyHasher();

    @Test
    void generatesUniquePrefixedKeys() {
        String first = apiKeyHasher.generatePlaintextKey();
        String second = apiKeyHasher.generatePlaintextKey();

        assertThat(first).startsWith("usk_").isNotEqualTo(second);
    }

    @Test
    void hashIsDeterministicForSameInput() {
        String key = apiKeyHasher.generatePlaintextKey();

        assertThat(apiKeyHasher.hash(key)).isEqualTo(apiKeyHasher.hash(key));
    }

    @Test
    void differentKeysHashDifferently() {
        String first = apiKeyHasher.generatePlaintextKey();
        String second = apiKeyHasher.generatePlaintextKey();

        assertThat(apiKeyHasher.hash(first)).isNotEqualTo(apiKeyHasher.hash(second));
    }

    @Test
    void hashNeverContainsThePlaintextKey() {
        String key = apiKeyHasher.generatePlaintextKey();

        assertThat(apiKeyHasher.hash(key)).doesNotContain(key);
    }

    @Test
    void extractPrefixNeverExposesTheFullSecret() {
        String key = apiKeyHasher.generatePlaintextKey();

        String prefix = apiKeyHasher.extractPrefix(key);

        assertThat(prefix.length()).isLessThan(key.length());
        assertThat(key).startsWith(prefix);
    }
}
