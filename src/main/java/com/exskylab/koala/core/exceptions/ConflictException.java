package com.exskylab.koala.core.exceptions;

import org.springframework.http.HttpStatus;

public class ConflictException extends KoalaException {

    public ConflictException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
