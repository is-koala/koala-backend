package com.exskylab.koala.core.exceptions;

import org.springframework.http.HttpStatus;

public class EmailSendException extends KoalaException {
    public EmailSendException(String message) {
        super(message, HttpStatus.BAD_GATEWAY);
    }
}
