package com.exskylab.koala.core.exceptions;

import org.springframework.http.HttpStatus;

public class AssignmentAlreadyAnsweredException extends KoalaException {
    public AssignmentAlreadyAnsweredException(String message) {
        super(message, HttpStatus.CONFLICT);
    }
}
