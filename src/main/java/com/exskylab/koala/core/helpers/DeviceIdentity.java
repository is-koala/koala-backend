package com.exskylab.koala.core.helpers;

import jakarta.servlet.http.HttpServletRequest;

import java.util.regex.Pattern;

public final class DeviceIdentity {

    public static final String HEADER = "X-Device-Id";

    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._:-]{8,128}");

    private DeviceIdentity() {
    }

    public static String fromRequest(HttpServletRequest request) {
        return sanitize(request == null ? null : request.getHeader(HEADER));
    }

    public static String sanitize(String candidate) {
        if (candidate == null) {
            return null;
        }
        String trimmed = candidate.trim();
        return SAFE_ID.matcher(trimmed).matches() ? trimmed : null;
    }
}
