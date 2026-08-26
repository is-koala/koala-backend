package com.exskylab.koala.core.exceptions;

import org.springframework.http.HttpStatus;

public class SessionDoesntMatch extends KoalaException {
    public SessionDoesntMatch(String message) {
        super(message, HttpStatus.UNAUTHORIZED);
    }
}
