package com.adaptiveRating.exception;

public class AppCoreException extends RuntimeException {
    public AppCoreException(String message) {
        super(message);
    }

    public AppCoreException(String message, Throwable cause) {
        super(message, cause);
    }
}