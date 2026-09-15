package com.exskylab.koala.core.helpers;

import jakarta.servlet.http.HttpServletRequest;

import com.exskylab.koala.core.constants.AuthMessages;
import com.exskylab.koala.core.exceptions.ValidationException;

public record ClientContext(String ipAddress, String userAgent, String deviceId, ClientPlatform platform,
                            DeviceHints deviceHints) {

    public ClientContext {
        deviceHints = deviceHints == null ? DeviceHints.NONE : deviceHints;
    }

    public ClientContext(String ipAddress, String userAgent, String deviceId) {
        this(ipAddress, userAgent, deviceId, ClientPlatform.UNKNOWN, DeviceHints.NONE);
    }

    public ClientContext(String ipAddress, String userAgent, String deviceId, ClientPlatform platform) {
        this(ipAddress, userAgent, deviceId, platform, DeviceHints.NONE);
    }

    public static ClientContext from(HttpServletRequest request, ClientIpResolver clientIpResolver) {
        return new ClientContext(
                clientIpResolver.resolve(request),
                request.getHeader("User-Agent"),
                DeviceIdentity.fromRequest(request),
                ClientPlatform.from(request.getHeader(ClientPlatform.HEADER)),
                DeviceHints.from(request));
    }

    public void requireWebDeviceId() {
        if (platform == ClientPlatform.WEB && deviceId == null) {
            throw new ValidationException(AuthMessages.WEB_DEVICE_ID_REQUIRED);
        }
    }
}
