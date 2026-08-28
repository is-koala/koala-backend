package com.exskylab.koala.business.concretes;

import com.exskylab.koala.core.helpers.DeviceIdentity;
import com.exskylab.koala.dataAccess.DeviceDao;
import com.exskylab.koala.entities.Device;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeviceIdentityTest {

    private static final String PHONE_A = "3f2b9c11-aaaa-4d55-9e77-000000000001";
    private static final String PHONE_B = "3f2b9c11-bbbb-4d55-9e77-000000000002";
    private static final String DART_UA = "Dart/3.5 (dart:io)";

    private DeviceDao deviceDao;
    private DeviceManager deviceManager;
    private User user;

    @BeforeEach
    void setUp() {
        deviceDao = mock(DeviceDao.class);
        deviceManager = new DeviceManager(deviceDao, mock(com.exskylab.koala.business.abstracts.SecurityService.class));
        user = new User();
        user.setId(UUID.randomUUID());

        when(deviceDao.save(any())).thenAnswer(i -> {
            Device d = i.getArgument(0);
            if (d.getId() == null) {
                d.setId(UUID.randomUUID());
            }
            return d;
        });
    }

    @Test
    void twoPhonesWithTheSameUserAgentAreTwoDifferentDevices() {
        when(deviceDao.findByUser_IdAndClientDeviceId(user.getId(), PHONE_A)).thenReturn(Optional.empty());
        when(deviceDao.findByUser_IdAndClientDeviceId(user.getId(), PHONE_B)).thenReturn(Optional.empty());

        Device first = deviceManager.findOrCreateDevice(user, "1.1.1.1", DART_UA, PHONE_A);
        Device second = deviceManager.findOrCreateDevice(user, "1.1.1.1", DART_UA, PHONE_B);

        assertNotEquals(first.getClientDeviceId(), second.getClientDeviceId());
        verify(deviceDao, times(2)).save(any());
    }

    @Test
    void aDartVersionBumpDoesNotCreateANewDevice() {
        Device existing = new Device();
        existing.setId(UUID.randomUUID());
        existing.setUser(user);
        existing.setClientDeviceId(PHONE_A);
        when(deviceDao.findByUser_IdAndClientDeviceId(user.getId(), PHONE_A)).thenReturn(Optional.of(existing));

        Device afterUpgrade = deviceManager.findOrCreateDevice(user, "1.1.1.1", "Dart/9.9 (dart:io)", PHONE_A);

        assertEquals(existing.getId(), afterUpgrade.getId(),
                "Istemci sürümü degisince ayni cihaz kalmaliydi.");
    }

    @Test
    void aRequestWithoutTheHeaderStillFallsBackToUserAgentMatching() {
        when(deviceDao.findByUserAndNameAndType(any(), any(), any())).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> deviceManager.findOrCreateDevice(user, "1.1.1.1",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)", null));

        verify(deviceDao, never()).findByUser_IdAndClientDeviceId(any(), any());
    }

    @Test
    void lookupPrefersTheHeaderOverTheUserAgent() {
        Device existing = new Device();
        existing.setClientDeviceId(PHONE_A);
        when(deviceDao.findByUser_IdAndClientDeviceId(user.getId(), PHONE_A)).thenReturn(Optional.of(existing));

        assertTrue(deviceManager.findDevice(user, DART_UA, PHONE_A).isPresent());
        verify(deviceDao, never()).findByUserAndNameAndType(any(), any(), any());
    }

    @Test
    void aMissingDeviceIsReportedAsEmptyNotAnException() {
        when(deviceDao.findByUser_IdAndClientDeviceId(user.getId(), PHONE_A)).thenReturn(Optional.empty());

        assertTrue(deviceManager.findDevice(user, DART_UA, PHONE_A).isEmpty());
    }

    @Test
    void malformedDeviceIdsAreIgnoredSoTheyCannotPoisonTheLookup() {
        assertNull(DeviceIdentity.sanitize("kisa"));
        assertNull(DeviceIdentity.sanitize("bosluk iceren id"));
        assertNull(DeviceIdentity.sanitize("id\nwith\nnewline"));
        assertNull(DeviceIdentity.sanitize(null));
        assertEquals(PHONE_A, DeviceIdentity.sanitize("  " + PHONE_A + "  "));
    }
}
