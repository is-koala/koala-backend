package com.exskylab.koala.core.exceptions;

import java.util.Locale;
import org.springframework.http.HttpStatus;

public abstract class KoalaException extends RuntimeException {

    private final HttpStatus httpStatus;
    private final String errorCode;

    protected KoalaException(String message, HttpStatus httpStatus) {
        super(message);
        this.httpStatus = httpStatus;
        this.errorCode = deriveErrorCode(message, getClass());
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    static String deriveErrorCode(String message, Class<?> type) {
        if (message != null && message.startsWith("{") && message.endsWith("}")) {
            return message.substring(1, message.length() - 1)
                    .replace('.', '_')
                    .replace('-', '_')
                    .toUpperCase(Locale.ROOT);
        }

        return type.getSimpleName()
                .replaceAll("Exception$", "")
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .toUpperCase(Locale.ROOT);
    }
}
