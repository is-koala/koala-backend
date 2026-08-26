package com.exskylab.koala.core.exceptions;

import org.springframework.http.HttpStatus;

public class UserIsNotEmployerException extends KoalaException {
    public UserIsNotEmployerException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
