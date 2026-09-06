package com.exskylab.koala.core.exceptions;

import org.springframework.http.HttpStatus;

public class ExternalServiceException extends KoalaException {

    public ExternalServiceException(String message) {
        super(message, HttpStatus.BAD_GATEWAY, false);
    }
}
