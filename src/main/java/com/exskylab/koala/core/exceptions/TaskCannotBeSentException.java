package com.exskylab.koala.core.exceptions;

import org.springframework.http.HttpStatus;

public class TaskCannotBeSentException extends KoalaException {
    public TaskCannotBeSentException(String message) {
        super(message, HttpStatus.BAD_GATEWAY);
    }
}
