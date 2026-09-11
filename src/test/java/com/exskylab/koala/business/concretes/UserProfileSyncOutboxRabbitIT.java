package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.UserProfileSyncService;
import com.exskylab.koala.core.configs.RabbitMQConfig;
import com.exskylab.koala.core.producers.user.UserProducerService;
import com.exskylab.koala.core.producers.user.UserUpdatedEventDto;
import com.exskylab.koala.core.utilities.payment.iyzico.IyzicoPaymentService;
import com.exskylab.koala.core.utilities.payment.iyzico.dtos.SubMerchantUpdateRequestDto;
import com.exskylab.koala.dataAccess.UserProfileSyncOutboxDao;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.entities.UserProfileSyncOutbox;
import com.exskylab.koala.entities.UserProfileSyncStatus;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@TestPropertySource(properties = {
        "management.health.rabbit.enabled=false",
        "user-profile-sync.dispatch.enabled=true",
        "user-profile-sync.dispatch.max-attempts=3",
        "user-profile-sync.dispatch.initial-backoff=PT1S",
        "user-profile-sync.dispatch.max-backoff=PT2S",
        "user-profile-sync.dispatch.jitter-factor=0",
        "user-profile-sync.dispatch.fixed-delay=PT1H"
})
class UserProfileSyncOutboxRabbitIT extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-11T12:00:00Z");
    private static final GenericContainer<?> RABBIT = new GenericContainer<>("rabbitmq:4.1.3-management")
            .withExposedPorts(5672)
            .withEnv("RABBITMQ_DEFAULT_USER", "koala-test")
            .withEnv("RABBITMQ_DEFAULT_PASS", "test-only");

    static {
        RABBIT.start();
    }

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBIT.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "koala-test");
        registry.add("spring.rabbitmq.password", () -> "test-only");
    }

    @Autowired private UserProducerService producer;
    @Autowired private UserProfileSyncService syncService;
    @Autowired private UserProfileSyncOutboxDao outboxDao;
    @Autowired private RabbitTemplate rabbit;
    @Autowired private RabbitListenerEndpointRegistry listeners;
    @Autowired private ObjectMapper objectMapper;
    @Autowired @Qualifier("usersExchange") private TopicExchange usersExchange;
    @Autowired @Qualifier("userProfileUpdatedForSyncQueue") private Queue syncQueue;
    @Autowired @Qualifier("userProfileUpdatedBinding") private Binding syncBinding;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private Clock clock;
    @MockitoBean private IyzicoPaymentService iyzicoPaymentService;
    private final List<UUID> userIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        listenerFor(RabbitMQConfig.USER_PROFILE_UPDATED_FOR_SYNC_QUEUE).stop();
        RabbitAdmin admin = new RabbitAdmin(rabbit);
        admin.declareExchange(usersExchange);
        admin.declareQueue(syncQueue);
        admin.declareBinding(syncBinding);
        admin.purgeQueue(RabbitMQConfig.USER_PROFILE_UPDATED_FOR_SYNC_QUEUE, false);
    }

    @AfterEach
    void cleanCommittedFixtures() {
        listenerFor(RabbitMQConfig.USER_PROFILE_UPDATED_FOR_SYNC_QUEUE).stop();
        for (UUID userId : userIds) {
            jdbcTemplate.update("delete from user_profile_sync_outbox where user_id = ?", userId);
            jdbcTemplate.update("delete from users where id = ?", userId);
        }
        userIds.clear();
    }

    @Test
    void aProfileSyncSurvivesAMissingQueueAndPublishesAfterTopologyRecovery() {
        UserProfileSyncOutbox intent = queuedIntent();
        RabbitAdmin admin = new RabbitAdmin(rabbit);

        assertTrue(admin.deleteQueue(RabbitMQConfig.USER_PROFILE_UPDATED_FOR_SYNC_QUEUE));
        assertFalse(syncService.runTask(intent.getId()));

        UserProfileSyncOutbox retry = outboxDao.findById(intent.getId()).orElseThrow();
        assertEquals(UserProfileSyncStatus.PENDING, retry.getStatus());
        assertEquals(1, retry.getAttempts());
        assertNull(retry.getPublishedAt());
        assertNotNull(retry.getNextAttemptAt());
        assertNull(retry.getClaimId());

        admin.declareQueue(syncQueue);
        admin.declareBinding(syncBinding);
        retry.setNextAttemptAt(NOW);
        outboxDao.saveAndFlush(retry);

        assertTrue(syncService.runTask(intent.getId()));

        Message message = rabbit.receive(RabbitMQConfig.USER_PROFILE_UPDATED_FOR_SYNC_QUEUE, 5_000);
        assertNotNull(message);
        UserUpdatedEventDto delivered = objectMapper.readValue(message.getBody(), UserUpdatedEventDto.class);
        assertEquals(intent.getId(), delivered.getIntentId());
        assertEquals(intent.getUserId(), delivered.getId());
        assertEquals("Ada", delivered.getFirstName());
        assertEquals("sub-merchant-key", delivered.getIyzicoSubMerchantKey());
        UserProfileSyncOutbox published = outboxDao.findById(intent.getId()).orElseThrow();
        assertEquals(2, published.getAttempts());
        assertEquals(NOW, published.getPublishedAt());
        assertNull(published.getClaimId());
    }

    @Test
    void aDuplicateDeliveryCannotInvokeTheProviderTwice() {
        UserProfileSyncOutbox intent = queuedIntent();
        UserUpdatedEventDto payload = objectMapper.readValue(intent.getPayload(), UserUpdatedEventDto.class);

        AbstractMessageListenerContainer listener = listenerFor(RabbitMQConfig.USER_PROFILE_UPDATED_FOR_SYNC_QUEUE);
        listener.start();
        try {
            assertTrue(syncService.runTask(intent.getId()));
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertNotNull(outboxDao.findById(intent.getId()).orElseThrow().getProcessedAt()));

            rabbit.convertAndSend(RabbitMQConfig.USER_EXCHANGE,
                    RabbitMQConfig.USER_PROFILE_UPDATED_ROUTING_KEY, payload);

            await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                    verify(iyzicoPaymentService, times(1)).updateSubMerchant(any(SubMerchantUpdateRequestDto.class)));
        } finally {
            listener.stop();
        }
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

    private AbstractMessageListenerContainer listenerFor(String queue) {
        return listeners.getListenerContainers().stream()
                .map(AbstractMessageListenerContainer.class::cast)
                .filter(container -> Arrays.asList(container.getQueueNames()).contains(queue))
                .findFirst().orElseThrow();
    }
}
