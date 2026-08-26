package com.exskylab.koala.core.exceptions;

import org.springframework.http.HttpStatus;

public class DeviceDoesntMatchException extends KoalaException {
    public DeviceDoesntMatchException(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}
