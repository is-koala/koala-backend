package com.exskylab.koala.core.exceptions;

import org.springframework.http.HttpStatus;

public class TooManyRequestsException extends KoalaException {

    public TooManyRequestsException(String message) {
        super(message, HttpStatus.TOO_MANY_REQUESTS);
    }
}
