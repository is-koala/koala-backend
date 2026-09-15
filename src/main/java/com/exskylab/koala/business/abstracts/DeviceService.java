package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.device.response.DeviceDto;
import com.exskylab.koala.entities.Device;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.core.helpers.ClientPlatform;
import com.exskylab.koala.core.helpers.DeviceHints;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceService {

    List<Device> getDevicesByUserIdAndPushTokenNotNull(UUID id);

    Device findOrCreateDevice(User user, String ipAddress, String userAgent, String clientDeviceId,
                              ClientPlatform platform, DeviceHints hints);

    Optional<Device> findDevice(User user, String userAgent, String clientDeviceId, ClientPlatform platform,
                                DeviceHints hints);

    default Device findOrCreateDevice(User user, String ipAddress, String userAgent, String clientDeviceId,
                                      ClientPlatform platform) {
        return findOrCreateDevice(user, ipAddress, userAgent, clientDeviceId, platform, DeviceHints.NONE);
    }

    default Device findOrCreateDevice(User user, String ipAddress, String userAgent, String clientDeviceId) {
        return findOrCreateDevice(user, ipAddress, userAgent, clientDeviceId, ClientPlatform.UNKNOWN,
                DeviceHints.NONE);
    }

    default Optional<Device> findDevice(User user, String userAgent, String clientDeviceId,
                                        ClientPlatform platform) {
        return findDevice(user, userAgent, clientDeviceId, platform, DeviceHints.NONE);
    }

    default Optional<Device> findDevice(User user, String userAgent, String clientDeviceId) {
        return findDevice(user, userAgent, clientDeviceId, ClientPlatform.UNKNOWN, DeviceHints.NONE);
    }

    void savePushToken(String userAgent, String clientDeviceId, String pushToken);

    void clearPushToken(String userAgent, String clientDeviceId);

    Optional<String> currentPushToken(UUID userId, UUID deviceId);

    boolean clearPushTokenIfMatches(UUID userId, UUID deviceId, String expectedToken);

    DeviceDto renameMyDevice(UUID deviceId, String name);
}
