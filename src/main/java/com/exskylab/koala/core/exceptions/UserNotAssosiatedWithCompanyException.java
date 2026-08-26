package com.exskylab.koala.core.exceptions;

import org.springframework.http.HttpStatus;

public class UserNotAssosiatedWithCompanyException extends KoalaException {
    public UserNotAssosiatedWithCompanyException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
