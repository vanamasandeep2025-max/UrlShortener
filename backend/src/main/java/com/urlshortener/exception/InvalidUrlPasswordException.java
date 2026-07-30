package com.urlshortener.exception;

public class InvalidUrlPasswordException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidUrlPasswordException(String message) {
        super(message);
    }
}
