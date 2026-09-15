package com.exskylab.koala.core.helpers;

import jakarta.servlet.http.HttpServletRequest;

import com.exskylab.koala.core.constants.AuthMessages;
import com.exskylab.koala.core.exceptions.ValidationException;

public record ClientContext(String ipAddress, String userAgent, String deviceId, ClientPlatform platform,
                            DeviceHints deviceHints) {

    public ClientContext {
        platform = platform == null ? ClientPlatform.UNKNOWN : platform;
        deviceHints = deviceHints == null ? DeviceHints.NONE : deviceHints;
    }

    public static ClientContext from(HttpServletRequest request, ClientIpResolver clientIpResolver) {
        DeviceIdentityRequest identity = DeviceIdentityRequest.from(request);
        return new ClientContext(clientIpResolver.resolve(request), identity.userAgent(), identity.deviceId(),
                identity.platform(), identity.hints());
    }

    public DeviceIdentityRequest deviceIdentity() {
        return new DeviceIdentityRequest(userAgent, deviceId, platform, deviceHints);
    }

    public void requireWebDeviceId() {
        if (platform == ClientPlatform.WEB && deviceId == null) {
            throw new ValidationException(AuthMessages.WEB_DEVICE_ID_REQUIRED);
        }
    }
}
