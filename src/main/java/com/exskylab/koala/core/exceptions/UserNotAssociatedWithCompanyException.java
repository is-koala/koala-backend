package com.exskylab.koala.core.exceptions;

import org.springframework.http.HttpStatus;

public class UserNotAssociatedWithCompanyException extends KoalaException {
    public UserNotAssociatedWithCompanyException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
