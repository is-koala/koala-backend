package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.UserProfileSyncService;
import com.exskylab.koala.core.configs.RabbitMQConfig;
import com.exskylab.koala.core.logging.LogContext;
import com.exskylab.koala.core.producers.ConfirmedRabbitPublisher;
import com.exskylab.koala.core.producers.user.UserProducerService;
import com.exskylab.koala.dataAccess.UserProfileSyncOutboxDao;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.entities.UserProfileSyncOutbox;
import com.exskylab.koala.entities.UserProfileSyncStatus;
import com.exskylab.koala.support.AbstractIntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@TestPropertySource(properties = {
        "user-profile-sync.dispatch.enabled=true",
        "user-profile-sync.dispatch.max-attempts=2",
        "user-profile-sync.dispatch.initial-backoff=PT1M",
        "user-profile-sync.dispatch.max-backoff=PT10M",
        "user-profile-sync.dispatch.claim-timeout=PT5M",
        "user-profile-sync.dispatch.publisher-confirm-timeout=PT10S",
        "user-profile-sync.dispatch.processing-claim-timeout=PT5M",
        "user-profile-sync.dispatch.batch-size=20",
        "user-profile-sync.dispatch.fixed-delay=PT1H"
})
class UserProfileSyncDispatchIT extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-11T12:00:00Z");

    @Autowired private UserProducerService producer;
    @Autowired private UserProfileSyncService syncService;
    @Autowired private UserProfileSyncOutboxDao outboxDao;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private MeterRegistry meterRegistry;
    @MockitoBean private ConfirmedRabbitPublisher publisher;
    @MockitoBean private Clock clock;
    private final List<UUID> userIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
    }

    @AfterEach
    void cleanCommittedFixtures() {
        LogContext.clear();
        for (UUID userId : userIds) {
            jdbcTemplate.update("delete from user_profile_sync_outbox where user_id = ?", userId);
            jdbcTemplate.update("delete from users where id = ?", userId);
        }
        userIds.clear();
    }

    @Test
    void aBrokerFailureIsRetriedThenTerminalizedWithASanitizedReason() {
        UserProfileSyncOutbox task = queuedIntent();
        doThrow(new IllegalStateException("amqp-secret-token")).when(publisher)
                .convertAndPublish(eq(RabbitMQConfig.USER_EXCHANGE),
                        eq(RabbitMQConfig.USER_PROFILE_UPDATED_ROUTING_KEY), any(), any());

        assertFalse(syncService.runTask(task.getId()));
        UserProfileSyncOutbox retry = outboxDao.findById(task.getId()).orElseThrow();
        assertEquals(UserProfileSyncStatus.PENDING, retry.getStatus());
        assertEquals(1, retry.getAttempts());
        assertEquals("BROKER_PUBLISH_RETRY_SCHEDULED", retry.getFailureReason());
        assertNull(retry.getPublishedAt());
        retry.setNextAttemptAt(NOW);
        outboxDao.saveAndFlush(retry);

        assertFalse(syncService.runTask(task.getId()));

        UserProfileSyncOutbox exhausted = outboxDao.findById(task.getId()).orElseThrow();
        assertEquals(2, exhausted.getAttempts());
        assertEquals(UserProfileSyncStatus.FAILED, exhausted.getStatus());
        assertEquals("BROKER_PUBLISH_EXHAUSTED", exhausted.getFailureReason());
        assertFalse(exhausted.getFailureReason().contains("secret"));
        assertNull(exhausted.getClaimId());
        verify(publisher, times(2)).convertAndPublish(eq(RabbitMQConfig.USER_EXCHANGE),
                eq(RabbitMQConfig.USER_PROFILE_UPDATED_ROUTING_KEY), any(), any());
    }

    @Test
    void aConfirmedPublishWhoseCompletionCannotBePersistedRemainsReplayableWithoutSpendingFailureBudget() {
        UserProfileSyncOutbox task = queuedIntent();
        jdbcTemplate.execute("""
                create function reject_user_profile_sync_completion() returns trigger
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
                create trigger reject_user_profile_sync_completion
                before update on user_profile_sync_outbox
                for each row when (new.id = '%s'::uuid)
                execute function reject_user_profile_sync_completion()
                """.formatted(task.getId()));

        try {
            assertThrows(RuntimeException.class, () -> syncService.runTask(task.getId()));
        } finally {
            jdbcTemplate.execute("drop trigger reject_user_profile_sync_completion on user_profile_sync_outbox");
            jdbcTemplate.execute("drop function reject_user_profile_sync_completion()");
        }

        UserProfileSyncOutbox awaiting = outboxDao.findById(task.getId()).orElseThrow();
        assertEquals(UserProfileSyncStatus.PENDING, awaiting.getStatus());
        assertNull(awaiting.getPublishedAt());
        assertEquals(1, awaiting.getAttempts());
        assertNotNull(awaiting.getClaimId());
        assertNull(awaiting.getFailureReason());

        jdbcTemplate.update("update user_profile_sync_outbox set claimed_at = ? where id = ?",
                Timestamp.from(NOW.minus(Duration.ofMinutes(6))), task.getId());

        assertEquals(List.of(task.getId()), syncService.findDueTaskIds());
        assertTrue(syncService.runTask(task.getId()));

        UserProfileSyncOutbox replayed = outboxDao.findById(task.getId()).orElseThrow();
        assertEquals(UserProfileSyncStatus.PENDING, replayed.getStatus());
        assertEquals(NOW, replayed.getPublishedAt());
        assertEquals(1, replayed.getAttempts());
        assertNull(replayed.getClaimId());
        assertNull(replayed.getFailureReason());
        verify(publisher, times(2)).convertAndPublish(eq(RabbitMQConfig.USER_EXCHANGE),
                eq(RabbitMQConfig.USER_PROFILE_UPDATED_ROUTING_KEY), any(), any());
    }

    @Test
    void twoWorkersCannotPublishTheSameIntent() throws Exception {
        UserProfileSyncOutbox task = queuedIntent();
        CountDownLatch enteredBroker = new CountDownLatch(1);
        CountDownLatch releaseBroker = new CountDownLatch(1);
        doAnswer(invocation -> {
            enteredBroker.countDown();
            if (!releaseBroker.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("broker release timed out");
            }
            return null;
        }).when(publisher).convertAndPublish(any(), any(), any(), any());

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> syncService.runTask(task.getId()));
            assertTrue(enteredBroker.await(5, TimeUnit.SECONDS));
            var second = executor.submit(() -> syncService.runTask(task.getId()));
            assertFalse(second.get(5, TimeUnit.SECONDS));
            releaseBroker.countDown();
            assertTrue(first.get(5, TimeUnit.SECONDS));
        }

        UserProfileSyncOutbox published = outboxDao.findById(task.getId()).orElseThrow();
        assertEquals(NOW, published.getPublishedAt());
        verify(publisher, times(1)).convertAndPublish(any(), any(), any(), any());
    }

    @Test
    void eachPublishRestoresItsPersistedOriginInsteadOfTheSchedulerBatchContext() {
        UserProfileSyncOutbox task = queuedIntent();
        task.setDispatchRequestId("origin-request");
        task.setDispatchUserId("origin-user");
        task.setDispatchSessionId("origin-session");
        task.setDispatchDeviceId("origin-device");
        outboxDao.saveAndFlush(task);
        AtomicReference<Map<String, String>> publishedContext = new AtomicReference<>();
        doAnswer(invocation -> {
            publishedContext.set(LogContext.carried());
            return null;
        }).when(publisher).convertAndPublish(any(), any(), any(), any());
        LogContext.adopt(Map.of(LogContext.REQUEST_ID, "scheduler-request"));

        assertTrue(syncService.runTask(task.getId()));
        assertEquals(Map.of(
                LogContext.REQUEST_ID, "origin-request",
                LogContext.USER_ID, "origin-user",
                LogContext.SESSION_ID, "origin-session",
                LogContext.DEVICE_ID, "origin-device"), publishedContext.get());
        assertEquals(Map.of(LogContext.REQUEST_ID, "scheduler-request"), LogContext.carried());
    }

    @Test
    void backlogAgeAndTerminalFailuresArePublishedAsGauges() {
        UserProfileSyncOutbox queued = queuedIntent();
        queued.setNextAttemptAt(NOW.plus(Duration.ofHours(1)));
        outboxDao.saveAndFlush(queued);
        jdbcTemplate.update("update user_profile_sync_outbox set created_at = ? where id = ?",
                Timestamp.from(NOW.minusSeconds(45)), queued.getId());
        jdbcTemplate.update("""
                update user_profile_sync_outbox
                set status = 'FAILED', failure_reason = 'BROKER_PUBLISH_EXHAUSTED', published_at = ?
                where id = ?
                """, Timestamp.from(NOW), queuedIntent().getId());

        syncService.findDueTaskIds();

        assertEquals(1, meterRegistry.get("koala.user.profile.sync.backlog").gauge().value());
        assertEquals(45, meterRegistry.get("koala.user.profile.sync.oldest.age").gauge().value());
        assertEquals(1, meterRegistry.get("koala.user.profile.sync.terminal.broker.failures").gauge().value());
    }

    private UserProfileSyncOutbox queuedIntent() {
        UUID userId = persistUser();
        producer.publishUserProfileUpdated(user(userId));
        UUID intentId = outboxDao.findUnclaimedUnpublishedId(userId).orElseThrow();
        UserProfileSyncOutbox stored = outboxDao.findById(intentId).orElseThrow();
        stored.setNextAttemptAt(NOW);
        return outboxDao.saveAndFlush(stored);
    }

    private UUID persistUser() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into users (id, created_at, is_deleted, email, password, is_employer,
                    email_verified, phone_verified, identity_verified, first_name, last_name,
                    iyzico_submerchant_key)
                values (?, now(), false, ?, 'test', false, true, true, true, 'Ada', 'Yilmaz', ?)
                """, userId, userId + "@koala.test", "sub-merchant-key");
        userIds.add(userId);
        return userId;
    }

    private User user(UUID userId) {
        User user = new User();
        user.setId(userId);
        user.setFirstName("Ada");
        user.setLastName("Yilmaz");
        user.setEmail(userId + "@koala.test");
        user.setPhoneNumber("+905551112233");
        user.setIyzicoSubmerchantKey("sub-merchant-key");
        return user;
    }
}
