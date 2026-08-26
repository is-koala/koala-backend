package com.exskylab.koala.core.exceptions;

import org.springframework.http.HttpStatus;

public class UserNotFoundException extends KoalaException {
    public UserNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
