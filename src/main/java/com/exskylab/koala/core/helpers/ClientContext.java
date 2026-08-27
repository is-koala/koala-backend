package com.exskylab.koala.core.helpers;

import jakarta.servlet.http.HttpServletRequest;

public record ClientContext(String ipAddress, String userAgent, String deviceId) {

    public static ClientContext from(HttpServletRequest request) {
        return new ClientContext(
                clientIp(request),
                request.getHeader("User-Agent"),
                DeviceIdentity.fromRequest(request));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
