package com.urlshortener.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Set;

public class ValidHttpUrlValidator implements ConstraintValidator<ValidHttpUrl, String> {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final int MAX_LENGTH = 8192;

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        if (value.length() > MAX_LENGTH) {
            return false;
        }
        try {
            URL url = new URL(value);
            if (!ALLOWED_SCHEMES.contains(url.getProtocol())) {
                return false;
            }
            return url.getHost() != null && !url.getHost().isBlank();
        } catch (MalformedURLException e) {
            return false;
        }
    }
}
