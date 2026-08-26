package com.exskylab.koala.core.exceptions;

import org.springframework.http.HttpStatus;

public class EmailOrPasswordMismatchException extends KoalaException {
    public EmailOrPasswordMismatchException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}
