package com.exskylab.koala.core.helpers;

import jakarta.servlet.http.HttpServletRequest;

import com.exskylab.koala.core.constants.AuthMessages;
import com.exskylab.koala.core.exceptions.ValidationException;

public record ClientContext(String ipAddress, String userAgent, String deviceId, ClientPlatform platform) {

    public ClientContext(String ipAddress, String userAgent, String deviceId) {
        this(ipAddress, userAgent, deviceId, ClientPlatform.UNKNOWN);
    }

    public static ClientContext from(HttpServletRequest request, ClientIpResolver clientIpResolver) {
        return new ClientContext(
                clientIpResolver.resolve(request),
                request.getHeader("User-Agent"),
                DeviceIdentity.fromRequest(request),
                ClientPlatform.from(request.getHeader(ClientPlatform.HEADER)));
    }

    public void requireWebDeviceId() {
        if (platform == ClientPlatform.WEB && deviceId == null) {
            throw new ValidationException(AuthMessages.WEB_DEVICE_ID_REQUIRED);
        }
    }
}
