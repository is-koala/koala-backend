package com.exskylab.koala.core.utilities.identity;

public class PassiveAuthenticationException extends RuntimeException {

    private final String messageKey;

    public PassiveAuthenticationException(String messageKey, String detail) {
        super(detail);
        this.messageKey = messageKey;
    }

    public String getMessageKey() {
        return messageKey;
    }
}
