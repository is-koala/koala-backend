package com.exskylab.koala.core.exceptions;

import org.springframework.http.HttpStatus;

public class NotificationNotFoundException extends KoalaException {
    public NotificationNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
