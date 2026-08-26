package com.exskylab.koala.core.exceptions;

import org.springframework.http.HttpStatus;

public class NotValidAuthorizationHeaderException extends KoalaException {
    public NotValidAuthorizationHeaderException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}
