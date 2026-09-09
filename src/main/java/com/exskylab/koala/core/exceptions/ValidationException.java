package com.exskylab.koala.core.exceptions;

import org.springframework.http.HttpStatus;

public class ValidationException extends KoalaException {

    public ValidationException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }

    private ValidationException(String message, HttpStatus status) {
        super(message, status);
    }

    public static ValidationException requestBodyTooLarge(String message) {
        return new ValidationException(message, HttpStatus.PAYLOAD_TOO_LARGE);
    }
}
