package com.exskylab.koala.core.exceptions;

import org.springframework.http.HttpStatus;

public class NotificationDispatchException extends KoalaException {
    public NotificationDispatchException(String message) {
        super(message, HttpStatus.BAD_GATEWAY);
    }
}
