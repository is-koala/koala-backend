package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.DeviceService;
import com.exskylab.koala.core.constants.DeviceMessages;
import com.exskylab.koala.core.exceptions.DeviceNotFoundException;
import com.exskylab.koala.dataAccess.DeviceDao;
import com.exskylab.koala.entities.Device;
import com.exskylab.koala.entities.DeviceType;
import com.exskylab.koala.entities.User;
import org.springframework.transaction.annotation.Transactional;
import nl.basjes.parse.useragent.UserAgent;
import nl.basjes.parse.useragent.UserAgentAnalyzer;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
    public Device findOrCreateDevice(User user, String ipAddress, String userAgent) {
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
            existingDevice.get().setLastSeenAt(LocalDateTime.now());
            return deviceDao.save(existingDevice.get());
        }else {
            Device newDevice = new Device();
            newDevice.setUser(user);
            newDevice.setName(finalDeviceName);
            newDevice.setType(deviceType);
            newDevice.setPushToken(null);
            newDevice.setFirstSeenAt(LocalDateTime.now());
            newDevice.setLastSeenAt(LocalDateTime.now());

            return deviceDao.save(newDevice);
        }

    }

    @Override
    @Transactional
    public Device findDeviceByUserAndUserAgent(User user, String userAgent) {
        if (userAgent == null || userAgent.isBlank()){
            userAgent = "unknown";
        }

        UserAgent agent = userAgentAnalyzer.parse(userAgent);
        String deviceName = agent.getValue(UserAgent.DEVICE_NAME);
        String operatingSystem = agent.getValue(UserAgent.OPERATING_SYSTEM_NAME_VERSION);
        DeviceType deviceType = toDeviceType(operatingSystem, agent.getValue(UserAgent.AGENT_NAME_VERSION));
        String finalDeviceName = String.format("%s (%s)", deviceName, operatingSystem);

        return deviceDao.findByUserAndNameAndType(user, finalDeviceName, deviceType)
                .orElseThrow(() -> new DeviceNotFoundException(DeviceMessages.DEVICE_NOT_FOUND_BY_USER_AND_USER_AGENT));

    }

    private DeviceType toDeviceType(String operatingSystem, String agentName) {

        if (operatingSystem == null){
            return DeviceType.UNKNOWN;
        }

        String osLower = operatingSystem.toLowerCase();

        if (osLower.contains("ios")){
            return DeviceType.IOS;
        }

        if (osLower.contains("android")){
            return DeviceType.ANDROID;
        }

        if (agentName != null && List.of("chrome", "firefox", "safari", "edge").stream().anyMatch(agentName.toLowerCase()::contains)) {
            return DeviceType.WEB_BROWSER;
        }


        if (osLower.contains("windows") || osLower.contains("mac os") || osLower.contains("linux")){
            return DeviceType.WEB_BROWSER;
        }


        return DeviceType.OTHER;

   }
}
