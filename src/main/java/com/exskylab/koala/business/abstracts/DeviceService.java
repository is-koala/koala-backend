package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.entities.Device;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.core.helpers.ClientPlatform;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceService {

    List<Device> getDevicesByUserIdAndPushTokenNotNull(UUID id);

    Device findOrCreateDevice(User user, String ipAddress, String userAgent, String clientDeviceId,
                              ClientPlatform platform);

    Optional<Device> findDevice(User user, String userAgent, String clientDeviceId, ClientPlatform platform);

    default Device findOrCreateDevice(User user, String ipAddress, String userAgent, String clientDeviceId) {
        return findOrCreateDevice(user, ipAddress, userAgent, clientDeviceId, ClientPlatform.UNKNOWN);
    }

    default Optional<Device> findDevice(User user, String userAgent, String clientDeviceId) {
        return findDevice(user, userAgent, clientDeviceId, ClientPlatform.UNKNOWN);
    }

    void savePushToken(String userAgent, String clientDeviceId, String pushToken);

    void clearPushToken(String userAgent, String clientDeviceId);
}
