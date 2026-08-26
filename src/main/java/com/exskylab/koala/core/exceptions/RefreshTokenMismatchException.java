package com.exskylab.koala.core.exceptions;

import org.springframework.http.HttpStatus;

public class RefreshTokenMismatchException extends KoalaException {
    public RefreshTokenMismatchException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}
