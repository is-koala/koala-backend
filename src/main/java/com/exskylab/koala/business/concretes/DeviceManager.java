package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.DeviceService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.AuthMessages;
import com.exskylab.koala.core.constants.DeviceMessages;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.helpers.ClientPlatform;
import com.exskylab.koala.core.helpers.DeviceIdentity;
import com.exskylab.koala.core.privacy.PushTokenMutationLock;
import com.exskylab.koala.dataAccess.DeviceDao;
import com.exskylab.koala.entities.Device;
import com.exskylab.koala.entities.DeviceType;
import com.exskylab.koala.entities.User;
import lombok.extern.slf4j.Slf4j;
import nl.basjes.parse.useragent.UserAgent;
import nl.basjes.parse.useragent.UserAgentAnalyzer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@Slf4j
public class DeviceManager implements DeviceService {

    private final DeviceDao deviceDao;
    private final UserAgentAnalyzer userAgentAnalyzer;
    private final SecurityService securityService;
    private final PushTokenMutationLock pushTokenDeliveryLock;

    public DeviceManager(DeviceDao deviceDao, SecurityService securityService,
                         PushTokenMutationLock pushTokenDeliveryLock) {
        this.deviceDao = deviceDao;
        this.pushTokenDeliveryLock = pushTokenDeliveryLock;

        this.userAgentAnalyzer = UserAgentAnalyzer
                .newBuilder()
                .withField(UserAgent.DEVICE_NAME)
                .withField(UserAgent.DEVICE_CLASS)
                .withField(UserAgent.OPERATING_SYSTEM_NAME_VERSION)
                .withField(UserAgent.AGENT_NAME_VERSION)
                .build();
        this.securityService = securityService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Device> getDevicesByUserIdAndPushTokenNotNull(UUID id) {
        return deviceDao.findAllByUser_IdAndPushTokenIsNotNull(id);
    }

    @Override
    @Transactional
    public Device findOrCreateDevice(User user, String ipAddress, String userAgent, String clientDeviceId,
                                     ClientPlatform platform) {
        pushTokenDeliveryLock.lockUser(user.getId());
        requireActiveUser(user.getId());
        String deviceId = DeviceIdentity.sanitize(clientDeviceId);

        if (platform == ClientPlatform.WEB && deviceId == null) {
            throw new ValidationException(AuthMessages.WEB_DEVICE_ID_REQUIRED);
        }

        if (deviceId != null) {
            Optional<Device> byClientId = deviceDao.findByUser_IdAndClientDeviceId(user.getId(), deviceId);
            if (byClientId.isPresent()) {
                Device device = byClientId.get();
                pushTokenDeliveryLock.lockTokens(device.getPushToken() == null
                        ? List.of() : List.of(device.getPushToken()));
                device.setLastSeenAt(Instant.now());
                device.setName(describe(userAgent));
                device.setType(platform == ClientPlatform.WEB ? DeviceType.WEB_BROWSER : typeOf(userAgent));
                return deviceDao.save(device);
            }
            return deviceDao.save(newDevice(user, userAgent, deviceId, platform));
        }

        return findOrCreateByUserAgent(user, userAgent);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Device> findDevice(User user, String userAgent, String clientDeviceId,
                                       ClientPlatform platform) {
        String deviceId = DeviceIdentity.sanitize(clientDeviceId);

        if (platform == ClientPlatform.WEB && deviceId == null) {
            return Optional.empty();
        }

        if (deviceId != null) {
            return deviceDao.findByUser_IdAndClientDeviceId(user.getId(), deviceId);
        }

        return deviceDao.findByUserAndNameAndType(user, describe(userAgent), typeOf(userAgent));
    }

    @Override
    @Transactional
    public void savePushToken(String userAgent, String clientDeviceId, String pushToken) {
        User user = securityService.getAuthenticatedUser();
        pushTokenDeliveryLock.lockMutations();
        pushTokenDeliveryLock.lockUser(user.getId());
        requireActiveUser(user.getId());

        Device device = findDevice(user, userAgent, clientDeviceId)
                .orElseThrow(() -> new ResourceNotFoundException(DeviceMessages.DEVICE_NOT_FOUND));

        pushTokenDeliveryLock.lockTokens(Stream.of(device.getPushToken(), pushToken)
                .filter(Objects::nonNull).toList());
        detachTokenFromOtherDevices(pushToken, device.getId());
        deviceDao.flush();

        device.setPushToken(pushToken);
        deviceDao.saveAndFlush(device);

        log.info("Push token registered for device {} of user {}", device.getId(), user.getId());
    }

    @Override
    @Transactional
    public void clearPushToken(String userAgent, String clientDeviceId) {
        User user = securityService.getAuthenticatedUser();
        pushTokenDeliveryLock.lockMutations();
        pushTokenDeliveryLock.lockUser(user.getId());

        findDevice(user, userAgent, clientDeviceId).ifPresent(device -> {
            pushTokenDeliveryLock.lockTokens(device.getPushToken() == null
                    ? List.of() : List.of(device.getPushToken()));
            device.setPushToken(null);
            deviceDao.saveAndFlush(device);
            log.info("Push token cleared for device {} of user {}", device.getId(), user.getId());
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> currentPushToken(UUID userId, UUID deviceId) {
        return deviceDao.findByIdAndUser_IdAndPushTokenIsNotNull(deviceId, userId)
                .map(Device::getPushToken);
    }

    @Override
    @Transactional
    public boolean clearPushTokenIfMatches(UUID userId, UUID deviceId, String expectedToken) {
        pushTokenDeliveryLock.lockMutations();
        pushTokenDeliveryLock.lockUser(userId);
        pushTokenDeliveryLock.lockTokens(List.of(expectedToken));
        return deviceDao.clearPushTokenIfMatches(deviceId, expectedToken) == 1;
    }

    private void detachTokenFromOtherDevices(String pushToken, UUID keptDeviceId) {
        for (Device other : deviceDao.findAllByPushToken(pushToken)) {
            if (other.getId().equals(keptDeviceId)) {
                continue;
            }

            other.setPushToken(null);
            deviceDao.save(other);
            log.info("Push token moved away from device {}, it now belongs to device {}",
                    other.getId(), keptDeviceId);
        }
    }

    private void requireActiveUser(UUID userId) {
        if (!securityService.isActiveUser(userId)) {
            throw new ResourceNotFoundException(DeviceMessages.DEVICE_NOT_FOUND);
        }
    }

    private Device newDevice(User user, String userAgent, String clientDeviceId, ClientPlatform platform) {
        Device device = new Device();
        device.setUser(user);
        device.setClientDeviceId(clientDeviceId);
        device.setName(describe(userAgent));
        device.setType(platform == ClientPlatform.WEB ? DeviceType.WEB_BROWSER : typeOf(userAgent));
        device.setPushToken(null);
        device.setFirstSeenAt(Instant.now());
        device.setLastSeenAt(Instant.now());
        return device;
    }

    private String describe(String userAgent) {
        UserAgent agent = userAgentAnalyzer.parse(normalize(userAgent));
        return String.format("%s (%s)", agent.getValue(UserAgent.DEVICE_NAME),
                agent.getValue(UserAgent.OPERATING_SYSTEM_NAME_VERSION));
    }

    private DeviceType typeOf(String userAgent) {
        UserAgent agent = userAgentAnalyzer.parse(normalize(userAgent));
        return toDeviceType(agent.getValue(UserAgent.OPERATING_SYSTEM_NAME_VERSION),
                agent.getValue(UserAgent.AGENT_NAME_VERSION));
    }

    private String normalize(String userAgent) {
        return userAgent == null || userAgent.isBlank() ? "unknown" : userAgent;
    }

    private Device findOrCreateByUserAgent(User user, String userAgent) {
        if (userAgent == null || userAgent.isBlank()){
            userAgent = "unknown";
        }
        UserAgent agent = userAgentAnalyzer.parse(userAgent);

        String deviceName = agent.getValue(UserAgent.DEVICE_NAME);

        String operatingSystem = agent.getValue(UserAgent.OPERATING_SYSTEM_NAME_VERSION);

        String browser = agent.getValue(UserAgent.AGENT_NAME_VERSION);

        DeviceType deviceType = toDeviceType(operatingSystem, browser);

        String finalDeviceName = String.format("%s (%s)", deviceName, operatingSystem);


        Optional<Device> existingDevice = deviceDao.findByUserAndNameAndType(user, finalDeviceName, deviceType);


        if (existingDevice.isPresent()){
            pushTokenDeliveryLock.lockTokens(existingDevice.get().getPushToken() == null
                    ? List.of() : List.of(existingDevice.get().getPushToken()));
            existingDevice.get().setLastSeenAt(Instant.now());
            return deviceDao.save(existingDevice.get());
        }else {
            Device newDevice = new Device();
            newDevice.setUser(user);
            newDevice.setName(finalDeviceName);
            newDevice.setType(deviceType);
            newDevice.setPushToken(null);
            newDevice.setFirstSeenAt(Instant.now());
            newDevice.setLastSeenAt(Instant.now());

            return deviceDao.save(newDevice);
        }

    }


    private DeviceType toDeviceType(String operatingSystem, String agentName) {

        if (operatingSystem == null){
            return DeviceType.UNKNOWN;
        }

        String osLower = operatingSystem.toLowerCase(Locale.ROOT);

        if (osLower.contains("ios")){
            return DeviceType.IOS;
        }

        if (osLower.contains("android")){
            return DeviceType.ANDROID;
        }

        if (agentName != null && List.of("chrome", "firefox", "safari", "edge").stream().anyMatch(agentName.toLowerCase(Locale.ROOT)::contains)) {
            return DeviceType.WEB_BROWSER;
        }


        if (osLower.contains("windows") || osLower.contains("mac os") || osLower.contains("linux")){
            return DeviceType.WEB_BROWSER;
        }


        return DeviceType.OTHER;

   }
}
