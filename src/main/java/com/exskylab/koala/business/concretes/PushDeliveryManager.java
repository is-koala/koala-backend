package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.DeviceService;
import com.exskylab.koala.business.abstracts.PushDeliveryService;
import com.exskylab.koala.core.properties.NotificationDispatchProperties;
import com.exskylab.koala.core.properties.PushDeliveryProperties;
import com.exskylab.koala.core.utilities.push.PushDeliveryException;
import com.exskylab.koala.core.utilities.push.PushFailureCode;
import com.exskylab.koala.core.utilities.push.PushMessage;
import com.exskylab.koala.core.utilities.push.PushService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
public class PushDeliveryManager implements PushDeliveryService {

    private final PushDeliveryStore store;
    private final DeviceService deviceService;
    private final PushService pushService;
    private final PushTokenDeliveryLock tokenDeliveryLock;
    private final PushDeliveryProperties properties;
    private final NotificationDispatchProperties dispatchProperties;
    private final Clock clock;

    public PushDeliveryManager(PushDeliveryStore store, DeviceService deviceService,
                               PushService pushService, PushTokenDeliveryLock tokenDeliveryLock,
                               PushDeliveryProperties properties,
                               NotificationDispatchProperties dispatchProperties, Clock clock) {
        this.store = store;
        this.deviceService = deviceService;
        this.pushService = pushService;
        this.tokenDeliveryLock = tokenDeliveryLock;
        this.properties = properties;
        this.dispatchProperties = dispatchProperties;
        this.clock = clock;
    }

    @Override
    public void deliver(UUID notificationId) {
        Instant now = clock.instant();
        store.claim(notificationId, now).ifPresent(this::send);
    }

    @Override
    public int deliverDueBatch() {
        Instant now = clock.instant();
        int attempted = 0;
        for (UUID notificationId : store.findDueIds(now)) {
            try {
                deliver(notificationId);
            } catch (RuntimeException exception) {
                log.warn("Unexpected push delivery failure for notification {}; stale-claim recovery remains active",
                        notificationId, exception);
            }
            attempted++;
        }
        return attempted;
    }

    @Override
    public int recoverStaleClaims() {
        Instant now = clock.instant();
        return store.recoverStale(now.minus(properties.getClaimTimeout()), now);
    }

    private void send(PushDeliveryClaim claim) {
        try {
            try {
                PushTokenDeliveryResult<String> result = tokenDeliveryLock.tryExecuteIfCurrent(
                        claim.recipientId(), claim.deviceId(), claim.token(),
                        () -> pushService.send(new PushMessage(
                                claim.token(), claim.title(), claim.body(), claim.data()),
                                dispatchProperties.getProviderTimeout()));
                if (!result.lockAcquired()) {
                    Instant deferredAt = clock.instant();
                    store.deferForTokenContention(
                            claim, deferredAt.plus(properties.getInitialBackoff()), deferredAt);
                    return;
                }
                if (!result.currentOwner()) {
                    store.suppress(claim, PushFailureCode.TOKEN_NO_LONGER_ASSIGNED, clock.instant());
                    return;
                }
                store.complete(claim, result.result(), clock.instant());
            } catch (PushDeliveryException exception) {
                handleExpectedFailure(claim, exception);
            }
        } catch (RuntimeException exception) {
            store.recordInfrastructureFailure(claim, clock.instant());
            throw exception;
        }
    }

    private void handleExpectedFailure(PushDeliveryClaim claim, PushDeliveryException exception) {
        Instant failedAt = clock.instant();
        if (exception.invalidToken()) {
            deviceService.clearPushTokenIfMatches(claim.recipientId(), claim.deviceId(), claim.token());
            store.fail(claim, exception.code(), failedAt);
            return;
        }
        if (exception.retryable()) {
            store.retry(claim, exception.code(), nextAttemptAt(claim, exception, failedAt), failedAt);
            return;
        }
        store.fail(claim, exception.code(), failedAt);
    }

    private Instant nextAttemptAt(PushDeliveryClaim claim, PushDeliveryException exception, Instant now) {
        long multiplier = 1L << Math.min(Math.max(claim.attempt() - 1, 0), 20);
        Duration base = properties.getInitialBackoff().multipliedBy(multiplier);
        if (base.compareTo(properties.getMaxBackoff()) > 0) {
            base = properties.getMaxBackoff();
        }
        double jitter = ThreadLocalRandom.current().nextDouble(0.5, 1.5);
        Duration jittered = Duration.ofMillis(Math.max(1, Math.round(base.toMillis() * jitter)));
        Duration delay = jittered.compareTo(properties.getMaxBackoff()) > 0
                ? properties.getMaxBackoff() : jittered;
        Duration retryAfter = exception.retryAfter();
        if (retryAfter != null && !retryAfter.isNegative() && !retryAfter.isZero()) {
            Duration boundedRetryAfter = retryAfter.compareTo(properties.getMaxProviderDeferral()) > 0
                    ? properties.getMaxProviderDeferral() : retryAfter;
            if (boundedRetryAfter.compareTo(delay) > 0) {
                delay = boundedRetryAfter;
            }
        }
        return now.plus(delay);
    }
}
