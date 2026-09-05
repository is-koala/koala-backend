package com.exskylab.koala.core.consumers;

import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.core.configs.RabbitMQConfig;
import com.exskylab.koala.core.producers.notification.EmailTaskDto;
import com.exskylab.koala.core.producers.notification.NotificationProducerService;
import com.exskylab.koala.entities.Notification;
import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.NotificationChannel;
import com.exskylab.koala.entities.NotificationStatus;
import com.exskylab.koala.support.AbstractIntegrationTest;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@TestPropertySource(properties = {
        "management.health.mail.enabled=false",
        "qa.marketplace-seed.version=",
        "email.delivery.mode=ALLOWLIST",
        "email.delivery.allowed-recipients=qa-employer@example.test"
})
class EmailRetryIT extends AbstractIntegrationTest {

    private static final GenericContainer<?> RABBIT = new GenericContainer<>("rabbitmq:4.1.3-management")
            .withExposedPorts(5672)
            .withEnv("RABBITMQ_DEFAULT_USER", "koala-test")
            .withEnv("RABBITMQ_DEFAULT_PASS", "test-only");

    static {
        RABBIT.start();
        var connectionFactory = new com.rabbitmq.client.ConnectionFactory();
        connectionFactory.setHost(RABBIT.getHost());
        connectionFactory.setPort(RABBIT.getMappedPort(5672));
        connectionFactory.setUsername("koala-test");
        connectionFactory.setPassword("test-only");
        try (var connection = connectionFactory.newConnection(); var channel = connection.createChannel()) {
            channel.queueDeclare(RabbitMQConfig.EMAIL_QUEUE, true, false, false, Map.of());
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot prepare the existing email queue", exception);
        }
    }

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", RABBIT::getHost);
        registry.add("spring.rabbitmq.port", () -> RABBIT.getMappedPort(5672));
        registry.add("spring.rabbitmq.username", () -> "koala-test");
        registry.add("spring.rabbitmq.password", () -> "test-only");
    }

    @Autowired private RabbitTemplate rabbit;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RabbitListenerEndpointRegistry listeners;
    @Autowired private NotificationService notifications;
    @Autowired private NotificationProducerService producer;
    @Autowired @Qualifier("emailQueue") private Queue emailQueue;
    @MockitoBean private JavaMailSender mailSender;

    @Test
    void anExistingQueueRetriesIdlessMessagesThenParksFailuresWithoutStoppingDelivery() {
        RabbitAdmin admin = new RabbitAdmin(rabbit);
        assertDoesNotThrow(() -> admin.declareQueue(emailQueue), "The deployed queue must remain compatible");
        AbstractMessageListenerContainer listener = listeners.getListenerContainers().stream()
                .map(AbstractMessageListenerContainer.class::cast)
                .filter(container -> Arrays.asList(container.getQueueNames()).contains(RabbitMQConfig.EMAIL_QUEUE))
                .findFirst().orElseThrow();
        when(mailSender.createMimeMessage())
                .thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("SMTP unavailable")).when(mailSender).send(any(MimeMessage.class));

        listener.start();
        try {
            Notification failed = queuedEmail();
            EmailTaskDto task = taskFor(failed);
            producer.sendEmailTask(task);

            Message parked = rabbit.receive(RabbitMQConfig.EMAIL_DEAD_LETTER_QUEUE, 15_000);
            assertNotNull(parked, "Exhausted delivery must remain available in the DLQ");
            assertNull(parked.getMessageProperties().getMessageId(), "Exercise old messages without an AMQP ID");
            assertEquals(task, objectMapper.readValue(parked.getBody(), EmailTaskDto.class));
            assertEquals(NotificationStatus.FAILED, notifications.getById(failed.getId()).getStatus());
            assertNull(notifications.getById(failed.getId()).getSentAt());
            verify(mailSender, times(3)).send(any(MimeMessage.class));

            doNothing().when(mailSender).send(any(MimeMessage.class));
            Notification next = queuedEmail();
            producer.sendEmailTask(taskFor(next));

            await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                    assertEquals(NotificationStatus.SENT, notifications.getById(next.getId()).getStatus()));
            verify(mailSender, times(4)).send(any(MimeMessage.class));
            assertTrue(listener.isRunning(), "A provider failure must not terminate the email consumer");
        } finally {
            listener.stop();
        }
    }

    private Notification queuedEmail() {
        Notification notification = new Notification();
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setCategory(NotificationCategory.ACCOUNT_SECURITY);
        notification.setStatus(NotificationStatus.QUEUED);
        notification.setDestinationEmail("qa-employer@example.test");
        notification.setSubject("Hesap doğrulama");
        return notifications.save(notification);
    }

    private EmailTaskDto taskFor(Notification notification) {
        return new EmailTaskDto(notification.getId(), notification.getDestinationEmail(),
                notification.getSubject(), "raw-html-template", Map.of("htmlContent", "<p>Koala</p>"));
    }
}
