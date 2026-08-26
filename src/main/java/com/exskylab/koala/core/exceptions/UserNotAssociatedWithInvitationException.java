package com.exskylab.koala.core.exceptions;

import org.springframework.http.HttpStatus;

public class UserNotAssociatedWithInvitationException extends KoalaException {
    public UserNotAssociatedWithInvitationException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
