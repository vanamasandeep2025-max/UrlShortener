package com.urlshortener.exception;

public class UrlExpiredException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public UrlExpiredException(String message) {
        super(message);
    }
}
