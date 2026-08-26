package com.exskylab.koala.core.exceptions;

import org.springframework.http.HttpStatus;

public class UserNotWorkerForAssignmentException extends KoalaException {
    public UserNotWorkerForAssignmentException(String message) {
        super(message, HttpStatus.FORBIDDEN);
    }
}
