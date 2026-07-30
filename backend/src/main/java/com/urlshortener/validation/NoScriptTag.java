package com.urlshortener.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defense-in-depth server-side guard against obvious XSS payloads in free-text input
 * (script tags, javascript: URIs, inline event handlers). This does not replace proper
 * output encoding on the frontend - it exists so malicious input never reaches storage.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NoScriptTagValidator.class)
public @interface NoScriptTag {

    String message() default "must not contain script tags or executable content";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
