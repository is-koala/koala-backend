package com.exskylab.koala.core.helpers;

import java.util.Locale;

public enum ClientPlatform {
    IOS,
    ANDROID,
    WEB,
    UNKNOWN;

    public static final String HEADER = "X-Client-Platform";

    public static ClientPlatform from(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return UNKNOWN;
        }
    }
}
