package com.exskylab.koala.core.helpers;

import jakarta.servlet.http.HttpServletRequest;

public record ClientContext(String ipAddress, String userAgent, String deviceId) {

    public static ClientContext from(HttpServletRequest request) {
        return new ClientContext(
                ClientIpResolver.resolve(request),
                request.getHeader("User-Agent"),
                DeviceIdentity.fromRequest(request));
    }

}
