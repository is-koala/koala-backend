package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.NotificationDispatchService;
import com.exskylab.koala.core.configs.RabbitMQConfig;
import com.exskylab.koala.core.producers.notification.EmailTaskDto;
import com.exskylab.koala.core.producers.notification.PushTaskDto;
import com.exskylab.koala.core.utilities.push.PushMessage;
import com.exskylab.koala.core.utilities.push.PushService;
import com.exskylab.koala.dataAccess.NotificationDao;
import com.exskylab.koala.entities.Device;
import com.exskylab.koala.entities.DispatchPriority;
import com.exskylab.koala.entities.Notification;
import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.NotificationChannel;
import com.exskylab.koala.entities.NotificationStatus;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.support.AbstractIntegrationTest;
import com.exskylab.koala.support.TestRabbit;
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
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.awaitility.Awaitility.await;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@TestPropertySource(properties = {
        "management.health.rabbit.enabled=false",
        "notification.dispatch.enabled=true",
        "notification.dispatch.max-attempts=3",
        "notification.dispatch.initial-backoff=PT1S",
        "notification.dispatch.max-backoff=PT2S",
        "notification.dispatch.jitter-factor=0",
        "notification.dispatch.delivery-claim-timeout=PT0.2S",
        "notification.dispatch.provider-timeout=PT0.05S",
        "notification.dispatch.delivery-busy-retry-interval=PT0.05S",
        "notification.dispatch.delivery-busy-max-retries=6"
})
class NotificationOutboxRabbitIT extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-09T12:00:00Z");

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        TestRabbit.register(registry);
    }

    @Autowired private NotificationDispatchService dispatchService;
    @Autowired private NotificationDao notificationDao;
    @Autowired private RabbitTemplate rabbit;
    @Autowired private RabbitListenerEndpointRegistry listeners;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private TopicExchange notificationsExchange;
    @Autowired @Qualifier("emailQueue") private Queue emailQueue;
    @Autowired @Qualifier("emailBinding") private Binding emailBinding;
    @Autowired private JdbcTemplate jdbcTemplate;
    @MockitoBean private Clock clock;
    @MockitoBean private PushService pushService;
    private final List<UUID> notificationIds = new ArrayList<>();
    private final List<UUID> userIds = new ArrayList<>();
    private final List<UUID> deviceIds = new ArrayList<>();

    @BeforeEach
    void setUp() {
        when(clock.instant()).thenReturn(NOW);
        RabbitAdmin admin = new RabbitAdmin(rabbit);
        admin.declareExchange(notificationsExchange);
        admin.declareQueue(emailQueue);
        admin.declareBinding(emailBinding);
        admin.purgeQueue(RabbitMQConfig.EMAIL_QUEUE, false);
    }

    @AfterEach
    void cleanCommittedFixtures() {
        RabbitAdmin admin = new RabbitAdmin(rabbit);
        admin.declareExchange(notificationsExchange);
        admin.declareQueue(emailQueue);
        admin.declareBinding(emailBinding);
        for (UUID notificationId : notificationIds) {
            jdbcTemplate.update("delete from notifications where id = ?", notificationId);
        }
        notificationIds.clear();
        for (UUID deviceId : deviceIds) {
            jdbcTemplate.update("delete from devices where id = ?", deviceId);
        }
        deviceIds.clear();
        for (UUID userId : userIds) {
            jdbcTemplate.update("delete from users where id = ?", userId);
        }
        userIds.clear();
    }

    @Test
    void aNotificationSurvivesAMissingQueueAndPublishesAfterTopologyRecovery() {
        Notification notification = queuedEmail();
        RabbitAdmin admin = new RabbitAdmin(rabbit);

        assertTrue(admin.deleteQueue(RabbitMQConfig.EMAIL_QUEUE));
        assertFalse(dispatchService.runTask(notification.getId()));

        Notification retry = notificationDao.findById(notification.getId()).orElseThrow();
        assertEquals(NotificationStatus.QUEUED, retry.getStatus());
        assertEquals(1, retry.getDispatchAttempts());
        assertNull(retry.getPublishedAt());
        assertNotNull(retry.getNextDispatchAt());
        assertNull(retry.getDispatchClaimId());

        admin.declareQueue(emailQueue);
        admin.declareBinding(emailBinding);
        retry.setNextDispatchAt(NOW);
        notificationDao.saveAndFlush(retry);

        assertTrue(dispatchService.runTask(notification.getId()));

        Message message = rabbit.receive(RabbitMQConfig.EMAIL_QUEUE, 5_000);
        assertNotNull(message);
        EmailTaskDto delivered = objectMapper.readValue(message.getBody(), EmailTaskDto.class);
        assertEquals(notification.getId(), delivered.id());
        assertEquals("outbox@koala.test", delivered.to());
        assertEquals("Hesap guvenligi", delivered.subject());
        assertEquals("raw-html-template", delivered.templateName());
        assertEquals("<p>Koala</p>", delivered.templateParameters().get("htmlContent"));
        Notification published = notificationDao.findById(notification.getId()).orElseThrow();
        assertEquals(2, published.getDispatchAttempts());
        assertEquals(NOW, published.getPublishedAt());
        assertNull(published.getDispatchClaimId());
    }

    @Test
    void aDuplicatePushDeliveryCannotInvokeTheProviderTwice() {
        Notification notification = queuedPush(NotificationStatus.QUEUED);
        PushTaskDto task = objectMapper.readValue(notification.getDispatchPayload(), PushTaskDto.class);
        when(pushService.send(any(PushMessage.class), eq(Duration.ofMillis(50))))
                .thenReturn("message-7");

        AbstractMessageListenerContainer listener = listenerFor(RabbitMQConfig.PUSH_QUEUE);
        listener.start();
        try {
            assertTrue(dispatchService.runTask(notification.getId()));
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertEquals(NotificationStatus.SENT,
                            notificationDao.findById(notification.getId()).orElseThrow().getStatus()));

            rabbit.convertAndSend(RabbitMQConfig.NOTIFICATION_EXCHANGE, RabbitMQConfig.PUSH_ROUTING_KEY, task);

            await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(3)).untilAsserted(() ->
                    verify(pushService, times(1)).send(any(PushMessage.class), eq(Duration.ofMillis(50))));
        } finally {
            listener.stop();
        }
    }

    @Test
    void anInFlightPushProcessingClaimDoesNotInvokeTheProvider() {
        Notification notification = queuedPush(NotificationStatus.PROCESSING);
        notification.setPushProcessingClaimId(UUID.randomUUID());
        notification.setPushProcessingStartedAt(Instant.now());
        notificationDao.saveAndFlush(notification);
        PushTaskDto task = objectMapper.readValue(notification.getDispatchPayload(), PushTaskDto.class);

        AbstractMessageListenerContainer listener = listenerFor(RabbitMQConfig.PUSH_QUEUE);
        listener.start();
        try {
            rabbit.convertAndSend(RabbitMQConfig.NOTIFICATION_EXCHANGE, RabbitMQConfig.PUSH_ROUTING_KEY, task);

            await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
                verifyNoInteractions(pushService);
                assertEquals(NotificationStatus.PROCESSING,
                        notificationDao.findById(notification.getId()).orElseThrow().getStatus());
            });
        } finally {
            listener.stop();
        }
    }

    @Test
    void aLegacyFailedPushDeliveryIsAcknowledgedWithoutReactivation() {
        Notification notification = queuedPush(NotificationStatus.FAILED);
        PushTaskDto task = objectMapper.readValue(notification.getDispatchPayload(), PushTaskDto.class);

        AbstractMessageListenerContainer listener = listenerFor(RabbitMQConfig.PUSH_QUEUE);
        listener.start();
        try {
            rabbit.convertAndSend(RabbitMQConfig.NOTIFICATION_EXCHANGE, RabbitMQConfig.PUSH_ROUTING_KEY, task);

            await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(3)).untilAsserted(() -> {
                verifyNoInteractions(pushService);
                assertEquals(NotificationStatus.FAILED,
                        notificationDao.findById(notification.getId()).orElseThrow().getStatus());
            });
        } finally {
            listener.stop();
        }
    }

    private Notification queuedEmail() {
        Notification notification = new Notification();
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setCategory(NotificationCategory.ACCOUNT_SECURITY);
        notification.setStatus(NotificationStatus.QUEUED);
        notification.setDestinationEmail("outbox@koala.test");
        notification.setSubject("Hesap guvenligi");
        notification.setDispatchPriority(DispatchPriority.NORMAL);
        notification.setNextDispatchAt(NOW);
        notification = notificationDao.saveAndFlush(notification);
        notification.setDispatchPayload(objectMapper.writeValueAsString(new EmailTaskDto(
                notification.getId(), notification.getDestinationEmail(), notification.getSubject(),
                "raw-html-template", Map.of("htmlContent", "<p>Koala</p>"))));
        notification = notificationDao.saveAndFlush(notification);
        notificationIds.add(notification.getId());
        return notification;
    }

    private Notification queuedPush(NotificationStatus status) {
        UUID userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into users
                    (id, first_name, username, email, email_verified, phone_verified,
                     identity_verified, is_employer, created_at, is_deleted)
                values (?, 'Outbox', ?, ?, false, false, false, false, now(), false)
                """, userId, "outbox-" + userId, userId + "@koala.test");
        jdbcTemplate.update("""
                insert into devices
                    (id, created_at, is_deleted, user_id, type, name, client_device_id, push_token)
                values (?, now(), false, ?, 'ANDROID', 'outbox-device', ?, ?)
                """, deviceId, userId, UUID.randomUUID().toString(), "push-token-" + deviceId);
        userIds.add(userId);
        deviceIds.add(deviceId);
        User recipient = new User();
        recipient.setId(userId);
        Device device = new Device();
        device.setId(deviceId);
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setDestinationDevice(device);
        notification.setChannel(NotificationChannel.PUSH);
        notification.setCategory(NotificationCategory.APPLICATION_UPDATES);
        notification.setStatus(status);
        notification.setSubject("Exact title");
        notification.setBody("Exact body");
        notification.setPushDataPayload("{\"jobId\":\"job-7\"}");
        notification.setPushNextAttemptAt(NOW);
        notification.setDispatchPriority(DispatchPriority.NORMAL);
        notification.setNextDispatchAt(NOW);
        notification = notificationDao.saveAndFlush(notification);
        notification.setDispatchPayload(objectMapper.writeValueAsString(new PushTaskDto(notification.getId())));
        notification = notificationDao.saveAndFlush(notification);
        notificationIds.add(notification.getId());
        return notification;
    }

    private AbstractMessageListenerContainer listenerFor(String queue) {
        return listeners.getListenerContainers().stream()
                .map(AbstractMessageListenerContainer.class::cast)
                .filter(container -> Arrays.asList(container.getQueueNames()).contains(queue))
                .findFirst().orElseThrow();
    }
}
