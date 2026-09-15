package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.device.response.DeviceDto;
import com.exskylab.koala.core.helpers.DeviceIdentityRequest;
import com.exskylab.koala.entities.Device;
import com.exskylab.koala.entities.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceService {

    List<Device> getDevicesByUserIdAndPushTokenNotNull(UUID id);

    Device findOrCreateDevice(User user, DeviceIdentityRequest identity);

    Optional<Device> findDevice(User user, DeviceIdentityRequest identity);

    void savePushToken(String userAgent, String clientDeviceId, String pushToken);

    void clearPushToken(String userAgent, String clientDeviceId);

    Optional<String> currentPushToken(UUID userId, UUID deviceId);

    boolean clearPushTokenIfMatches(UUID userId, UUID deviceId, String expectedToken);

    DeviceDto renameMyDevice(UUID deviceId, String name);
}
