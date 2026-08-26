package com.exskylab.koala.core.exceptions;

import org.springframework.http.HttpStatus;

public class SessionNotFoundException extends KoalaException {
    public SessionNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
