package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.DeviceService;
import com.exskylab.koala.business.abstracts.IysService;
import com.exskylab.koala.business.abstracts.NotificationPreferenceService;
import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.UnsubscribeTokenService;
import com.exskylab.koala.core.dtos.notification.request.SendPushToUserDto;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.core.privacy.AccountAnonymizer;
import com.exskylab.koala.core.properties.NotificationDispatchProperties;
import com.exskylab.koala.dataAccess.NotificationDao;
import com.exskylab.koala.entities.DispatchPriority;
import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.support.AbstractIntegrationTest;
import com.exskylab.koala.support.OverlappingWrites;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PushNotificationAnonymizationIT extends AbstractIntegrationTest {

    private static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired
    private AccountAnonymizer accountAnonymizer;

    @Autowired
    private DeviceService deviceService;

    @Autowired
    private NotificationDao notificationDao;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private PushTokenDeliveryLock tokenMutationLock;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void accountAnonymizationCannotBeFollowedByAPushFromAStaleDeviceSnapshot() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        seed(userId, deviceId, requestId);

        CountDownLatch devicesRead = new CountDownLatch(1);
        CountDownLatch allowNotificationWrite = new CountDownLatch(1);
        DeviceService coordinatedDevices = mock(DeviceService.class);
        when(coordinatedDevices.getDevicesByUserIdAndPushTokenNotNull(userId)).thenAnswer(ignored -> {
            List<com.exskylab.koala.entities.Device> snapshot =
                    deviceService.getDevicesByUserIdAndPushTokenNotNull(userId);
            devicesRead.countDown();
            await(allowNotificationWrite);
            return snapshot;
        });

        NotificationManager manager = manager(userId, coordinatedDevices);
        SendPushToUserDto request = request(userId);

        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> notification = executor.submit(() -> transaction.executeWithoutResult(status ->
                    manager.sendPushToUser(request, DispatchPriority.NORMAL, true)));
            assertTrue(devicesRead.await(5, TimeUnit.SECONDS));

            Future<?> anonymization = executor.submit(() -> accountAnonymizer.anonymize(userId, requestId));
            assertThrows(TimeoutException.class, () -> anonymization.get(250, TimeUnit.MILLISECONDS));

            allowNotificationWrite.countDown();
            notification.get(5, TimeUnit.SECONDS);
            anonymization.get(5, TimeUnit.SECONDS);

            assertEquals(0, jdbcTemplate.queryForObject("""
                    select count(*) from notifications
                     where recipient_id = ?
                       and (subject is not null or body is not null or template_parameters is not null
                         or push_data_payload is not null or destination_device_id is not null)
                    """, Integer.class, userId));
            assertEquals("true:", jdbcTemplate.queryForObject("""
                    select is_deleted::text || ':' || coalesce(push_token, '')
                      from devices where id = ?
                    """, String.class, deviceId));
        } finally {
            allowNotificationWrite.countDown();
            executor.shutdownNow();
            jdbcTemplate.update("delete from notifications where recipient_id = ?", userId);
            jdbcTemplate.update("delete from account_anonymization_audit where request_id = ?", requestId);
            jdbcTemplate.update("delete from account_deletion_requests where id = ?", requestId);
            jdbcTemplate.update("delete from devices where id = ?", deviceId);
            jdbcTemplate.update("delete from users where id = ?", userId);
        }
    }

    @Test
    void previousBinaryAnonymizationFencesANewPushWrite() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        seed(userId, deviceId, requestId);
        NotificationManager manager = manager(userId, deviceService);
        CountDownLatch oldScrubFinished = new CountDownLatch(1);
        CountDownLatch allowOldCommit = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> anonymization = executor.submit(() -> transaction.executeWithoutResult(status -> {
                jdbcTemplate.update("""
                        insert into account_anonymization_audit (id, request_id, step, occurred_at)
                        values (?, ?, 'STARTED', now())
                        """, UUID.randomUUID(), requestId);
                jdbcTemplate.update("""
                        update notifications
                           set destination_phone_number = null, destination_email = null,
                               destination_push_token = null, subject = null, body = null,
                               template_parameters = null, external_id = null, failure_reason = null
                         where recipient_id = ? or sender_id = ?
                        """, userId, userId);
                oldScrubFinished.countDown();
                await(allowOldCommit);
                jdbcTemplate.update("""
                        update devices
                           set name = null, client_device_id = null, push_token = null,
                               is_deleted = true, deleted_at = now()
                         where user_id = ?
                        """, userId);
                jdbcTemplate.update("update users set is_deleted = true, deleted_at = now() where id = ?", userId);
            }));
            assertTrue(oldScrubFinished.await(5, TimeUnit.SECONDS));

            Future<?> notification = executor.submit(() -> transaction.executeWithoutResult(status ->
                    manager.sendPushToUser(request(userId), DispatchPriority.NORMAL, true)));
            assertThrows(TimeoutException.class, () -> notification.get(250, TimeUnit.MILLISECONDS));

            allowOldCommit.countDown();
            anonymization.get(5, TimeUnit.SECONDS);
            notification.get(5, TimeUnit.SECONDS);

            assertEquals(0, jdbcTemplate.queryForObject("""
                    select count(*) from notifications
                     where recipient_id = ?
                       and (subject is not null or body is not null or template_parameters is not null
                         or push_data_payload is not null or destination_device_id is not null)
                    """, Integer.class, userId));
        } finally {
            allowOldCommit.countDown();
            executor.shutdownNow();
            jdbcTemplate.update("delete from notifications where recipient_id = ?", userId);
            jdbcTemplate.update("delete from account_anonymization_audit where request_id = ?", requestId);
            jdbcTemplate.update("delete from account_deletion_requests where id = ?", requestId);
            jdbcTemplate.update("delete from devices where id = ?", deviceId);
            jdbcTemplate.update("delete from users where id = ?", userId);
        }
    }

    @Test
    void pausedLargeFanOutDoesNotBlockAnUnrelatedAnonymization() throws Exception {
        List<UUID> recipients = IntStream.range(0, 512).mapToObj(ignored -> UUID.randomUUID()).toList();
        UUID unrelated = UUID.randomUUID();
        UUID unrelatedDevice = UUID.randomUUID();
        UUID unrelatedRequest = UUID.randomUUID();
        seed(unrelated, unrelatedDevice, unrelatedRequest);
        CountDownLatch fanOutLocked = new CountDownLatch(1);
        CountDownLatch releaseFanOut = new CountDownLatch(1);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> fanOut = executor.submit(() -> transaction.executeWithoutResult(status -> {
                notificationService.preparePushRecipients(recipients);
                fanOutLocked.countDown();
                await(releaseFanOut);
            }));
            assertTrue(fanOutLocked.await(5, TimeUnit.SECONDS));

            Future<?> anonymization = executor.submit(() -> accountAnonymizer.anonymize(
                    unrelated, unrelatedRequest));

            anonymization.get(1, TimeUnit.SECONDS);
            assertTrue(Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                    "select is_deleted from users where id = ?", Boolean.class, unrelated)));
            releaseFanOut.countDown();
            fanOut.get(5, TimeUnit.SECONDS);
        } finally {
            releaseFanOut.countDown();
            executor.shutdownNow();
            jdbcTemplate.update("delete from account_anonymization_audit where request_id = ?", unrelatedRequest);
            jdbcTemplate.update("delete from account_deletion_requests where id = ?", unrelatedRequest);
            jdbcTemplate.update("delete from devices where id = ?", unrelatedDevice);
            jdbcTemplate.update("delete from users where id = ?", unrelated);
        }
    }

    @Test
    void reverseOrderRecipientFanOutsBothCommit() throws Exception {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000101");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000102");
        OverlappingWrites overlap = new OverlappingWrites();

        List<String> outcomes = overlap.outcomesOf(
                () -> prepareRecipients(overlap, "FIRST", List.of(first, second)),
                () -> prepareRecipients(overlap, "SECOND", List.of(second, first)));

        assertEquals(List.of("FIRST", "SECOND"), outcomes.stream().sorted().toList());
    }

    private String prepareRecipients(OverlappingWrites overlap, String result, List<UUID> recipients) {
        return new TransactionTemplate(transactionManager).execute(status -> {
            overlap.meet();
            notificationService.preparePushRecipients(recipients);
            return result;
        });
    }

    private NotificationManager manager(UUID userId, DeviceService devices) {
        SecurityService securityService = mock(SecurityService.class);
        User systemUser = new User();
        systemUser.setId(SYSTEM_USER_ID);
        when(securityService.getSystemUser()).thenReturn(systemUser);
        when(securityService.isActiveUser(userId)).thenAnswer(ignored -> Boolean.TRUE.equals(
                jdbcTemplate.queryForObject(
                        "select exists(select 1 from users where id = ? and is_deleted = false)",
                        Boolean.class, userId)));
        NotificationPreferenceService preferences = mock(NotificationPreferenceService.class);
        when(preferences.isAllowed(any(), any(), any())).thenReturn(true);
        return new NotificationManager(
                notificationDao, devices, objectMapper, securityService,
                new PageableSanitizer(), preferences, mock(UnsubscribeTokenService.class),
                mock(IysService.class), tokenMutationLock, new NotificationDispatchProperties());
    }

    private SendPushToUserDto request(UUID userId) {
        User recipient = new User();
        recipient.setId(userId);
        SendPushToUserDto request = new SendPushToUserDto();
        request.setRecipient(recipient);
        request.setCategory(NotificationCategory.ACCOUNT_SECURITY);
        request.setTitle("Private title");
        request.setBody("Private body");
        request.setDataPayload(java.util.Map.of("route", "private-route"));
        return request;
    }

    private void seed(UUID userId, UUID deviceId, UUID requestId) {
        jdbcTemplate.update("""
                insert into users
                    (id, first_name, username, email, email_verified, phone_verified,
                     identity_verified, is_employer, created_at, is_deleted)
                values (?, 'Delete', ?, ?, false, false, false, false, now(), false)
                """, userId, "delete-" + userId, userId + "@example.test");
        jdbcTemplate.update("""
                insert into devices
                    (id, created_at, is_deleted, user_id, type, name, client_device_id, push_token)
                values (?, now(), false, ?, 'ANDROID', 'delete-device', ?, ?)
                """, deviceId, userId, UUID.randomUUID().toString(), "delete-token-" + userId);
        jdbcTemplate.update("""
                insert into account_deletion_requests
                    (id, user_id, subject_id, status, requested_at, created_at, is_deleted)
                values (?, ?, ?, 'REQUESTED', now(), now(), false)
                """, requestId, userId, userId);
    }

    private void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for notification race coordination");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while coordinating notification race", exception);
        }
    }
}
