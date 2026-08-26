package com.exskylab.koala.core.exceptions;

import org.springframework.http.HttpStatus;

public class UserVerificationNotFoundException extends KoalaException {
    public UserVerificationNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
