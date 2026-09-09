package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.DeviceService;
import com.exskylab.koala.core.properties.PushDeliveryProperties;
import com.exskylab.koala.core.utilities.push.PushFailureCode;
import com.exskylab.koala.dataAccess.NotificationDao;
import com.exskylab.koala.entities.Device;
import com.exskylab.koala.entities.Notification;
import com.exskylab.koala.entities.NotificationChannel;
import com.exskylab.koala.entities.NotificationStatus;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class PushDeliveryStoreTest {

    private static final Instant NOW = Instant.parse("2026-09-09T12:00:00Z");

    private NotificationDao notificationDao;
    private DeviceService deviceService;
    private PushDeliveryStore store;
    private Notification notification;

    @BeforeEach
    void setUp() {
        notificationDao = mock(NotificationDao.class);
        deviceService = mock(DeviceService.class);
        PushDeliveryProperties properties = new PushDeliveryProperties();
        properties.setMaxAttempts(3);
        store = new PushDeliveryStore(notificationDao, deviceService, properties, new ObjectMapper());

        User recipient = new User();
        recipient.setId(UUID.randomUUID());
        Device device = new Device();
        device.setId(UUID.randomUUID());
        device.setUser(recipient);
        device.setPushToken("current-token");
        notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setRecipient(recipient);
        notification.setDestinationDevice(device);
        notification.setDestinationPushToken("stale-queue-token");
        notification.setChannel(NotificationChannel.PUSH);
        notification.setStatus(NotificationStatus.QUEUED);
        notification.setSubject("title");
        notification.setBody("body");
        notification.setPushDataPayload("{\"notificationId\":\"attacker\",\"route\":\"jobs\"}");
        when(notificationDao.findForPushDelivery(notification.getId())).thenReturn(Optional.of(notification));
        when(deviceService.currentPushToken(recipient.getId(), device.getId()))
                .thenReturn(Optional.of("current-token"));
    }

    @Test
    void claimUsesCurrentOwnershipAndProtectsTheReservedNotificationId() {
        PushDeliveryClaim claim = store.claim(notification.getId(), NOW).orElseThrow();

        assertEquals("current-token", claim.token());
        assertEquals(notification.getId().toString(), claim.data().get("notificationId"));
        assertEquals("jobs", claim.data().get("route"));
        assertEquals(NotificationStatus.PROCESSING, notification.getStatus());
        assertEquals(1, notification.getPushAttemptCount());
    }

    @Test
    void duplicateClaimCannotAcquireAProcessingDelivery() {
        assertTrue(store.claim(notification.getId(), NOW).isPresent());

        assertTrue(store.claim(notification.getId(), NOW).isEmpty());
    }

    @Test
    void tokenThatNoLongerBelongsToTheRecipientIsSuppressed() {
        when(deviceService.currentPushToken(notification.getRecipient().getId(),
                notification.getDestinationDevice().getId())).thenReturn(Optional.empty());

        assertTrue(store.claim(notification.getId(), NOW).isEmpty());

        assertEquals(NotificationStatus.SUPPRESSED, notification.getStatus());
        assertEquals("TOKEN_NO_LONGER_ASSIGNED", notification.getFailureReason());
        verify(notificationDao).saveAndFlush(notification);
    }

    @Test
    void corruptPersistedPayloadBecomesASanitizedTerminalFailure() {
        notification.setPushDataPayload("{not-json token=current-token}");

        assertTrue(store.claim(notification.getId(), NOW).isEmpty());

        assertEquals(NotificationStatus.FAILED, notification.getStatus());
        assertEquals("INVALID_MESSAGE", notification.getFailureReason());
    }

    @Test
    void retryAtTheAttemptLimitBecomesTerminal() {
        PushDeliveryClaim claim = store.claim(notification.getId(), NOW).orElseThrow();
        notification.setPushAttemptCount(3);

        store.retry(claim, PushFailureCode.PROVIDER_UNAVAILABLE, NOW.plus(Duration.ofMinutes(1)), NOW);

        assertEquals(NotificationStatus.FAILED, notification.getStatus());
        assertEquals("RETRIES_EXHAUSTED", notification.getFailureReason());
    }

    @Test
    void successfulCompletionClearsPriorRetryFailureState() {
        PushDeliveryClaim claim = store.claim(notification.getId(), NOW).orElseThrow();
        notification.setFailureReason("PROVIDER_UNAVAILABLE");
        notification.setPushNextAttemptAt(NOW.plusSeconds(30));

        store.complete(claim, "provider-message", NOW.plusSeconds(1));

        assertEquals(NotificationStatus.SENT, notification.getStatus());
        assertEquals(null, notification.getFailureReason());
        assertEquals(null, notification.getPushNextAttemptAt());
    }

    @Test
    void staleClaimIsReleasedForTheSchedulerWithoutLosingItsAttempt() {
        notification.setStatus(NotificationStatus.PROCESSING);
        notification.setPushAttemptCount(1);
        notification.setPushProcessingClaimId(UUID.randomUUID());
        notification.setPushProcessingStartedAt(NOW.minus(Duration.ofMinutes(3)));
        when(notificationDao.findStalePushDeliveryIds(any(), any())).thenReturn(List.of(notification.getId()));

        assertEquals(1, store.recoverStale(NOW.minus(Duration.ofMinutes(2)), NOW));

        assertEquals(NotificationStatus.QUEUED, notification.getStatus());
        assertEquals("STALE_CLAIM_RECOVERED", notification.getFailureReason());
        assertEquals(NOW, notification.getPushNextAttemptAt());
        assertEquals(1, notification.getPushAttemptCount());
    }

    @Test
    void tokenContentionReturnsTheClaimToTheSchedulerWithoutConsumingAnAttempt() {
        PushDeliveryClaim claim = store.claim(notification.getId(), NOW).orElseThrow();
        Instant retryAt = NOW.plus(Duration.ofMinutes(1));

        store.deferForTokenContention(claim, retryAt, NOW);

        assertEquals(NotificationStatus.QUEUED, notification.getStatus());
        assertEquals(PushFailureCode.TOKEN_SERIALIZATION_BUSY.name(), notification.getFailureReason());
        assertEquals(retryAt, notification.getPushNextAttemptAt());
        assertEquals(0, notification.getPushAttemptCount());
    }
}
