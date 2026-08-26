package com.exskylab.koala.core.utilities.results;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResult extends Result {

    private final String errorCode;
    private final List<FieldError> details;

    public ErrorResult(String message, HttpStatus httpStatus) {
        this(message, httpStatus, null, null);
    }

    public ErrorResult(String message, HttpStatus httpStatus, String errorCode) {
        this(message, httpStatus, errorCode, null);
    }

    public ErrorResult(String message, HttpStatus httpStatus, String errorCode, List<FieldError> details) {
        super(false, message, httpStatus);
        this.errorCode = errorCode;
        this.details = details;
    }

    public ErrorResult(HttpStatus httpStatus) {
        super(false, httpStatus);
        this.errorCode = null;
        this.details = null;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public List<FieldError> getDetails() {
        return details;
    }
}
