package com.exskylab.koala.business.concretes;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.exskylab.koala.business.abstracts.DeviceService;
import com.exskylab.koala.core.properties.PushDeliveryProperties;
import com.exskylab.koala.core.utilities.push.PushDeliveryException;
import com.exskylab.koala.core.utilities.push.PushFailureCode;
import com.exskylab.koala.core.utilities.push.PushMessage;
import com.exskylab.koala.core.utilities.push.PushService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PushDeliveryManagerTest {

    private static final Instant NOW = Instant.parse("2026-09-09T12:00:00Z");

    private PushDeliveryStore store;
    private DeviceService deviceService;
    private PushService pushService;
    private PushTokenDeliveryLock tokenDeliveryLock;
    private PushDeliveryManager manager;
    private PushDeliveryClaim claim;

    @BeforeEach
    void setUp() {
        store = mock(PushDeliveryStore.class);
        deviceService = mock(DeviceService.class);
        pushService = mock(PushService.class);
        tokenDeliveryLock = mock(PushTokenDeliveryLock.class);
        PushDeliveryProperties properties = new PushDeliveryProperties();
        properties.setMaxAttempts(3);
        properties.setInitialBackoff(Duration.ofMinutes(1));
        properties.setMaxBackoff(Duration.ofMinutes(15));
        properties.setMaxProviderDeferral(Duration.ofHours(24));
        properties.setClaimTimeout(Duration.ofMinutes(2));
        manager = new PushDeliveryManager(store, deviceService, pushService, tokenDeliveryLock, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
        claim = new PushDeliveryClaim(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "current-token", "title", "body", Map.of("notificationId", "n"), 1);
        when(store.claim(claim.notificationId(), NOW)).thenReturn(Optional.of(claim));
        doAnswer(invocation -> {
            Supplier<String> operation = invocation.getArgument(3);
            return PushTokenDeliveryResult.current(operation.get());
        }).when(tokenDeliveryLock).tryExecuteIfCurrent(any(), any(), any(), any());
    }

    @Test
    void successfulDeliveryCompletesTheOwnedClaim() {
        when(pushService.send(any())).thenReturn("projects/p/messages/m");

        manager.deliver(claim.notificationId());

        ArgumentCaptor<PushMessage> message = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushService).send(message.capture());
        assertEquals("current-token", message.getValue().token());
        verify(store).complete(claim, "projects/p/messages/m", NOW);
    }

    @Test
    void retryableFailureIsPersistedForADeferredAttempt() {
        PushDeliveryException failure = PushDeliveryException.retryable(
                PushFailureCode.RATE_LIMITED, Duration.ofMinutes(30), new RuntimeException("secret response"));
        when(pushService.send(any())).thenThrow(failure);

        manager.deliver(claim.notificationId());

        verify(store).retry(claim, PushFailureCode.RATE_LIMITED, NOW.plus(Duration.ofMinutes(30)), NOW);
        verify(store, never()).fail(any(), any(), any());
    }

    @Test
    void retryDelayStartsWhenTheProviderFailureOccurs() {
        Instant failedAt = NOW.plusSeconds(30);
        Clock movingClock = mock(Clock.class);
        when(movingClock.instant()).thenReturn(NOW, failedAt);
        PushDeliveryProperties properties = new PushDeliveryProperties();
        properties.setMaxAttempts(3);
        properties.setInitialBackoff(Duration.ofMinutes(1));
        properties.setMaxBackoff(Duration.ofMinutes(15));
        properties.setMaxProviderDeferral(Duration.ofHours(24));
        properties.setClaimTimeout(Duration.ofMinutes(2));
        manager = new PushDeliveryManager(store, deviceService, pushService, tokenDeliveryLock, properties,
                movingClock);
        PushDeliveryException failure = PushDeliveryException.retryable(
                PushFailureCode.RATE_LIMITED, Duration.ofMinutes(30), new RuntimeException("provider response"));
        when(pushService.send(any())).thenThrow(failure);

        manager.deliver(claim.notificationId());

        verify(store).retry(claim, PushFailureCode.RATE_LIMITED,
                failedAt.plus(Duration.ofMinutes(30)), failedAt);
    }

    @Test
    void providerRetryAfterIsCappedByTheDurableDeferralLimit() {
        PushDeliveryException failure = PushDeliveryException.retryable(
                PushFailureCode.RATE_LIMITED, Duration.ofDays(30), new RuntimeException("secret response"));
        when(pushService.send(any())).thenThrow(failure);

        manager.deliver(claim.notificationId());

        verify(store).retry(claim, PushFailureCode.RATE_LIMITED, NOW.plus(Duration.ofHours(24)), NOW);
    }

    @Test
    void invalidTokenIsClearedOnlyWhenItStillMatchesTheClaim() {
        when(pushService.send(any())).thenThrow(PushDeliveryException.invalidToken(
                new RuntimeException("response containing token")));

        manager.deliver(claim.notificationId());

        verify(deviceService).clearPushTokenIfMatches(claim.recipientId(), claim.deviceId(), claim.token());
        verify(store).fail(claim, PushFailureCode.INVALID_TOKEN, NOW);
    }

    @Test
    void reassignedTokenIsSuppressedUnderTheRegistrationLockBeforeSend() {
        doReturn(PushTokenDeliveryResult.notCurrent()).when(tokenDeliveryLock)
                .tryExecuteIfCurrent(any(), any(), any(), any());

        manager.deliver(claim.notificationId());

        verify(tokenDeliveryLock).tryExecuteIfCurrent(
                org.mockito.ArgumentMatchers.eq(claim.recipientId()),
                org.mockito.ArgumentMatchers.eq(claim.deviceId()),
                org.mockito.ArgumentMatchers.eq(claim.token()), any());
        verify(pushService, never()).send(any());
        verify(store).suppress(claim, PushFailureCode.TOKEN_NO_LONGER_ASSIGNED, NOW);
    }

    @Test
    void busyTokenSerializationIsDurablyDeferredWithoutConsumingAProviderAttempt() {
        doReturn(PushTokenDeliveryResult.contended()).when(tokenDeliveryLock)
                .tryExecuteIfCurrent(any(), any(), any(), any());

        manager.deliver(claim.notificationId());

        verify(pushService, never()).send(any());
        verify(store).deferForTokenContention(claim, NOW.plus(Duration.ofMinutes(1)), NOW);
        verify(store, never()).recordInfrastructureFailure(any(), any());
    }

    @Test
    void unexpectedFailuresEscapeForRabbitRetryAndDlq() {
        when(pushService.send(any())).thenThrow(new IllegalStateException("database unavailable"));

        try {
            manager.deliver(claim.notificationId());
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("database unavailable"));
        }

        verify(store, never()).complete(any(), any(), any());
        verify(store).recordInfrastructureFailure(claim, NOW);
        verify(store, never()).retry(any(), any(), any(), any());
        verify(store, never()).fail(any(), any(), any());
    }

    @Test
    void persistenceFailureWhileRecordingAnExpectedResultIsReleasedForRabbitRetry() {
        PushDeliveryException providerFailure = PushDeliveryException.retryable(
                PushFailureCode.PROVIDER_UNAVAILABLE, null, new RuntimeException("provider response"));
        when(pushService.send(any())).thenThrow(providerFailure);
        org.mockito.Mockito.doThrow(new IllegalStateException("database unavailable"))
                .when(store).retry(any(), any(), any(), any());

        assertThrows(IllegalStateException.class, () -> manager.deliver(claim.notificationId()));

        verify(store).recordInfrastructureFailure(claim, NOW);
    }

    @Test
    void scheduledDeliveryLogsTheUnexpectedFailureWithItsStackTrace() {
        RuntimeException failure = new IllegalStateException("database unavailable");
        when(store.findDueIds(NOW)).thenReturn(java.util.List.of(claim.notificationId()));
        when(store.claim(claim.notificationId(), NOW)).thenThrow(failure);
        Logger logger = (Logger) LoggerFactory.getLogger(PushDeliveryManager.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            assertEquals(1, manager.deliverDueBatch());

            ILoggingEvent warning = appender.list.getLast();
            assertEquals(IllegalStateException.class.getName(), warning.getThrowableProxy().getClassName());
            assertEquals("database unavailable", warning.getThrowableProxy().getMessage());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
