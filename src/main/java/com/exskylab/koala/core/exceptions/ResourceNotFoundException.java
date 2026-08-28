package com.exskylab.koala.core.exceptions;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends KoalaException {

    public ResourceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
