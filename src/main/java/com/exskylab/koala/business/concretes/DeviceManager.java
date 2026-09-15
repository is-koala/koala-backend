package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.DeviceService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.AuthMessages;
import com.exskylab.koala.core.constants.DeviceMessages;
import com.exskylab.koala.core.dtos.device.response.DeviceDto;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.helpers.ClientPlatform;
import com.exskylab.koala.core.helpers.DeviceIdentity;
import com.exskylab.koala.core.helpers.DeviceIdentityRequest;
import com.exskylab.koala.core.helpers.DeviceNaming;
import com.exskylab.koala.core.mappers.DeviceMapper;
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
    private final DeviceMapper deviceMapper;

    public DeviceManager(DeviceDao deviceDao, SecurityService securityService,
                         PushTokenMutationLock pushTokenDeliveryLock, DeviceMapper deviceMapper) {
        this.deviceDao = deviceDao;
        this.pushTokenDeliveryLock = pushTokenDeliveryLock;
        this.deviceMapper = deviceMapper;

        this.userAgentAnalyzer = UserAgentAnalyzer
                .newBuilder()
                .withField(UserAgent.DEVICE_NAME)
                .withField(UserAgent.DEVICE_CLASS)
                .withField(UserAgent.OPERATING_SYSTEM_NAME_VERSION)
                .withField(UserAgent.OPERATING_SYSTEM_NAME_VERSION_MAJOR)
                .withField(UserAgent.AGENT_NAME_VERSION)
                .withField(UserAgent.AGENT_NAME_VERSION_MAJOR)
                .build();
        this.securityService = securityService;
    }

    record Identity(String name, DeviceType type) {
    }

    @Override
    @Transactional(readOnly = true)
    public List<Device> getDevicesByUserIdAndPushTokenNotNull(UUID id) {
        return deviceDao.findAllByUser_IdAndPushTokenIsNotNull(id);
    }

    @Override
    @Transactional
    public Device findOrCreateDevice(User user, DeviceIdentityRequest request) {
        pushTokenDeliveryLock.lockUser(user.getId());
        requireActiveUser(user.getId());
        String deviceId = DeviceIdentity.sanitize(request.deviceId());

        if (request.platform() == ClientPlatform.WEB && deviceId == null) {
            throw new ValidationException(AuthMessages.WEB_DEVICE_ID_REQUIRED);
        }

        Identity identity = identify(request);

        if (deviceId != null) {
            Optional<Device> byClientId = deviceDao.findByUser_IdAndClientDeviceId(user.getId(), deviceId);
            if (byClientId.isPresent()) {
                Device device = byClientId.get();
                pushTokenDeliveryLock.lockTokens(device.getPushToken() == null
                        ? List.of() : List.of(device.getPushToken()));
                device.setLastSeenAt(Instant.now());
                device.setName(identity.name());
                device.setType(identity.type());
                return deviceDao.save(device);
            }
            return deviceDao.save(newDevice(user, identity, deviceId));
        }

        return findOrCreateByIdentity(user, identity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Device> findDevice(User user, DeviceIdentityRequest request) {
        String deviceId = DeviceIdentity.sanitize(request.deviceId());

        if (request.platform() == ClientPlatform.WEB && deviceId == null) {
            return Optional.empty();
        }

        if (deviceId != null) {
            return deviceDao.findByUser_IdAndClientDeviceId(user.getId(), deviceId);
        }

        Identity identity = identify(request);
        return deviceDao.findByUserAndNameAndType(user, identity.name(), identity.type());
    }

    @Override
    @Transactional
    public DeviceDto renameMyDevice(UUID deviceId, String name) {
        UUID userId = securityService.getAuthenticatedUserId();
        Device device = deviceDao.findByIdAndUser_Id(deviceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(DeviceMessages.DEVICE_NOT_FOUND));
        device.setCustomName(name.strip());
        Device saved = deviceDao.save(device);
        log.info("Device {} renamed by its owner {}", deviceId, userId);
        return deviceMapper.toDeviceDto(saved);
    }

    @Override
    @Transactional
    public void savePushToken(String userAgent, String clientDeviceId, String pushToken) {
        User user = securityService.getAuthenticatedUser();
        pushTokenDeliveryLock.lockMutations();
        pushTokenDeliveryLock.lockUser(user.getId());
        requireActiveUser(user.getId());

        Device device = findDevice(user, DeviceIdentityRequest.legacy(userAgent, clientDeviceId))
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

        findDevice(user, DeviceIdentityRequest.legacy(userAgent, clientDeviceId)).ifPresent(device -> {
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

    private Device newDevice(User user, Identity identity, String clientDeviceId) {
        Device device = new Device();
        device.setUser(user);
        device.setClientDeviceId(clientDeviceId);
        device.setName(identity.name());
        device.setType(identity.type());
        device.setPushToken(null);
        device.setFirstSeenAt(Instant.now());
        device.setLastSeenAt(Instant.now());
        return device;
    }

    Identity identify(DeviceIdentityRequest request) {
        UserAgent agent = userAgentAnalyzer.parse(normalize(request.userAgent()));
        String operatingSystem = agent.getValue(UserAgent.OPERATING_SYSTEM_NAME_VERSION);
        String browser = agent.getValue(UserAgent.AGENT_NAME_VERSION);
        DeviceType type = typeOf(request.platform(), operatingSystem, browser);
        DeviceNaming.ParsedAgent parsed = new DeviceNaming.ParsedAgent(
                agent.getValue(UserAgent.DEVICE_NAME),
                operatingSystem,
                agent.getValue(UserAgent.AGENT_NAME_VERSION_MAJOR),
                agent.getValue(UserAgent.OPERATING_SYSTEM_NAME_VERSION_MAJOR));
        return new Identity(DeviceNaming.name(request.hints(), parsed, type), type);
    }

    private String normalize(String userAgent) {
        return userAgent == null || userAgent.isBlank() ? "unknown" : userAgent;
    }

    private Device findOrCreateByIdentity(User user, Identity identity) {
        Optional<Device> existingDevice = deviceDao.findByUserAndNameAndType(user, identity.name(), identity.type());

        if (existingDevice.isPresent()) {
            pushTokenDeliveryLock.lockTokens(existingDevice.get().getPushToken() == null
                    ? List.of() : List.of(existingDevice.get().getPushToken()));
            existingDevice.get().setLastSeenAt(Instant.now());
            return deviceDao.save(existingDevice.get());
        }

        return deviceDao.save(newDevice(user, identity, null));
    }

    private DeviceType typeOf(ClientPlatform platform, String operatingSystem, String agentName) {
        return platform == ClientPlatform.WEB ? DeviceType.WEB_BROWSER : toDeviceType(operatingSystem, agentName);
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
