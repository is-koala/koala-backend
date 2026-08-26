package com.exskylab.koala.core.exceptions;

import org.springframework.http.HttpStatus;

public class UserAlreadyExistsException extends KoalaException {
    public UserAlreadyExistsException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
