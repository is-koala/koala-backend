package com.exskylab.koala.core.exceptions;

import org.springframework.http.HttpStatus;

public class SmsSendException extends KoalaException {
    public SmsSendException(String message) {
        super(message, HttpStatus.BAD_GATEWAY);
    }
}
