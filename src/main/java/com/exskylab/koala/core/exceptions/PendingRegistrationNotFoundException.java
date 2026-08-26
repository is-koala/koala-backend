package com.exskylab.koala.core.exceptions;

import org.springframework.http.HttpStatus;

public class PendingRegistrationNotFoundException extends KoalaException {
    public PendingRegistrationNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
