package com.urlshortener.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restricts a URL field to well-formed http/https URLs. Rejecting other schemes
 * (javascript:, file:, data:, ftp:, ...) at the validation boundary closes off an
 * "unvalidated redirect" / scheme-smuggling avenue for a service whose entire job
 * is to redirect browsers to a stored URL.
 */
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ValidHttpUrlValidator.class)
public @interface ValidHttpUrl {

    String message() default "must be a well-formed http:// or https:// URL";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
