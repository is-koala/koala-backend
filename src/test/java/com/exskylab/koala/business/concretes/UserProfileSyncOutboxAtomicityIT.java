package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.UserProfileSyncService;
import com.exskylab.koala.core.logging.LogContext;
import com.exskylab.koala.core.producers.ConfirmedRabbitPublisher;
import com.exskylab.koala.core.producers.user.UserProducerService;
import com.exskylab.koala.core.producers.user.UserUpdatedEventDto;
import com.exskylab.koala.dataAccess.UserProfileSyncOutboxDao;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.entities.UserProfileSyncOutbox;
import com.exskylab.koala.entities.UserProfileSyncStatus;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;

@TestPropertySource(properties = {
        "user-profile-sync.dispatch.enabled=true",
        "user-profile-sync.dispatch.fixed-delay=PT1H"
})
class UserProfileSyncOutboxAtomicityIT extends AbstractIntegrationTest {

    @Autowired private UserProducerService producer;
    @Autowired private UserProfileSyncService syncService;
    @Autowired private UserProfileSyncOutboxDao outboxDao;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private ConfirmedRabbitPublisher publisher;
    private final List<UUID> userIds = new ArrayList<>();

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
    void rollingBackTheCallerTransactionLeavesNeitherProfileChangeNorPublishIntent() {
        UUID userId = persistUser();
        AtomicReference<UUID> intentId = new AtomicReference<>();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            producer.publishUserProfileUpdated(user(userId));
            intentId.set(outboxDao.findUnclaimedUnpublishedId(userId).orElseThrow());
            status.setRollbackOnly();
        });

        assertTrue(outboxDao.findById(intentId.get()).isEmpty());
        verifyNoInteractions(publisher);
    }

    @Test
    void callerSuccessMeansTheProfileChangeAndExactPublishIntentWereDurablyAcceptedTogether() {
        UUID userId = persistUser();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> producer.publishUserProfileUpdated(user(userId)));

        UserProfileSyncOutbox stored = outboxDao.findById(
                outboxDao.findUnclaimedUnpublishedId(userId).orElseThrow()).orElseThrow();
        UserUpdatedEventDto payload = objectMapper.readValue(stored.getPayload(), UserUpdatedEventDto.class);
        assertEquals(UserProfileSyncStatus.PENDING, stored.getStatus());
        assertEquals(0, stored.getAttempts());
        assertNotNull(stored.getNextAttemptAt());
        assertEquals(userId, payload.getId());
        assertEquals(stored.getId(), payload.getIntentId());
        assertEquals("Ada", payload.getFirstName());
        assertEquals("sub-merchant-key", payload.getIyzicoSubMerchantKey());
        verifyNoInteractions(publisher);
        assertTrue(syncService.findDueTaskIds().contains(stored.getId()));
    }

    @Test
    void theOriginatingRequestContextIsPersistedWithTheOutboxIntent() {
        UUID userId = persistUser();
        LogContext.adopt(Map.of(
                LogContext.REQUEST_ID, "request-7",
                LogContext.USER_ID, "user-7",
                LogContext.SESSION_ID, "session-7",
                LogContext.DEVICE_ID, "device-7"));

        producer.publishUserProfileUpdated(user(userId));

        UserProfileSyncOutbox stored = outboxDao.findById(
                outboxDao.findUnclaimedUnpublishedId(userId).orElseThrow()).orElseThrow();
        assertEquals("request-7", stored.getDispatchRequestId());
        assertEquals("user-7", stored.getDispatchUserId());
        assertEquals("session-7", stored.getDispatchSessionId());
        assertEquals("device-7", stored.getDispatchDeviceId());
        verifyNoInteractions(publisher);
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
        user.setTcIdentityNumber("11111111110");
        user.setIyzicoSubmerchantKey("sub-merchant-key");
        return user;
    }
}
