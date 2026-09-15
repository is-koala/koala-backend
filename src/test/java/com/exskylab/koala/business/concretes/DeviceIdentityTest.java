package com.exskylab.koala.business.concretes;

import com.exskylab.koala.core.helpers.DeviceHints;
import com.exskylab.koala.core.helpers.DeviceIdentity;
import com.exskylab.koala.core.helpers.DeviceNaming;
import com.exskylab.koala.core.helpers.ClientPlatform;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.dtos.device.response.DeviceDto;
import com.exskylab.koala.entities.DeviceType;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.dataAccess.DeviceDao;
import com.exskylab.koala.entities.Device;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.core.mappers.DeviceMapperImpl;
import org.junit.jupiter.api.BeforeEach;
import com.exskylab.koala.core.helpers.DeviceIdentityRequest;
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
    private SecurityService securityService;
    private User user;

    @BeforeEach
    void setUp() {
        deviceDao = mock(DeviceDao.class);
        securityService = mock(SecurityService.class);
        deviceManager = new DeviceManager(deviceDao, securityService, mock(PushTokenDeliveryLock.class), new DeviceMapperImpl());
        user = new User();
        user.setId(UUID.randomUUID());
        when(securityService.isActiveUser(user.getId())).thenReturn(true);

        when(deviceDao.save(any())).thenAnswer(i -> {
            Device d = i.getArgument(0);
            if (d.getId() == null) {
                d.setId(UUID.randomUUID());
            }
            return d;
        });
    }

    @Test
    void deletedAccountCannotCreateANewDeviceFromAStaleUserObject() {
        when(securityService.isActiveUser(user.getId())).thenReturn(false);

        assertThrows(com.exskylab.koala.core.exceptions.ResourceNotFoundException.class,
                () -> deviceManager.findOrCreateDevice(user, identity(DART_UA, PHONE_A)));

        verify(deviceDao, never()).save(any());
        verify(securityService).isActiveUser(user.getId());
    }

    @Test
    void twoPhonesWithTheSameUserAgentAreTwoDifferentDevices() {
        when(deviceDao.findByUser_IdAndClientDeviceId(user.getId(), PHONE_A)).thenReturn(Optional.empty());
        when(deviceDao.findByUser_IdAndClientDeviceId(user.getId(), PHONE_B)).thenReturn(Optional.empty());

        Device first = deviceManager.findOrCreateDevice(user, identity(DART_UA, PHONE_A));
        Device second = deviceManager.findOrCreateDevice(user, identity(DART_UA, PHONE_B));

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

        Device afterUpgrade = deviceManager.findOrCreateDevice(user, identity("Dart/9.9 (dart:io)", PHONE_A));

        assertEquals(existing.getId(), afterUpgrade.getId(),
                "Istemci sürümü degisince ayni cihaz kalmaliydi.");
    }

    @Test
    void aRequestWithoutTheHeaderStillFallsBackToUserAgentMatching() {
        when(deviceDao.findByUserAndNameAndType(any(), any(), any())).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> deviceManager.findOrCreateDevice(user,
                identity("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)", null)));

        verify(deviceDao, never()).findByUser_IdAndClientDeviceId(any(), any());
    }

    @Test
    void anAndroidClientWithoutADeviceIdRetainsTheLegacyUserAgentFallback() {
        when(deviceDao.findByUserAndNameAndType(any(), any(), any())).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> deviceManager.findOrCreateDevice(user, identity(DART_UA, null, ClientPlatform.ANDROID)));

        verify(deviceDao, never()).findByUser_IdAndClientDeviceId(any(), any());
    }

    @Test
    void lookupPrefersTheHeaderOverTheUserAgent() {
        Device existing = new Device();
        existing.setClientDeviceId(PHONE_A);
        when(deviceDao.findByUser_IdAndClientDeviceId(user.getId(), PHONE_A)).thenReturn(Optional.of(existing));

        assertTrue(deviceManager.findDevice(user, identity(DART_UA, PHONE_A)).isPresent());
        verify(deviceDao, never()).findByUserAndNameAndType(any(), any(), any());
    }

    @Test
    void aMissingDeviceIsReportedAsEmptyNotAnException() {
        when(deviceDao.findByUser_IdAndClientDeviceId(user.getId(), PHONE_A)).thenReturn(Optional.empty());

        assertTrue(deviceManager.findDevice(user, identity(DART_UA, PHONE_A)).isEmpty());
    }

    @Test
    void malformedDeviceIdsAreIgnoredSoTheyCannotPoisonTheLookup() {
        assertNull(DeviceIdentity.sanitize("kisa"));
        assertNull(DeviceIdentity.sanitize("bosluk iceren id"));
        assertNull(DeviceIdentity.sanitize("id\nwith\nnewline"));
        assertNull(DeviceIdentity.sanitize(null));
        assertEquals(PHONE_A, DeviceIdentity.sanitize("  " + PHONE_A + "  "));
    }

    @Test
    void webDevicesAreMatchedByClientIdAndAlwaysClassifiedAsBrowsers() {
        when(deviceDao.findByUser_IdAndClientDeviceId(user.getId(), PHONE_A)).thenReturn(Optional.empty());

        Device browser = deviceManager.findOrCreateDevice(user, identity("opaque-bff-label", PHONE_A, ClientPlatform.WEB));

        assertEquals(com.exskylab.koala.entities.DeviceType.WEB_BROWSER, browser.getType());
        assertEquals(PHONE_A, browser.getClientDeviceId());
    }

    @Test
    void twoWebProfileIdsCreateIndependentBrowserDevices() {
        when(deviceDao.findByUser_IdAndClientDeviceId(user.getId(), PHONE_A)).thenReturn(Optional.empty());
        when(deviceDao.findByUser_IdAndClientDeviceId(user.getId(), PHONE_B)).thenReturn(Optional.empty());

        Device first = deviceManager.findOrCreateDevice(user, identity("Chrome", PHONE_A, ClientPlatform.WEB));
        Device second = deviceManager.findOrCreateDevice(user, identity("Safari", PHONE_B, ClientPlatform.WEB));

        assertNotEquals(first.getId(), second.getId());
        assertEquals(com.exskylab.koala.entities.DeviceType.WEB_BROWSER, first.getType());
        assertEquals(com.exskylab.koala.entities.DeviceType.WEB_BROWSER, second.getType());
    }

    @Test
    void aWebLoginCannotFallBackToTheUserAgent() {
        assertThrows(ValidationException.class, () -> deviceManager.findOrCreateDevice(user,
                identity("Mozilla/5.0", null, ClientPlatform.WEB)));

        verify(deviceDao, never()).findByUserAndNameAndType(any(), any(), any());
    }

    @Test
    void aWebSessionLookupWithAnInvalidDeviceIdNeverFallsBack() {
        assertTrue(deviceManager.findDevice(user, identity("Mozilla/5.0", "invalid", ClientPlatform.WEB)).isEmpty());

        verify(deviceDao, never()).findByUserAndNameAndType(any(), any(), any());
    }

    @Test
    void theMobileUserAgentWithoutHeadersNeverProducesQuestionMarks() {
        when(deviceDao.findByUser_IdAndClientDeviceId(user.getId(), PHONE_A)).thenReturn(Optional.empty());

        Device phone = deviceManager.findOrCreateDevice(user, identity("Koala (iOS)", PHONE_A, ClientPlatform.IOS));

        assertFalse(phone.getName().contains("??"), phone.getName());
        assertFalse(phone.getName().isBlank());
        assertEquals(DeviceType.IOS, phone.getType());
    }

    @Test
    void theDeviceHeadersNameThePhoneAfterItsMarketingNameOsAndAppVersion() {
        when(deviceDao.findByUser_IdAndClientDeviceId(user.getId(), PHONE_A)).thenReturn(Optional.empty());

        Device phone = deviceManager.findOrCreateDevice(user,
                new DeviceIdentityRequest("Koala (iOS)", PHONE_A, ClientPlatform.IOS, DeviceHints.of("iPhone15,3", "iOS 18.1", "1.1.0")));

        assertEquals("iPhone 14 Pro Max · iOS 18.1 · Koala 1.1.0", phone.getName());
        assertEquals(DeviceType.IOS, phone.getType());
    }

    @Test
    void aBrowserWithoutHeadersIsNamedAfterAgentOsAndWeb() {
        when(deviceDao.findByUser_IdAndClientDeviceId(user.getId(), PHONE_A)).thenReturn(Optional.empty());

        Device browser = deviceManager.findOrCreateDevice(user,
                identity("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/128.0.0.0 Safari/537.36", PHONE_A, ClientPlatform.WEB));

        assertTrue(browser.getName().startsWith("Chrome 128"), browser.getName());
        assertTrue(browser.getName().endsWith(DeviceNaming.SEPARATOR + DeviceNaming.WEB_SUFFIX), browser.getName());
        assertFalse(browser.getName().contains("??"), browser.getName());
    }

    @Test
    void aBlankUserAgentWithoutHeadersIsTheUnknownDevice() {
        when(deviceDao.findByUserAndNameAndType(any(), any(), any())).thenReturn(Optional.empty());

        Device nameless = deviceManager.findOrCreateDevice(user, identity("", null));

        assertEquals(DeviceNaming.UNKNOWN_DEVICE, nameless.getName());
    }

    @Test
    void theUserAgentFallbackLooksTheDeviceUpByTheSameNameItWouldStore() {
        when(deviceDao.findByUserAndNameAndType(any(), any(), any())).thenReturn(Optional.empty());
        DeviceHints hints = DeviceHints.of("Pixel 8", "Android 15", "1.1.0");

        Device created = deviceManager.findOrCreateDevice(user,
                new DeviceIdentityRequest(DART_UA, null, ClientPlatform.ANDROID, hints));
        when(deviceDao.findByUserAndNameAndType(user, created.getName(), created.getType()))
                .thenReturn(Optional.of(created));

        assertTrue(deviceManager.findDevice(user,
                new DeviceIdentityRequest(DART_UA, null, ClientPlatform.ANDROID, hints)).isPresent());
    }

    @Test
    void renamingKeepsTheGeneratedNameAndOnlyTheOwnerCanDoIt() {
        Device mine = new Device();
        mine.setId(UUID.randomUUID());
        mine.setUser(user);
        mine.setName("iPhone 14 Pro Max · iOS 18.1 · Koala 1.1.0");
        mine.setType(DeviceType.IOS);
        when(securityService.getAuthenticatedUserId()).thenReturn(user.getId());
        when(deviceDao.findByIdAndUser_Id(mine.getId(), user.getId())).thenReturn(Optional.of(mine));

        DeviceDto renamed = deviceManager.renameMyDevice(mine.getId(), "  Evdeki iPad ");

        assertEquals("Evdeki iPad", renamed.name());
        assertEquals("Evdeki iPad", renamed.customName());
        assertEquals("iPhone 14 Pro Max · iOS 18.1 · Koala 1.1.0", mine.getName());
        assertThrows(ResourceNotFoundException.class,
                () -> deviceManager.renameMyDevice(UUID.randomUUID(), "Baskasinin"));
    }

    @Test
    void aLaterLoginRefreshesTheGeneratedNameButKeepsTheCustomOne() {
        Device existing = new Device();
        existing.setId(UUID.randomUUID());
        existing.setUser(user);
        existing.setClientDeviceId(PHONE_A);
        existing.setName("iPhone 14 Pro Max · iOS 18.1 · Koala 1.1.0");
        existing.setCustomName("Evdeki iPad");
        when(deviceDao.findByUser_IdAndClientDeviceId(user.getId(), PHONE_A)).thenReturn(Optional.of(existing));

        Device after = deviceManager.findOrCreateDevice(user,
                new DeviceIdentityRequest("Koala (iOS)", PHONE_A, ClientPlatform.IOS, DeviceHints.of("iPhone15,3", "iOS 18.2", "1.2.0")));

        assertEquals("iPhone 14 Pro Max · iOS 18.2 · Koala 1.2.0", after.getName());
        assertEquals("Evdeki iPad", after.getCustomName());
        assertEquals("Evdeki iPad", after.displayName());
    }

    private static DeviceIdentityRequest identity(String userAgent, String deviceId) {
        return DeviceIdentityRequest.legacy(userAgent, deviceId);
    }

    private static DeviceIdentityRequest identity(String userAgent, String deviceId, ClientPlatform platform) {
        return new DeviceIdentityRequest(userAgent, deviceId, platform, DeviceHints.NONE);
    }
}
