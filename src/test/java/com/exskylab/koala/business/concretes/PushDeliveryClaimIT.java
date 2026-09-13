package com.exskylab.koala.business.concretes;

import com.exskylab.koala.support.AbstractIntegrationTest;
import com.exskylab.koala.support.OverlappingWrites;
import com.exskylab.koala.support.TestLatches;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.DeviceService;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.ClientPlatform;
import com.exskylab.koala.core.properties.NotificationDispatchProperties;
import com.exskylab.koala.core.properties.PushDeliveryProperties;
import com.exskylab.koala.core.utilities.push.PushService;
import com.exskylab.koala.dataAccess.DeviceDao;
import com.exskylab.koala.entities.Device;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PushDeliveryClaimIT extends AbstractIntegrationTest {

    private static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private PushDeliveryStore store;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DeviceDao deviceDao;

    @Autowired
    private PushTokenDeliveryLock tokenDeliveryLock;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void tokenOwnershipAdvisoryLockIsExecutableInsideARegistrationTransaction() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            tokenDeliveryLock.lockMutations();
            tokenDeliveryLock.lockTokens(List.of("fcm-token"));
        });
    }

    @Test
    void deliverySessionLockSerializesWithRegistrationTransactions() {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        boolean[] acquiredWhileSending = new boolean[1];
        UUID deviceId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into devices (id, created_at, is_deleted, user_id, type, name, client_device_id, push_token)
                values (?, now(), false, ?, 'ANDROID', 'lock-test', ?, 'shared-fcm-token')
                """, deviceId, SYSTEM_USER_ID, UUID.randomUUID().toString());

        try {
            PushTokenDeliveryResult<String> result = tokenDeliveryLock.executeIfCurrent(
                    SYSTEM_USER_ID, deviceId, "shared-fcm-token", () -> {
                        acquiredWhileSending[0] = Boolean.TRUE.equals(transaction.execute(
                                status -> jdbcTemplate.queryForObject(
                                        "select pg_try_advisory_xact_lock(hashtextextended(cast(? as text), 0))",
                                        Boolean.class, PushTokenDeliveryLock.tokenKey("shared-fcm-token"))));
                        return "sent";
                    });

            assertTrue(result.currentOwner());
            assertEquals("sent", result.result());
            assertFalse(acquiredWhileSending[0]);
            assertTrue(Boolean.TRUE.equals(transaction.execute(status -> jdbcTemplate.queryForObject(
                    "select pg_try_advisory_xact_lock(hashtextextended(cast(? as text), 0))",
                    Boolean.class, PushTokenDeliveryLock.tokenKey("shared-fcm-token")))));
        } finally {
            jdbcTemplate.update("delete from devices where id = ?", deviceId);
        }
    }

    @Test
    void deliverySerializesWithARealLogoutTokenClear() throws Exception {
        exerciseDeliveryAgainstMutation(null);
    }

    @Test
    void deliverySerializesWithARealTokenRotation() throws Exception {
        exerciseDeliveryAgainstMutation("replacement-fcm-token");
    }

    @Test
    void registrationCannotResurrectADeviceDeletedByAccountAnonymization() throws Exception {
        UUID deviceId = UUID.randomUUID();
        String clientDeviceId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                insert into devices (id, created_at, is_deleted, user_id, type, name, client_device_id, push_token)
                values (?, now(), false, ?, 'ANDROID', 'deletion-race-test', ?, 'old-fcm-token')
                """, deviceId, SYSTEM_USER_ID, clientDeviceId);
        DeviceManager deviceManager = deviceManager();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch deletionUpdated = new CountDownLatch(1);
        CountDownLatch allowDeletionCommit = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> deletion = executor.submit(() -> transaction.executeWithoutResult(status -> {
                tokenDeliveryLock.lockMutations();
                tokenDeliveryLock.lockTokens(List.of("old-fcm-token"));
                jdbcTemplate.update("""
                        update devices set name = null, client_device_id = null, push_token = null,
                            is_deleted = true, deleted_at = now() where id = ?
                        """, deviceId);
                deletionUpdated.countDown();
                await(allowDeletionCommit);
            }));
            TestLatches.awaitProgress(deletionUpdated);
            Future<?> registration = executor.submit(() -> transaction.executeWithoutResult(status ->
                    deviceManager.savePushToken("Dart/3.9 (dart:io)", clientDeviceId, "new-fcm-token")));
            assertThrows(TimeoutException.class, () -> registration.get(250, TimeUnit.MILLISECONDS));

            allowDeletionCommit.countDown();
            TestLatches.getProgress(deletion);
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> TestLatches.getProgress(registration));
            assertTrue(failure.getCause() instanceof ResourceNotFoundException);
            assertEquals("true:", jdbcTemplate.queryForObject("""
                    select is_deleted::text || ':' || coalesce(push_token, '') from devices where id = ?
                    """, String.class, deviceId));
        } finally {
            allowDeletionCommit.countDown();
            executor.shutdownNow();
            jdbcTemplate.update("delete from devices where id = ?", deviceId);
        }
    }

    @Test
    void clientDeviceMetadataUpdateSerializesWithInvalidTokenCleanup() throws Exception {
        String userAgent = "Dart/9.9 (dart:io)";
        String clientDeviceId = UUID.randomUUID().toString();
        UUID deviceId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into devices (id, created_at, is_deleted, user_id, type, name, client_device_id, push_token)
                values (?, now(), false, ?, 'ANDROID', 'stale-name', ?, 'invalid-fcm-token')
                """, deviceId, SYSTEM_USER_ID, clientDeviceId);

        exerciseMetadataUpdateAgainstInvalidTokenCleanup(
                deviceId, userAgent, clientDeviceId, ClientPlatform.ANDROID);
    }

    @Test
    void legacyUserAgentMetadataUpdateSerializesWithInvalidTokenCleanup() throws Exception {
        String userAgent = "Mozilla/5.0 (Linux; Android 16; Pixel 10 Pro) AppleWebKit/537.36 "
                + "Chrome/140.0.0.0 Mobile Safari/537.36";
        DeviceManager deviceManager = deviceManager();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Device device = transaction.execute(status -> deviceManager.findOrCreateDevice(
                systemUser(), "192.0.2.10", userAgent, null, ClientPlatform.ANDROID));
        UUID deviceId = device.getId();
        jdbcTemplate.update("update devices set push_token = 'invalid-fcm-token' where id = ?", deviceId);

        exerciseMetadataUpdateAgainstInvalidTokenCleanup(
                deviceId, userAgent, null, ClientPlatform.ANDROID);
    }

    @Test
    void concurrentClaimsAllowOnlyOneWorkerAndCompletionIsFenced() throws Exception {
        UUID deviceId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        seed(deviceId, notificationId);
        OverlappingWrites overlap = new OverlappingWrites();

        try {
            List<String> outcomes = overlap.outcomesOf(
                    () -> claim(notificationId, overlap),
                    () -> claim(notificationId, overlap));

            assertEquals(List.of("CLAIMED", "SKIPPED"), outcomes.stream().sorted().toList());
            assertEquals(1, jdbcTemplate.queryForObject(
                    "select push_attempt_count from notifications where id = ?", Integer.class, notificationId));

            UUID realClaimId = jdbcTemplate.queryForObject(
                    "select push_processing_claim_id from notifications where id = ?", UUID.class, notificationId);
            PushDeliveryClaim stale = new PushDeliveryClaim(notificationId, UUID.randomUUID(), SYSTEM_USER_ID, deviceId,
                    "current-token", "title", "body", java.util.Map.of(), 1);
            store.complete(stale, "stale-message", Instant.now());
            assertEquals("PROCESSING", jdbcTemplate.queryForObject(
                    "select status from notifications where id = ?", String.class, notificationId));

            PushDeliveryClaim real = new PushDeliveryClaim(notificationId, realClaimId, SYSTEM_USER_ID, deviceId,
                    "current-token", "title", "body", java.util.Map.of(), 1);
            store.complete(real, "provider-message", Instant.now());
            assertEquals("SENT:provider-message", jdbcTemplate.queryForObject(
                    "select status || ':' || external_id from notifications where id = ?",
                    String.class, notificationId));
        } finally {
            jdbcTemplate.update("delete from notifications where id = ?", notificationId);
            jdbcTemplate.update("delete from devices where id = ?", deviceId);
        }
    }

    @Test
    void retrySchedulerNeverClaimsAnInitialRecordReservedForOutboxPublishing() {
        UUID deviceId = UUID.randomUUID();
        UUID initialId = UUID.randomUUID();
        UUID retryId = UUID.randomUUID();
        seed(deviceId, initialId);
        jdbcTemplate.update("""
                insert into notifications (
                    id, created_at, is_deleted, recipient_id, channel, category,
                    destination_device_id, subject, body, status, push_attempt_count,
                    push_data_payload, push_next_attempt_at)
                values (?, now(), false, ?, 'PUSH', 'ACCOUNT_SECURITY', ?, 'title', 'body',
                    'QUEUED', 1, '{}', now())
                """, retryId, SYSTEM_USER_ID, deviceId);

        try {
            assertEquals(List.of(retryId), store.findDueIds(Instant.now().plusSeconds(1)));
        } finally {
            jdbcTemplate.update("delete from notifications where id in (?, ?)", initialId, retryId);
            jdbcTemplate.update("delete from devices where id = ?", deviceId);
        }
    }

    @Test
    void providerWorstCaseCannotBeRecoveredIntoADuplicateSend() throws Exception {
        UUID deviceId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        seed(deviceId, notificationId);
        Instant claimedAt = Instant.parse("2026-09-09T12:00:00Z");
        AtomicReference<Instant> now = new AtomicReference<>(claimedAt);
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(ignored -> now.get());
        CountDownLatch providerEntered = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        AtomicInteger providerCalls = new AtomicInteger();
        PushService pushService = (message, timeout) -> {
            int call = providerCalls.incrementAndGet();
            providerEntered.countDown();
            await(releaseProvider);
            return "provider-message-" + call;
        };
        PushDeliveryManager manager = new PushDeliveryManager(
                store, deviceService, pushService, tokenDeliveryLock, new PushDeliveryProperties(),
                new NotificationDispatchProperties(), clock);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> firstDelivery = executor.submit(() -> manager.deliver(notificationId));
            TestLatches.awaitProgress(providerEntered);
            now.set(claimedAt.plus(Duration.ofSeconds(505)));

            int recovered = manager.recoverStaleClaims();
            Future<?> possibleDuplicate = executor.submit(() -> manager.deliver(notificationId));
            releaseProvider.countDown();
            TestLatches.getProgress(firstDelivery);
            TestLatches.getProgress(possibleDuplicate);

            assertEquals(0, recovered);
            assertEquals(1, providerCalls.get());
        } finally {
            releaseProvider.countDown();
            executor.shutdownNow();
            jdbcTemplate.update("delete from notifications where id = ?", notificationId);
            jdbcTemplate.update("delete from devices where id = ?", deviceId);
        }
    }

    @Test
    void claimLeaseDoesNotStartWhileTokenSerializationIsBusy() throws Exception {
        UUID deviceId = UUID.randomUUID();
        UUID notificationId = UUID.randomUUID();
        seed(deviceId, notificationId);
        Instant startedAt = Instant.parse("2026-09-09T12:00:00Z");
        AtomicReference<Instant> now = new AtomicReference<>(startedAt);
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(ignored -> now.get());
        AtomicInteger providerCalls = new AtomicInteger();
        PushService pushService = (message, timeout) -> "provider-message-" + providerCalls.incrementAndGet();
        PushDeliveryManager manager = new PushDeliveryManager(
                store, deviceService, pushService, tokenDeliveryLock, new PushDeliveryProperties(),
                new NotificationDispatchProperties(), clock);
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> holder = executor.submit(() -> tokenDeliveryLock.executeIfCurrent(
                    SYSTEM_USER_ID, deviceId, "current-token", () -> {
                        lockHeld.countDown();
                        await(releaseLock);
                        return "prior-delivery";
                    }));
            TestLatches.awaitProgress(lockHeld);

            Future<?> contendedDelivery = executor.submit(() -> manager.deliver(notificationId));
            TestLatches.getProgress(contendedDelivery);
            assertEquals("QUEUED:0:TOKEN_SERIALIZATION_BUSY", jdbcTemplate.queryForObject("""
                    select status || ':' || push_attempt_count || ':' || failure_reason
                      from notifications where id = ?
                    """, String.class, notificationId));
            assertEquals(0, providerCalls.get());

            now.set(startedAt.plus(Duration.ofMinutes(11)));
            assertEquals(0, manager.recoverStaleClaims());
            releaseLock.countDown();
            TestLatches.getProgress(holder);

            manager.deliver(notificationId);
            assertEquals(1, providerCalls.get());
        } finally {
            releaseLock.countDown();
            executor.shutdownNow();
            jdbcTemplate.update("delete from notifications where id = ?", notificationId);
            jdbcTemplate.update("delete from devices where id = ?", deviceId);
        }
    }

    @Test
    void clientDeviceMetadataCannotRestoreATokenMovedToAnotherUser() throws Exception {
        exerciseMetadataUpdateAgainstTokenMovement(UUID.randomUUID().toString());
    }

    @Test
    void legacyMetadataCannotRestoreATokenMovedToAnotherUser() throws Exception {
        exerciseMetadataUpdateAgainstTokenMovement(null);
    }

    private String claim(UUID notificationId, OverlappingWrites overlap) {
        overlap.meet();
        return store.claim(notificationId, Instant.now()).isPresent() ? "CLAIMED" : "SKIPPED";
    }

    private void exerciseDeliveryAgainstMutation(String replacementToken) throws Exception {
        UUID deviceId = UUID.randomUUID();
        String clientDeviceId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                insert into devices (id, created_at, is_deleted, user_id, type, name, client_device_id, push_token)
                values (?, now(), false, ?, 'ANDROID', 'concurrency-test', ?, 'shared-fcm-token')
                """, deviceId, SYSTEM_USER_ID, clientDeviceId);
        DeviceManager deviceManager = deviceManager();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch deliveryEntered = new CountDownLatch(1);
        CountDownLatch releaseDelivery = new CountDownLatch(1);
        CountDownLatch mutationStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<PushTokenDeliveryResult<String>> delivery = executor.submit(() ->
                    tokenDeliveryLock.executeIfCurrent(SYSTEM_USER_ID, deviceId, "shared-fcm-token", () -> {
                        deliveryEntered.countDown();
                        await(releaseDelivery);
                        return "sent";
                    }));
            TestLatches.awaitProgress(deliveryEntered);
            Future<?> mutation = executor.submit(() -> {
                mutationStarted.countDown();
                transaction.executeWithoutResult(status -> {
                    if (replacementToken == null) {
                        deviceManager.clearPushToken("Dart/3.9 (dart:io)", clientDeviceId);
                    } else {
                        deviceManager.savePushToken("Dart/3.9 (dart:io)", clientDeviceId, replacementToken);
                    }
                });
            });
            TestLatches.awaitProgress(mutationStarted);
            assertThrows(TimeoutException.class, () -> mutation.get(250, TimeUnit.MILLISECONDS));

            releaseDelivery.countDown();
            assertTrue(TestLatches.getProgress(delivery).currentOwner());
            TestLatches.getProgress(mutation);

            assertEquals(replacementToken, jdbcTemplate.queryForObject(
                    "select push_token from devices where id = ?", String.class, deviceId));
        } finally {
            releaseDelivery.countDown();
            executor.shutdownNow();
            jdbcTemplate.update("delete from devices where id = ?", deviceId);
        }
    }

    private void exerciseMetadataUpdateAgainstInvalidTokenCleanup(
            UUID deviceId, String userAgent, String clientDeviceId, ClientPlatform platform) throws Exception {
        DeviceManager deviceManager = deviceManager();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        CountDownLatch metadataUpdated = new CountDownLatch(1);
        CountDownLatch allowMetadataCommit = new CountDownLatch(1);
        CountDownLatch cleanupStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> metadataUpdate = executor.submit(() -> transaction.executeWithoutResult(status -> {
                deviceManager.findOrCreateDevice(
                        systemUser(), "192.0.2.20", userAgent, clientDeviceId, platform);
                metadataUpdated.countDown();
                await(allowMetadataCommit);
            }));
            TestLatches.awaitProgress(metadataUpdated);

            Future<Boolean> invalidTokenCleanup = executor.submit(() -> {
                cleanupStarted.countDown();
                return transaction.execute(status -> deviceManager.clearPushTokenIfMatches(
                        SYSTEM_USER_ID, deviceId, "invalid-fcm-token"));
            });
            TestLatches.awaitProgress(cleanupStarted);
            assertThrows(TimeoutException.class,
                    () -> invalidTokenCleanup.get(250, TimeUnit.MILLISECONDS));

            allowMetadataCommit.countDown();
            TestLatches.getProgress(metadataUpdate);
            assertTrue(TestLatches.getProgress(invalidTokenCleanup));
            assertEquals(":true", jdbcTemplate.queryForObject("""
                    select coalesce(push_token, '') || ':' || (last_seen_at is not null)::text
                      from devices where id = ?
                    """, String.class, deviceId));
        } finally {
            allowMetadataCommit.countDown();
            executor.shutdownNow();
            jdbcTemplate.update("delete from devices where id = ?", deviceId);
        }
    }

    private void exerciseMetadataUpdateAgainstTokenMovement(String clientDeviceId) throws Exception {
        String token = "moved-token-" + UUID.randomUUID();
        String userAgent = "Mozilla/5.0 (Linux; Android 16; Pixel 10 Pro) AppleWebKit/537.36 "
                + "Chrome/140.0.0.0 Mobile Safari/537.36";
        UUID newOwnerId = UUID.randomUUID();
        UUID newOwnerDeviceId = UUID.randomUUID();
        String newOwnerClientDeviceId = UUID.randomUUID().toString();
        jdbcTemplate.update("insert into users (id, created_at, is_deleted, email, password, is_employer, "
                        + "email_verified, phone_verified, identity_verified) "
                        + "values (?, now(), false, ?, 'test', false, true, false, true)",
                newOwnerId, newOwnerId + "@example.com");
        jdbcTemplate.update("""
                insert into devices (id, created_at, is_deleted, user_id, type, name, client_device_id)
                values (?, now(), false, ?, 'ANDROID', 'new-owner-device', ?)
                """, newOwnerDeviceId, newOwnerId, newOwnerClientDeviceId);

        CountDownLatch metadataReadToken = new CountDownLatch(1);
        CountDownLatch allowMetadataTokenLock = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicBoolean pauseOnce = new java.util.concurrent.atomic.AtomicBoolean(true);
        com.exskylab.koala.core.privacy.PushTokenMutationLock coordinatedLock =
                new com.exskylab.koala.core.privacy.PushTokenMutationLock() {
                    @Override
                    public void lockMutations() {
                        tokenDeliveryLock.lockMutations();
                    }

                    @Override
                    public void lockUser(UUID userId) {
                        tokenDeliveryLock.lockUser(userId);
                    }

                    @Override
                    public void lockTokens(java.util.Collection<String> tokens) {
                        if (tokens.contains(token) && pauseOnce.compareAndSet(true, false)) {
                            metadataReadToken.countDown();
                            await(allowMetadataTokenLock);
                        }
                        tokenDeliveryLock.lockTokens(tokens);
                    }
                };
        DeviceManager metadataManager = deviceManager(systemUser(), coordinatedLock);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        UUID oldDeviceId;
        if (clientDeviceId == null) {
            Device oldDevice = transaction.execute(status -> metadataManager.findOrCreateDevice(
                    systemUser(), "192.0.2.30", userAgent, null, ClientPlatform.ANDROID));
            oldDeviceId = oldDevice.getId();
            jdbcTemplate.update("update devices set push_token = ? where id = ?", token, oldDeviceId);
        } else {
            oldDeviceId = UUID.randomUUID();
            jdbcTemplate.update("""
                    insert into devices (id, created_at, is_deleted, user_id, type, name, client_device_id, push_token)
                    values (?, now(), false, ?, 'ANDROID', 'old-owner-device', ?, ?)
                    """, oldDeviceId, SYSTEM_USER_ID, clientDeviceId, token);
        }
        User newOwner = new User();
        newOwner.setId(newOwnerId);
        DeviceManager transferManager = deviceManager(newOwner, tokenDeliveryLock);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<?> metadataUpdate = executor.submit(() -> transaction.executeWithoutResult(status ->
                    metadataManager.findOrCreateDevice(
                            systemUser(), "192.0.2.31", userAgent, clientDeviceId, ClientPlatform.ANDROID)));
            TestLatches.awaitProgress(metadataReadToken);

            transaction.executeWithoutResult(status -> transferManager.savePushToken(
                    "Dart/3.9 (dart:io)", newOwnerClientDeviceId, token));
            allowMetadataTokenLock.countDown();
            TestLatches.getProgress(metadataUpdate);

            assertEquals(":true", jdbcTemplate.queryForObject("""
                    select coalesce(push_token, '') || ':' || (last_seen_at is not null)::text
                      from devices where id = ?
                    """, String.class, oldDeviceId));
            assertEquals(token, jdbcTemplate.queryForObject(
                    "select push_token from devices where id = ?", String.class, newOwnerDeviceId));
        } finally {
            allowMetadataTokenLock.countDown();
            executor.shutdownNow();
            jdbcTemplate.update("delete from devices where id in (?, ?)", oldDeviceId, newOwnerDeviceId);
            jdbcTemplate.update("delete from users where id = ?", newOwnerId);
        }
    }

    private DeviceManager deviceManager() {
        return deviceManager(systemUser(), tokenDeliveryLock);
    }

    private DeviceManager deviceManager(User authenticatedUser,
                                        com.exskylab.koala.core.privacy.PushTokenMutationLock mutationLock) {
        SecurityService securityService = mock(SecurityService.class);
        when(securityService.getAuthenticatedUser()).thenReturn(authenticatedUser);
        when(securityService.isActiveUser(authenticatedUser.getId())).thenAnswer(ignored -> Boolean.TRUE.equals(
                jdbcTemplate.queryForObject(
                        "select exists(select 1 from users where id = ? and is_deleted = false)",
                        Boolean.class, authenticatedUser.getId())));
        return new DeviceManager(deviceDao, securityService, mutationLock);
    }

    private User systemUser() {
        User user = new User();
        user.setId(SYSTEM_USER_ID);
        return user;
    }

    private void await(CountDownLatch latch) {
        TestLatches.awaitHold(latch);
    }

    private void seed(UUID deviceId, UUID notificationId) {
        jdbcTemplate.update("""
                insert into devices (id, created_at, is_deleted, user_id, type, name, client_device_id, push_token)
                values (?, now(), false, ?, 'ANDROID', 'claim-test', ?, 'current-token')
                """, deviceId, SYSTEM_USER_ID, UUID.randomUUID().toString());
        jdbcTemplate.update("""
                insert into notifications (
                    id, created_at, is_deleted, recipient_id, channel, category,
                    destination_push_token, destination_device_id, subject, body,
                    status, push_attempt_count, push_data_payload, push_next_attempt_at)
                values (?, now(), false, ?, 'PUSH', 'ACCOUNT_SECURITY', ?, ?, 'title', 'body',
                    'QUEUED', 0, '{"route":"home"}', null)
                """, notificationId, SYSTEM_USER_ID, "snapshot-token", deviceId);
    }
}
