package com.exskylab.koala.core.helpers;

import jakarta.servlet.http.HttpServletRequest;

public record DeviceIdentityRequest(String userAgent, String deviceId, ClientPlatform platform, DeviceHints hints) {

    public DeviceIdentityRequest {
        platform = platform == null ? ClientPlatform.UNKNOWN : platform;
        hints = hints == null ? DeviceHints.NONE : hints;
    }

    public static DeviceIdentityRequest from(HttpServletRequest request) {
        return new DeviceIdentityRequest(
                request.getHeader("User-Agent"),
                DeviceIdentity.fromRequest(request),
                ClientPlatform.from(request.getHeader(ClientPlatform.HEADER)),
                DeviceHints.from(request));
    }

    public static DeviceIdentityRequest legacy(String userAgent, String clientDeviceId) {
        return new DeviceIdentityRequest(userAgent, clientDeviceId, ClientPlatform.UNKNOWN, DeviceHints.NONE);
    }
}
