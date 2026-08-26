package com.exskylab.koala.core.exceptions;

import org.springframework.http.HttpStatus;

public class TokenExpiredException extends KoalaException {
    public TokenExpiredException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}
