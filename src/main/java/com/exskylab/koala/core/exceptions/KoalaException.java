package com.exskylab.koala.core.exceptions;

import org.springframework.http.HttpStatus;

public class KoalaException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String errorCode;

    public KoalaException(String message) {
        this(message, HttpStatus.BAD_REQUEST);
    }

    public KoalaException(String message, HttpStatus httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = deriveErrorCode(getClass());
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    private static String deriveErrorCode(Class<?> type) {
        String name = type.getSimpleName().replaceAll("Exception$", "");
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toUpperCase();
    }
}
