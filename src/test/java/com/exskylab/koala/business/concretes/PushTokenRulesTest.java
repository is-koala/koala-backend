package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.dataAccess.DeviceDao;
import com.exskylab.koala.entities.Device;
import com.exskylab.koala.entities.User;
import org.mockito.InOrder;
import com.exskylab.koala.core.mappers.DeviceMapperImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PushTokenRulesTest {

    private static final String TOKEN = "fcm-token-abc";
    private static final String DEVICE_ID = "device-of-owner";
    private static final String USER_AGENT = "Dart/3.9 (dart:io)";

    private DeviceDao deviceDao;
    private SecurityService securityService;
    private PushTokenDeliveryLock pushTokenDeliveryLock;
    private DeviceManager manager;

    private final User owner = user();

    @BeforeEach
    void setUp() {
        deviceDao = mock(DeviceDao.class);
        securityService = mock(SecurityService.class);
        pushTokenDeliveryLock = mock(PushTokenDeliveryLock.class);
        when(securityService.getAuthenticatedUser()).thenReturn(owner);

        manager = new DeviceManager(deviceDao, securityService, pushTokenDeliveryLock, new DeviceMapperImpl());

        when(deviceDao.save(any())).thenAnswer(call -> call.getArgument(0));
        when(deviceDao.findAllByPushToken(any())).thenReturn(List.of());
        when(securityService.isActiveUser(owner.getId())).thenReturn(true);
    }

    private static User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        return user;
    }

    private Device device(User forUser, String clientDeviceId) {
        Device device = new Device();
        device.setId(UUID.randomUUID());
        device.setUser(forUser);
        device.setClientDeviceId(clientDeviceId);
        when(deviceDao.findByUser_IdAndClientDeviceId(forUser.getId(), clientDeviceId))
                .thenReturn(Optional.of(device));
        return device;
    }

    @Test
    void theTokenLandsOnTheCallersOwnDevice() {
        Device own = device(owner, DEVICE_ID);

        manager.savePushToken(USER_AGENT, DEVICE_ID, TOKEN);

        assertEquals(TOKEN, own.getPushToken());
        verify(pushTokenDeliveryLock).lockMutations();
        verify(pushTokenDeliveryLock).lockUser(owner.getId());
        verify(pushTokenDeliveryLock).lockTokens(List.of(TOKEN));
    }

    @Test
    void aDeviceThatDoesNotBelongToTheCallerIsNotFound() {
        when(deviceDao.findByUser_IdAndClientDeviceId(owner.getId(), "someone-elses-device"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> manager.savePushToken(USER_AGENT, "someone-elses-device", TOKEN));
    }

    @Test
    void thePreviousOwnerOfTheTokenLosesIt() {
        Device own = device(owner, DEVICE_ID);

        Device previous = new Device();
        previous.setId(UUID.randomUUID());
        previous.setUser(user());
        previous.setPushToken(TOKEN);
        when(deviceDao.findAllByPushToken(TOKEN)).thenReturn(List.of(previous));

        manager.savePushToken(USER_AGENT, DEVICE_ID, TOKEN);

        assertNull(previous.getPushToken());
        assertEquals(TOKEN, own.getPushToken());
        InOrder persistenceOrder = inOrder(deviceDao);
        persistenceOrder.verify(deviceDao).flush();
        persistenceOrder.verify(deviceDao).saveAndFlush(own);
    }

    @Test
    void rotatingATokenLocksTheOldAndNewValuesInDeterministicOrder() {
        Device own = device(owner, DEVICE_ID);
        own.setPushToken("old-token");

        manager.savePushToken(USER_AGENT, DEVICE_ID, TOKEN);

        verify(pushTokenDeliveryLock).lockTokens(List.of("old-token", TOKEN));
    }

    @Test
    void reRegisteringTheSameTokenOnTheSameDeviceKeepsIt() {
        Device own = device(owner, DEVICE_ID);
        own.setPushToken(TOKEN);
        when(deviceDao.findAllByPushToken(TOKEN)).thenReturn(List.of(own));

        manager.savePushToken(USER_AGENT, DEVICE_ID, TOKEN);

        assertEquals(TOKEN, own.getPushToken());
    }

    @Test
    void loggingOutOnlyDropsTheTokenNotTheDevice() {
        Device own = device(owner, DEVICE_ID);
        own.setPushToken(TOKEN);

        manager.clearPushToken(USER_AGENT, DEVICE_ID);

        assertNull(own.getPushToken());
        verify(pushTokenDeliveryLock).lockMutations();
        verify(pushTokenDeliveryLock).lockTokens(List.of(TOKEN));
        verify(deviceDao, never()).delete(any());
    }

    @Test
    void clearingOnAnUnknownDeviceIsSilent() {
        when(deviceDao.findByUser_IdAndClientDeviceId(any(), any())).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> manager.clearPushToken(USER_AGENT, "unknown"));
    }

    @Test
    void deliveryReadsATokenOnlyThroughTheCurrentDeviceOwner() {
        Device own = device(owner, DEVICE_ID);
        own.setPushToken(TOKEN);
        when(deviceDao.findByIdAndUser_IdAndPushTokenIsNotNull(own.getId(), owner.getId()))
                .thenReturn(Optional.of(own));

        assertEquals(Optional.of(TOKEN), manager.currentPushToken(owner.getId(), own.getId()));
    }

    @Test
    void invalidTokenCleanupCannotEraseARefreshedToken() {
        UUID deviceId = UUID.randomUUID();
        when(deviceDao.clearPushTokenIfMatches(deviceId, TOKEN)).thenReturn(0);

        assertFalse(manager.clearPushTokenIfMatches(owner.getId(), deviceId, TOKEN));
        verify(pushTokenDeliveryLock).lockMutations();
        verify(pushTokenDeliveryLock).lockUser(owner.getId());
        verify(pushTokenDeliveryLock).lockTokens(List.of(TOKEN));
    }
}
