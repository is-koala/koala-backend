package com.exskylab.koala.dataAccess;

import com.exskylab.koala.support.AbstractIntegrationTest;
import com.exskylab.koala.support.OverlappingWrites;
import com.exskylab.koala.business.concretes.PushTokenDeliveryLock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PushDeliverySchemaIT extends AbstractIntegrationTest {

    private static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private PushTokenDeliveryLock tokenDeliveryLock;

    @Test
    void pushDeliveryColumnsCarryTheFullTokenAndDurableAttemptState() {
        assertEquals(512, maximumLength("notifications", "destination_push_token"));
        assertEquals("NO", nullable("notifications", "push_attempt_count"));
        assertEquals("integer", dataType("notifications", "push_attempt_count"));
        assertEquals("YES", nullable("notifications", "destination_device_id"));
        assertEquals("text", dataType("notifications", "push_data_payload"));
        assertEquals("timestamp with time zone", dataType("notifications", "push_next_attempt_at"));
        assertEquals("timestamp with time zone", dataType("notifications", "push_processing_started_at"));
        assertEquals("uuid", dataType("notifications", "push_processing_claim_id"));
    }

    @Test
    void databaseScrubsRawPushTokenSnapshotsWrittenByAnOlderBinary() {
        UUID notificationId = UUID.randomUUID();
        try {
            jdbcTemplate.update("""
                    insert into notifications (
                        id, created_at, is_deleted, recipient_id, channel, category,
                        destination_push_token, subject, body, status, push_attempt_count)
                    values (?, now(), false, ?, 'PUSH', 'ACCOUNT_SECURITY', ?, 'title', 'body', 'QUEUED', 0)
                    """, notificationId, SYSTEM_USER_ID, "legacy-insert-token");

            assertNull(jdbcTemplate.queryForObject(
                    "select destination_push_token from notifications where id = ?",
                    String.class, notificationId));

            jdbcTemplate.update("update notifications set destination_push_token = ? where id = ?",
                    "legacy-update-token", notificationId);
            assertNull(jdbcTemplate.queryForObject(
                    "select destination_push_token from notifications where id = ?",
                    String.class, notificationId));
        } finally {
            jdbcTemplate.update("delete from notifications where id = ?", notificationId);
        }
    }

    @Test
    void duePushDeliveryLookupHasAPartialIndex() {
        String definition = jdbcTemplate.queryForObject(
                "select indexdef from pg_indexes where indexname = 'idx_notifications_due_push_delivery'",
                String.class);

        assertTrue(definition.contains("push_next_attempt_at"));
        assertTrue(definition.contains("channel") && definition.contains("PUSH"));
        assertTrue(definition.contains("status") && definition.contains("QUEUED"));
        assertTrue(definition.contains("is_deleted = false"));
        assertTrue(definition.contains("push_attempt_count > 0"));
        assertTrue(definition.contains("failure_reason")
                && definition.contains("TOKEN_SERIALIZATION_BUSY"));
    }

    @Test
    void stalePushDeliveryLookupHasAMatchingPartialIndex() {
        String definition = jdbcTemplate.queryForObject(
                "select indexdef from pg_indexes where indexname = 'idx_notifications_stale_push_delivery'",
                String.class);

        assertTrue(definition.contains("push_processing_started_at"));
        assertTrue(definition.contains("channel") && definition.contains("PUSH"));
        assertTrue(definition.contains("status") && definition.contains("PROCESSING"));
        assertTrue(definition.contains("is_deleted = false"));
    }

    @Test
    void activePushTokensAreGloballyUnique() {
        String definition = jdbcTemplate.queryForObject(
                "select indexdef from pg_indexes where indexname = 'uk_devices_push_token_active'",
                String.class);

        assertTrue(definition.contains("UNIQUE INDEX"));
        assertTrue(definition.contains("push_token"));
        assertTrue(definition.contains("is_deleted = false"));
        assertTrue(definition.contains("push_token IS NOT NULL"));
    }

    @Test
    void twoConcurrentRegistrationsLeaveTheTokenWithOneOwner() throws Exception {
        UUID first = seedDevice();
        UUID second = seedDevice();
        OverlappingWrites overlap = new OverlappingWrites();

        try {
            List<String> outcomes = overlap.outcomesOf(
                    () -> register(first, overlap),
                    () -> register(second, overlap));

            assertEquals(List.of("SUCCESS", "SUCCESS"), outcomes.stream().sorted().toList());
            assertEquals(1, jdbcTemplate.queryForObject(
                    "select count(*) from devices where push_token = 'same-fcm-token' and is_deleted = false",
                    Integer.class));
        } finally {
            jdbcTemplate.update("delete from devices where id in (?, ?)", first, second);
        }
    }

    @Test
    void previousBinaryDestinationFirstTransferWorksDuringRollingDeployment() {
        UUID previousOwner = seedDevice();
        UUID destination = seedDevice();
        String token = "rolling-transfer-" + UUID.randomUUID();
        jdbcTemplate.update("update devices set push_token = ? where id = ?", token, previousOwner);

        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                previousBinaryUpdate(destination, "concurrency-test", token, false);
                previousBinaryUpdate(previousOwner, "concurrency-test", null, false);
            });

            assertEquals(destination, jdbcTemplate.queryForObject(
                    "select id from devices where push_token = ? and is_deleted = false",
                    UUID.class, token));
        } finally {
            jdbcTemplate.update("delete from devices where id in (?, ?)", previousOwner, destination);
        }
    }

    @Test
    void previousBinaryMetadataWriteCannotRestoreATokenAfterItMoves() {
        UUID previousOwner = seedDevice();
        UUID currentOwner = seedDevice();
        String token = "rolling-stale-" + UUID.randomUUID();
        jdbcTemplate.update("update devices set push_token = ? where id = ?", token, previousOwner);
        jdbcTemplate.update("update devices set push_token = null where id = ?", previousOwner);
        jdbcTemplate.update("update devices set push_token = ? where id = ?", token, currentOwner);

        try {
            previousBinaryUpdate(previousOwner, "concurrency-test", token, true);

            assertEquals(currentOwner, jdbcTemplate.queryForObject(
                    "select id from devices where push_token = ? and is_deleted = false",
                    UUID.class, token));
            assertEquals("concurrency-test:", jdbcTemplate.queryForObject("""
                    select name || ':' || coalesce(push_token, '') from devices where id = ?
                    """, String.class, previousOwner));
        } finally {
            jdbcTemplate.update("delete from devices where id in (?, ?)", previousOwner, currentOwner);
        }
    }

    @Test
    void previousBinaryMetadataWriteCannotClearACurrentToken() {
        UUID deviceId = seedDevice();
        String token = "rolling-metadata-clear-" + UUID.randomUUID();
        jdbcTemplate.update("update devices set push_token = ? where id = ?", token, deviceId);

        try {
            previousBinaryUpdate(deviceId, "concurrency-test", null, true);

            assertEquals(token, jdbcTemplate.queryForObject(
                    "select push_token from devices where id = ?", String.class, deviceId));
        } finally {
            jdbcTemplate.update("delete from devices where id = ?", deviceId);
        }
    }

    @Test
    void previousBinaryTokenOnlyClearWaitsForTheOldTokenDelivery() throws Exception {
        UUID deviceId = seedDevice();
        String token = "rolling-clear-" + UUID.randomUUID();
        jdbcTemplate.update("update devices set push_token = ? where id = ?", token, deviceId);

        try {
            assertTokenChangeWaitsForDelivery(deviceId, deviceId, token, null);
            assertNull(jdbcTemplate.queryForObject(
                    "select push_token from devices where id = ?", String.class, deviceId));
        } finally {
            jdbcTemplate.update("delete from devices where id = ?", deviceId);
        }
    }

    @Test
    void previousBinaryRotationWaitsForTheNewTokenDelivery() throws Exception {
        UUID previousOwner = seedDevice();
        UUID rotatingDevice = seedDevice();
        String previousToken = "rolling-new-" + UUID.randomUUID();
        String oldToken = "rolling-old-" + UUID.randomUUID();
        jdbcTemplate.update("update devices set push_token = ? where id = ?", previousToken, previousOwner);
        jdbcTemplate.update("update devices set push_token = ? where id = ?", oldToken, rotatingDevice);

        try {
            assertTokenChangeWaitsForDelivery(rotatingDevice, previousOwner, previousToken, previousToken);
            assertNull(jdbcTemplate.queryForObject(
                    "select push_token from devices where id = ?", String.class, previousOwner));
            assertEquals(previousToken, jdbcTemplate.queryForObject(
                    "select push_token from devices where id = ?", String.class, rotatingDevice));
        } finally {
            jdbcTemplate.update("delete from devices where id in (?, ?)", previousOwner, rotatingDevice);
        }
    }

    @Test
    void previousBinaryRotationWaitsForTheOldTokenDelivery() throws Exception {
        UUID rotatingDevice = seedDevice();
        String oldToken = "rolling-old-" + UUID.randomUUID();
        String newToken = "rolling-new-" + UUID.randomUUID();
        jdbcTemplate.update("update devices set push_token = ? where id = ?", oldToken, rotatingDevice);

        try {
            assertTokenChangeWaitsForDelivery(rotatingDevice, rotatingDevice, oldToken, newToken);
            assertEquals(newToken, jdbcTemplate.queryForObject(
                    "select push_token from devices where id = ?", String.class, rotatingDevice));
        } finally {
            jdbcTemplate.update("delete from devices where id = ?", rotatingDevice);
        }
    }

    private UUID seedDevice() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into devices (id, created_at, is_deleted, user_id, type, name, client_device_id)
                values (?, now(), false, ?, 'ANDROID', 'concurrency-test', ?)
                """, id, SYSTEM_USER_ID, UUID.randomUUID().toString());
        return id;
    }

    private String register(UUID deviceId, OverlappingWrites overlap) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return transaction.execute(status -> {
            overlap.meet();
            try {
                jdbcTemplate.update("update devices set push_token = 'same-fcm-token' where id = ?", deviceId);
                return "SUCCESS";
            } catch (DataIntegrityViolationException violation) {
                status.setRollbackOnly();
                return "CONFLICT";
            }
        });
    }

    private void previousBinaryUpdate(UUID deviceId, String name, String token, boolean refreshLastSeen) {
        jdbcTemplate.update("""
                update devices
                   set user_id = user_id, type = type, name = ?, client_device_id = client_device_id,
                       push_token = ?, first_seen_at = first_seen_at,
                       last_seen_at = case when ? then now() else last_seen_at end,
                       updated_at = now(), updated_by_id = updated_by_id, is_deleted = is_deleted,
                       deleted_at = deleted_at, deleted_by_id = deleted_by_id
                 where id = ?
                """, name, token, refreshLastSeen, deviceId);
    }

    private void assertTokenChangeWaitsForDelivery(UUID changedDeviceId, UUID lockedDeviceId,
                                                   String lockedToken, String replacementToken) throws Exception {
        CountDownLatch deliveryStarted = new CountDownLatch(1);
        CountDownLatch releaseDelivery = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> delivery = executor.submit(() -> {
                tokenDeliveryLock.executeIfCurrent(
                        SYSTEM_USER_ID, lockedDeviceId, lockedToken, () -> {
                            deliveryStarted.countDown();
                            await(releaseDelivery);
                            return "sent";
                        });
            });
            assertTrue(deliveryStarted.await(5, TimeUnit.SECONDS));

            Future<?> tokenChange = executor.submit(() ->
                    new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                            jdbcTemplate.update("update devices set push_token = ? where id = ?",
                                    replacementToken, changedDeviceId)));
            assertThrows(TimeoutException.class, () -> tokenChange.get(250, TimeUnit.MILLISECONDS));

            releaseDelivery.countDown();
            delivery.get(5, TimeUnit.SECONDS);
            tokenChange.get(5, TimeUnit.SECONDS);
        } finally {
            releaseDelivery.countDown();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for token delivery");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for token delivery", exception);
        }
    }

    private Integer maximumLength(String table, String column) {
        return jdbcTemplate.queryForObject("""
                select character_maximum_length from information_schema.columns
                where table_schema = 'public' and table_name = ? and column_name = ?
                """, Integer.class, table, column);
    }

    private String nullable(String table, String column) {
        return jdbcTemplate.queryForObject("""
                select is_nullable from information_schema.columns
                where table_schema = 'public' and table_name = ? and column_name = ?
                """, String.class, table, column);
    }

    private String dataType(String table, String column) {
        return jdbcTemplate.queryForObject("""
                select data_type from information_schema.columns
                where table_schema = 'public' and table_name = ? and column_name = ?
                """, String.class, table, column);
    }
}
