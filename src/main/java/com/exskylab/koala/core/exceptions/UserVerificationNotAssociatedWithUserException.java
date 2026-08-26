package com.exskylab.koala.core.exceptions;

import org.springframework.http.HttpStatus;

public class UserVerificationNotAssociatedWithUserException extends KoalaException {
    public UserVerificationNotAssociatedWithUserException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
