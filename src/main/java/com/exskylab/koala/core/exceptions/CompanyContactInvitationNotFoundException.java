package com.exskylab.koala.core.exceptions;

import org.springframework.http.HttpStatus;

public class CompanyContactInvitationNotFoundException extends KoalaException {
    public CompanyContactInvitationNotFoundException(String message) {
        super(message, HttpStatus.NOT_FOUND);
    }
}
