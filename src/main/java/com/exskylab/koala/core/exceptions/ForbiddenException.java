package com.exskylab.koala.core.exceptions;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends KoalaException {

    public ForbiddenException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
