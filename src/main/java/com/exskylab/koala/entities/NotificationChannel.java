package com.exskylab.koala.entities;

public enum NotificationChannel {

    EMAIL(true),
    SMS(true),
    PUSH(true),
    IN_APP(true);

    private final boolean preferable;

    NotificationChannel(boolean preferable) {
        this.preferable = preferable;
    }

    public boolean isPreferable() {
        return preferable;
    }
}
