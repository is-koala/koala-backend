package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.NotificationDispatchService;
import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.core.exceptions.ExternalServiceException;
import com.exskylab.koala.core.consumers.EmailConsumer;
import com.exskylab.koala.core.logging.LogContext;
import com.exskylab.koala.core.producers.notification.EmailTaskDto;
import com.exskylab.koala.core.producers.notification.NotificationProducerService;
import com.exskylab.koala.core.producers.notification.PushTaskDto;
import com.exskylab.koala.core.producers.notification.SmsTaskDto;
import com.exskylab.koala.dataAccess.NotificationDao;
import com.exskylab.koala.entities.DispatchPriority;
import com.exskylab.koala.entities.Notification;
import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.NotificationChannel;
import com.exskylab.koala.entities.NotificationStatus;
import com.exskylab.koala.support.AbstractIntegrationTest;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@TestPropertySource(properties = {
        "notification.dispatch.enabled=true",
        "notification.dispatch.max-attempts=2",
        "notification.dispatch.initial-backoff=PT1M",
        "notification.dispatch.max-backoff=PT10M",
        "notification.dispatch.claim-timeout=PT5M",
        "notification.dispatch.delivery-claim-timeout=PT5M",
        "notification.dispatch.legacy-grace-period=PT10M",
        "notification.dispatch.batch-size=20",
        "email.delivery.mode=UNRESTRICTED",
        "management.health.mail.enabled=false"
})
class NotificationDispatchManagerIT extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-09T12:00:00Z");

    @Autowired private NotificationDispatchService dispatchService;
    @Autowired private NotificationService notificationService;
    @Autowired private EmailConsumer emailConsumer;
    @Autowired private NotificationDao notificationDao;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private MeterRegistry meterRegistry;
    @MockitoBean private NotificationProducerService producer;
    @MockitoBean private JavaMailSender mailSender;
    @MockitoBean private Clock clock;
    private final List<UUID> notificationIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
    }

    @AfterEach
    void cleanCommittedFixtures() {
        LogContext.clear();
        for (UUID notificationId : notificationIds) {
            jdbcTemplate.update("delete from notifications where id = ?", notificationId);
        }
        notificationIds.clear();
    }

    @Test
    void eachPublishRestoresItsPersistedOriginInsteadOfTheSchedulerBatchContext() {
        Notification task = queuedEmail();
        task.setDispatchRequestId("origin-request");
        task.setDispatchUserId("origin-user");
        task.setDispatchSessionId("origin-session");
        task.setDispatchDeviceId("origin-device");
        notificationDao.saveAndFlush(task);
        AtomicReference<Map<String, String>> publishedContext = new AtomicReference<>();
        doAnswer(invocation -> {
            publishedContext.set(LogContext.carried());
            return null;
        }).when(producer).sendEmailTask(any());
        LogContext.adopt(Map.of(LogContext.REQUEST_ID, "scheduler-request"));

        assertTrue(dispatchService.runTask(task.getId()));

        assertEquals(Map.of(
                LogContext.REQUEST_ID, "origin-request",
                LogContext.USER_ID, "origin-user",
                LogContext.SESSION_ID, "origin-session",
                LogContext.DEVICE_ID, "origin-device"), publishedContext.get());
        assertEquals(Map.of(LogContext.REQUEST_ID, "scheduler-request"), LogContext.carried());
    }

    @Test
    void legacyRowsReceiveDifferentRequestIdsInsteadOfSharingTheSchedulerRequestId() {
        Notification first = queuedEmail();
        Notification second = queuedEmail();
        Notification storedFirst = notificationDao.findById(first.getId()).orElseThrow();
        Notification storedSecond = notificationDao.findById(second.getId()).orElseThrow();
        assertNull(storedFirst.getDispatchRequestId());
        assertNull(storedFirst.getDispatchUserId());
        assertNull(storedFirst.getDispatchSessionId());
        assertNull(storedFirst.getDispatchDeviceId());
        assertNull(storedSecond.getDispatchRequestId());

        List<String> publishedRequestIds = new ArrayList<>();
        doAnswer(invocation -> {
            publishedRequestIds.add(LogContext.requestId());
            return null;
        }).when(producer).sendEmailTask(any());
        LogContext.adopt(Map.of(LogContext.REQUEST_ID, "scheduler-request"));

        assertTrue(dispatchService.runTask(first.getId()));
        assertTrue(dispatchService.runTask(second.getId()));

        assertEquals(2, publishedRequestIds.size());
        assertNotNull(publishedRequestIds.get(0));
        assertNotNull(publishedRequestIds.get(1));
        assertNotEquals("scheduler-request", publishedRequestIds.get(0));
        assertNotEquals("scheduler-request", publishedRequestIds.get(1));
        assertNotEquals(publishedRequestIds.get(0), publishedRequestIds.get(1));
        assertEquals(Map.of(LogContext.REQUEST_ID, "scheduler-request"), LogContext.carried());
    }

    @Test
    void aDispatchSweepPublishesExactBacklogAgeAndTerminalBrokerFailureMetrics() {
        Notification queued = queuedEmail();
        queued.setNextDispatchAt(NOW.plus(Duration.ofHours(1)));
        notificationDao.saveAndFlush(queued);
        jdbcTemplate.update("update notifications set created_at = ? where id = ?",
                Timestamp.from(NOW.minusSeconds(45)), queued.getId());
        Notification terminal = queuedEmail();
        terminal.setStatus(NotificationStatus.FAILED);
        terminal.setFailureReason("BROKER_PUBLISH_EXHAUSTED");
        notificationDao.saveAndFlush(terminal);

        dispatchService.findDueTaskIds();

        assertEquals(1, meterRegistry.get("koala.notification.dispatch.backlog").gauge().value());
        assertEquals(45, meterRegistry.get("koala.notification.dispatch.oldest.age").gauge().value());
        assertEquals(1,
                meterRegistry.get("koala.notification.dispatch.terminal.broker.failures").gauge().value());
    }

    @Test
    void aBrokerFailureBacksOffWithJitterAndEventuallyBecomesASanitizedTerminalFailure() {
        Notification task = queuedEmail();
        doThrow(new ExternalServiceException("amqp://user:secret@broker/internal"))
                .when(producer).sendEmailTask(any());

        assertFalse(dispatchService.runTask(task.getId()));

        Notification retry = notificationDao.findById(task.getId()).orElseThrow();
        assertEquals(1, retry.getDispatchAttempts());
        assertEquals(NotificationStatus.QUEUED, retry.getStatus());
        assertTrue(!retry.getNextDispatchAt().isBefore(NOW.plusSeconds(30)));
        assertTrue(!retry.getNextDispatchAt().isAfter(NOW.plusSeconds(60)));
        assertNull(retry.getDispatchClaimId());

        retry.setNextDispatchAt(NOW);
        notificationDao.saveAndFlush(retry);

        assertFalse(dispatchService.runTask(task.getId()));

        Notification exhausted = notificationDao.findById(task.getId()).orElseThrow();
        assertEquals(2, exhausted.getDispatchAttempts());
        assertEquals(NotificationStatus.FAILED, exhausted.getStatus());
        assertEquals("BROKER_PUBLISH_EXHAUSTED", exhausted.getFailureReason());
        assertFalse(exhausted.getFailureReason().contains("secret"));
        assertNull(exhausted.getDispatchClaimId());
        verify(producer, times(2)).sendEmailTask(any());
    }

    @Test
    void aConfirmedPublishWhoseCompletionCannotBePersistedRemainsReplayableWithoutSpendingFailureBudget() {
        Notification task = queuedEmail();
        jdbcTemplate.execute("""
                create function reject_notification_dispatch_completion() returns trigger
                language plpgsql as $$
                begin
                    if old.published_at is null and new.published_at is not null then
                        raise exception 'dispatch completion unavailable';
                    end if;
                    return new;
                end
                $$
                """);
        jdbcTemplate.execute("""
                create trigger reject_notification_dispatch_completion
                before update on notifications
                for each row when (new.id = '%s'::uuid)
                execute function reject_notification_dispatch_completion()
                """.formatted(task.getId()));

        try {
            assertThrows(RuntimeException.class, () -> dispatchService.runTask(task.getId()));
        } finally {
            jdbcTemplate.execute("drop trigger reject_notification_dispatch_completion on notifications");
            jdbcTemplate.execute("drop function reject_notification_dispatch_completion()");
        }

        Notification awaitingReconciliation = notificationDao.findById(task.getId()).orElseThrow();
        assertEquals(NotificationStatus.QUEUED, awaitingReconciliation.getStatus());
        assertNull(awaitingReconciliation.getPublishedAt());
        assertEquals(1, awaitingReconciliation.getDispatchAttempts());
        assertTrue(awaitingReconciliation.getDispatchClaimId() != null);
        assertNull(awaitingReconciliation.getFailureReason());

        jdbcTemplate.update("update notifications set dispatch_claimed_at = ? where id = ?",
                Timestamp.from(NOW.minus(Duration.ofMinutes(6))), task.getId());

        assertEquals(List.of(task.getId()), dispatchService.findDueTaskIds());
        assertTrue(dispatchService.runTask(task.getId()));

        Notification replayed = notificationDao.findById(task.getId()).orElseThrow();
        assertEquals(NotificationStatus.QUEUED, replayed.getStatus());
        assertEquals(NOW, replayed.getPublishedAt());
        assertEquals(1, replayed.getDispatchAttempts());
        assertNull(replayed.getDispatchClaimId());
        assertNull(replayed.getFailureReason());
        verify(producer, times(2)).sendEmailTask(any());
    }

    @Test
    void twoWorkersCannotPublishTheSameNotification() throws Exception {
        Notification task = queuedEmail();
        CountDownLatch enteredBroker = new CountDownLatch(1);
        CountDownLatch releaseBroker = new CountDownLatch(1);
        doAnswer(invocation -> {
            enteredBroker.countDown();
            if (!releaseBroker.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("broker release timed out");
            }
            return null;
        }).when(producer).sendEmailTask(any());

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> dispatchService.runTask(task.getId()));
            assertTrue(enteredBroker.await(5, TimeUnit.SECONDS));
            var second = executor.submit(() -> dispatchService.runTask(task.getId()));
            assertFalse(second.get(5, TimeUnit.SECONDS));
            releaseBroker.countDown();
            assertTrue(first.get(5, TimeUnit.SECONDS));
        }

        Notification published = notificationDao.findById(task.getId()).orElseThrow();
        assertEquals(1, published.getDispatchAttempts());
        assertEquals(NOW, published.getPublishedAt());
        assertNull(published.getDispatchClaimId());
        verify(producer, times(1)).sendEmailTask(any());
    }

    @Test
    void stalePublisherClaimsAreRecoveredButTheirOldTokensCannotFinalizeOrRequeueANewerAttempt() {
        Notification task = queuedEmail();
        UUID oldClaim = UUID.randomUUID();
        task.setDispatchAttempts(1);
        task.setDispatchClaimId(oldClaim);
        task.setDispatchClaimedAt(NOW.minus(Duration.ofMinutes(6)));
        notificationDao.saveAndFlush(task);

        assertEquals(List.of(task.getId()), dispatchService.findDueTaskIds());
        assertTrue(dispatchService.runTask(task.getId()));

        Integer staleCompletion = new TransactionTemplate(transactionManager).execute(status ->
                notificationDao.completeDispatch(task.getId(), oldClaim, NOW.plusSeconds(1)));
        Integer staleFailure = new TransactionTemplate(transactionManager).execute(status ->
                notificationDao.failClaimedDispatch(task.getId(), oldClaim, 2, NOW.plusSeconds(30),
                        NOW.plusSeconds(1), "BROKER_PUBLISH_RETRY_SCHEDULED", "BROKER_PUBLISH_EXHAUSTED"));
        assertEquals(0, staleCompletion);
        assertEquals(0, staleFailure);
        Notification published = notificationDao.findById(task.getId()).orElseThrow();
        assertEquals(1, published.getDispatchAttempts());
        assertEquals(NOW, published.getPublishedAt());
    }

    @Test
    void concurrentAndRedeliveredMessagesPassOnlyOneDeliveryClaim() throws Exception {
        Notification notification = queuedEmail();
        CyclicBarrier start = new CyclicBarrier(2);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                try {
                    return notificationService.claimForDelivery(notification.getId(), NotificationChannel.EMAIL)
                            .isPresent();
                } catch (IllegalStateException exception) {
                    return false;
                }
            });
            var second = executor.submit(() -> {
                start.await(5, TimeUnit.SECONDS);
                try {
                    return notificationService.claimForDelivery(notification.getId(), NotificationChannel.EMAIL)
                            .isPresent();
                } catch (IllegalStateException exception) {
                    return false;
                }
            });

            int claims = (first.get(5, TimeUnit.SECONDS) ? 1 : 0) + (second.get(5, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, claims);
        }

        Notification claimed = notificationDao.findById(notification.getId()).orElseThrow();
        assertEquals(NotificationStatus.PROCESSING, claimed.getStatus());
        assertThrows(IllegalStateException.class,
                () -> notificationService.claimForDelivery(notification.getId(), NotificationChannel.EMAIL));
        notificationService.completeDelivery(notification.getId(), NotificationChannel.EMAIL,
                claimed.getDeliveryClaimId(), null);
        assertTrue(notificationService.claimForDelivery(notification.getId(), NotificationChannel.EMAIL).isEmpty());
    }

    @Test
    void staleDeliveryLeaseCanBeTakenOverButItsOldTokenCannotFinalize() {
        Notification notification = queuedEmail();
        UUID oldClaimId = UUID.randomUUID();
        notification.setStatus(NotificationStatus.PROCESSING);
        notification.setDeliveryClaimId(oldClaimId);
        notification.setDeliveryClaimedAt(Instant.now().minus(Duration.ofMinutes(6)));
        notificationDao.saveAndFlush(notification);

        UUID newClaimId = notificationService.claimForDelivery(notification.getId(), NotificationChannel.EMAIL)
                .orElseThrow();

        assertFalse(oldClaimId.equals(newClaimId));
        Integer oldCompletion = new TransactionTemplate(transactionManager).execute(status ->
                notificationDao.completeDelivery(notification.getId(), NotificationChannel.EMAIL.name(),
                        oldClaimId, null, Instant.now()));
        assertEquals(0, oldCompletion);
        notificationService.completeDelivery(notification.getId(), NotificationChannel.EMAIL, newClaimId, null);
        Notification completed = notificationDao.findById(notification.getId()).orElseThrow();
        assertEquals(NotificationStatus.SENT, completed.getStatus());
        assertNull(completed.getDeliveryClaimId());
    }

    @Test
    void deliveryCompletionCannotOverwriteConcurrentPublisherFencing() throws Exception {
        Notification task = queuedEmail();
        String originalPayload = task.getDispatchPayload();
        EmailTaskDto emailTask = objectMapper.readValue(originalPayload, EmailTaskDto.class);
        CountDownLatch enteredBroker = new CountDownLatch(1);
        CountDownLatch releaseBroker = new CountDownLatch(1);
        CountDownLatch enteredSmtp = new CountDownLatch(1);
        CountDownLatch releaseSmtp = new CountDownLatch(1);
        doAnswer(invocation -> {
            enteredBroker.countDown();
            if (!releaseBroker.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("broker release timed out");
            }
            return null;
        }).when(producer).sendEmailTask(any());
        when(mailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doAnswer(invocation -> {
            enteredSmtp.countDown();
            if (!releaseSmtp.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("smtp release timed out");
            }
            return null;
        }).when(mailSender).send(any(MimeMessage.class));

        try (var executor = Executors.newFixedThreadPool(2)) {
            var publisher = executor.submit(() -> dispatchService.runTask(task.getId()));
            assertTrue(enteredBroker.await(5, TimeUnit.SECONDS));
            var consumer = executor.submit(() -> emailConsumer.handleEmailTask(emailTask));
            assertTrue(enteredSmtp.await(5, TimeUnit.SECONDS));
            releaseBroker.countDown();
            assertTrue(publisher.get(5, TimeUnit.SECONDS));
            releaseSmtp.countDown();
            consumer.get(5, TimeUnit.SECONDS);
        } finally {
            releaseBroker.countDown();
            releaseSmtp.countDown();
        }

        Notification completed = notificationDao.findById(task.getId()).orElseThrow();
        assertEquals(NotificationStatus.SENT, completed.getStatus());
        assertEquals(NOW, completed.getPublishedAt());
        assertEquals(1, completed.getDispatchAttempts());
        assertNull(completed.getDispatchClaimId());
        assertNull(completed.getDispatchClaimedAt());
        assertEquals(originalPayload, completed.getDispatchPayload());
        assertEquals(DispatchPriority.NORMAL, completed.getDispatchPriority());
        assertEquals(NOW, completed.getNextDispatchAt());
    }

    @Test
    void anOldQueuedRowWithoutAReplayablePayloadIsTerminalizedWithoutGuessing() {
        Notification legacy = new Notification();
        legacy.setChannel(NotificationChannel.EMAIL);
        legacy.setCategory(NotificationCategory.ACCOUNT_SECURITY);
        legacy.setStatus(NotificationStatus.QUEUED);
        legacy.setDestinationEmail("legacy@koala.test");
        legacy = notificationDao.saveAndFlush(legacy);
        notificationIds.add(legacy.getId());
        jdbcTemplate.update("update notifications set created_at = ? where id = ?",
                Timestamp.from(NOW.minus(Duration.ofHours(1))), legacy.getId());

        assertTrue(dispatchService.findDueTaskIds().isEmpty());

        Notification recovered = notificationDao.findById(legacy.getId()).orElseThrow();
        assertEquals(NotificationStatus.FAILED, recovered.getStatus());
        assertEquals("LEGACY_DISPATCH_PAYLOAD_UNAVAILABLE", recovered.getFailureReason());
        verify(producer, times(0)).sendEmailTask(any());
    }

    @Test
    void highPriorityIsClaimedBeforeNormalWithoutChangingDurability() {
        Notification normal = queuedEmail(DispatchPriority.NORMAL);
        Notification high = queuedEmail(DispatchPriority.HIGH);

        assertEquals(List.of(high.getId(), normal.getId()), dispatchService.findDueTaskIds());
    }

    @Test
    void exactEmailPayloadSurvivesPersistenceAndRoutesEveryFieldToTheEmailProducer() {
        Notification notification = queuedNotification(NotificationChannel.EMAIL);
        EmailTaskDto expected = new EmailTaskDto(notification.getId(), "email-route@koala.test",
                "Exact subject", "exact-email-template", Map.of("firstName", "Ada", "locale", "tr"));
        storePayload(notification, expected);

        assertTrue(dispatchService.runTask(notification.getId()));

        verify(producer).sendEmailTask(expected);
    }

    @Test
    void exactSmsPayloadSurvivesPersistenceAndRoutesEveryFieldToTheSmsProducer() {
        Notification notification = queuedNotification(NotificationChannel.SMS);
        SmsTaskDto expected = new SmsTaskDto(notification.getId(), "+905551112233",
                "raw-sms-template", Map.of("textContent", "Kod 123456", "locale", "tr"));
        storePayload(notification, expected);

        assertTrue(dispatchService.runTask(notification.getId()));

        verify(producer).sendSmsTask(expected);
    }

    @Test
    void exactPushPayloadSurvivesPersistenceAndRoutesEveryFieldToThePushProducer() {
        Notification notification = queuedNotification(NotificationChannel.PUSH);
        PushTaskDto expected = new PushTaskDto(notification.getId(), "push-token-7", "Exact title",
                "Exact body", Map.of("jobId", "job-7", "screen", "job-detail"));
        storePayload(notification, expected);

        assertTrue(dispatchService.runTask(notification.getId()));

        verify(producer).sendPushTask(expected);
    }

    @Test
    void recoverySweepsHaveDedicatedPartialIndexes() {
        Set<String> indexes = new HashSet<>(jdbcTemplate.queryForList("""
                select indexname from pg_indexes
                where schemaname = 'public' and tablename = 'notifications'
                  and indexname like 'idx_notifications_dispatch_%'
                """, String.class));

        assertTrue(indexes.containsAll(Set.of(
                "idx_notifications_dispatch_due",
                "idx_notifications_dispatch_stale_claim",
                "idx_notifications_dispatch_legacy_payload",
                "idx_notifications_dispatch_exhausted",
                "idx_notifications_dispatch_backlog_age",
                "idx_notifications_dispatch_terminal_broker_failure")));
    }

    @Test
    void anExhaustedStaleClaimRemainsReplayableWhileAnUnclaimedExhaustedRowIsTerminalized() {
        Notification stale = queuedEmail();
        stale.setDispatchAttempts(2);
        stale.setDispatchClaimId(UUID.randomUUID());
        stale.setDispatchClaimedAt(NOW.minus(Duration.ofMinutes(6)));
        notificationDao.saveAndFlush(stale);
        Notification unclaimed = queuedEmail();
        unclaimed.setDispatchAttempts(2);
        notificationDao.saveAndFlush(unclaimed);

        assertEquals(List.of(stale.getId()), dispatchService.findDueTaskIds());

        Notification replayableStale = notificationDao.findById(stale.getId()).orElseThrow();
        Notification exhaustedUnclaimed = notificationDao.findById(unclaimed.getId()).orElseThrow();
        assertEquals(NotificationStatus.QUEUED, replayableStale.getStatus());
        assertEquals(1, replayableStale.getDispatchAttempts());
        assertNull(replayableStale.getDispatchClaimId());
        assertNull(replayableStale.getFailureReason());
        assertEquals(NotificationStatus.FAILED, exhaustedUnclaimed.getStatus());
        assertEquals("BROKER_PUBLISH_EXHAUSTED", exhaustedUnclaimed.getFailureReason());
        verify(producer, times(0)).sendEmailTask(any());
    }

    private Notification queuedEmail() {
        return queuedEmail(DispatchPriority.NORMAL);
    }

    private Notification queuedEmail(DispatchPriority priority) {
        Notification notification = queuedNotification(NotificationChannel.EMAIL);
        notification.setDestinationEmail("outbox@koala.test");
        notification.setSubject("Hesap guvenligi");
        notification.setDispatchPriority(priority);
        EmailTaskDto payload = new EmailTaskDto(null, notification.getDestinationEmail(),
                notification.getSubject(), "raw-html-template", Map.of("htmlContent", "<p>Koala</p>"));
        notification.setDispatchPayload(objectMapper.writeValueAsString(new EmailTaskDto(
                notification.getId(), payload.to(), payload.subject(), payload.templateName(),
                payload.templateParameters())));
        return notificationDao.saveAndFlush(notification);
    }

    private Notification queuedNotification(NotificationChannel channel) {
        Notification notification = new Notification();
        notification.setChannel(channel);
        notification.setCategory(NotificationCategory.ACCOUNT_SECURITY);
        notification.setStatus(NotificationStatus.QUEUED);
        notification.setDispatchPriority(DispatchPriority.NORMAL);
        notification.setNextDispatchAt(NOW);
        notification = notificationDao.saveAndFlush(notification);
        notificationIds.add(notification.getId());
        return notification;
    }

    private void storePayload(Notification notification, Object payload) {
        notification.setDispatchPayload(objectMapper.writeValueAsString(payload));
        notificationDao.saveAndFlush(notification);
    }
}
