package com.exskylab.koala.core.exceptions;

import org.springframework.http.HttpStatus;

public class InvitationAlreadyAnsweredException extends KoalaException {
    public InvitationAlreadyAnsweredException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
