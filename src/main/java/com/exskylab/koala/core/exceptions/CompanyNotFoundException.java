package com.exskylab.koala.core.exceptions;

import org.springframework.http.HttpStatus;

public class CompanyNotFoundException extends KoalaException {
    public CompanyNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
