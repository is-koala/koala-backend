package com.exskylab.koala.core.exceptions;

import org.springframework.http.HttpStatus;

public class UserNotAssosiatedWithInvitationException extends KoalaException {
    public UserNotAssosiatedWithInvitationException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
