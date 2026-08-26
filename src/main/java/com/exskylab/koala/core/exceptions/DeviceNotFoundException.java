package com.exskylab.koala.core.exceptions;

import org.springframework.http.HttpStatus;

public class DeviceNotFoundException extends KoalaException {
    public DeviceNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
