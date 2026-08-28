package com.exskylab.koala.core.exceptions;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends KoalaException {

    public UnauthorizedException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}
