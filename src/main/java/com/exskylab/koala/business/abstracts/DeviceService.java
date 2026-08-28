package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.entities.Device;
import com.exskylab.koala.entities.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceService {

    List<Device> getDevicesByUserIdAndPushTokenNotNull(UUID id);

    Device findOrCreateDevice(User user, String ipAddress, String userAgent, String clientDeviceId);

    Optional<Device> findDevice(User user, String userAgent, String clientDeviceId);

    void savePushToken(String userAgent, String clientDeviceId, String pushToken);

    void clearPushToken(String userAgent, String clientDeviceId);
}
