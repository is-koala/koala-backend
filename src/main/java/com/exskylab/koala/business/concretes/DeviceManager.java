package com.exskylab.koala.business.concretes;

import java.util.Locale;
import com.exskylab.koala.business.abstracts.DeviceService;
import com.exskylab.koala.core.helpers.DeviceIdentity;
import com.exskylab.koala.dataAccess.DeviceDao;
import com.exskylab.koala.entities.Device;
import com.exskylab.koala.entities.DeviceType;
import com.exskylab.koala.entities.User;
import org.springframework.transaction.annotation.Transactional;
import nl.basjes.parse.useragent.UserAgent;
import nl.basjes.parse.useragent.UserAgentAnalyzer;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DeviceManager implements DeviceService {

    private final DeviceDao deviceDao;
    private final UserAgentAnalyzer userAgentAnalyzer;

    public DeviceManager(DeviceDao deviceDao) {
        this.deviceDao = deviceDao;

        this.userAgentAnalyzer = UserAgentAnalyzer
                .newBuilder()
                .withField(UserAgent.DEVICE_NAME)
                .withField(UserAgent.DEVICE_CLASS)
                .withField(UserAgent.OPERATING_SYSTEM_NAME_VERSION)
                .withField(UserAgent.AGENT_NAME_VERSION)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Device> getDevicesByUserIdAndPushTokenNotNull(UUID id) {
        return deviceDao.findAllByUser_IdAndPushTokenIsNotNull(id);
    }

    @Override
    @Transactional
    public Device findOrCreateDevice(User user, String ipAddress, String userAgent, String clientDeviceId) {
        String deviceId = DeviceIdentity.sanitize(clientDeviceId);

        if (deviceId != null) {
            Optional<Device> byClientId = deviceDao.findByUser_IdAndClientDeviceId(user.getId(), deviceId);
            if (byClientId.isPresent()) {
                Device device = byClientId.get();
                device.setLastSeenAt(Instant.now());
                device.setName(describe(userAgent));
                device.setType(typeOf(userAgent));
                return deviceDao.save(device);
            }
            return deviceDao.save(newDevice(user, userAgent, deviceId));
        }

        return findOrCreateByUserAgent(user, userAgent);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Device> findDevice(User user, String userAgent, String clientDeviceId) {
        String deviceId = DeviceIdentity.sanitize(clientDeviceId);

        if (deviceId != null) {
            return deviceDao.findByUser_IdAndClientDeviceId(user.getId(), deviceId);
        }

        return deviceDao.findByUserAndNameAndType(user, describe(userAgent), typeOf(userAgent));
    }

    private Device newDevice(User user, String userAgent, String clientDeviceId) {
        Device device = new Device();
        device.setUser(user);
        device.setClientDeviceId(clientDeviceId);
        device.setName(describe(userAgent));
        device.setType(typeOf(userAgent));
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
