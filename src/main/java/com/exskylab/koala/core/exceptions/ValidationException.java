package com.exskylab.koala.core.exceptions;

import org.springframework.http.HttpStatus;

public class ValidationException extends KoalaException {

    public ValidationException(String message) {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
