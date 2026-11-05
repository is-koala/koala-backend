package com.exskylab.koala.entities;

public enum ClientAuditAction {

    EXPORT("export");

    private final String code;

    ClientAuditAction(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
