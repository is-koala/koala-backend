package com.exskylab.koala.core.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.core.configs.RabbitMQConfig;
import com.exskylab.koala.core.consumers.EmailConsumer;
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
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.listener.AbstractMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@TestPropertySource(properties = {
        "management.health.mail.enabled=false",
        "email.delivery.mode=ALLOWLIST",
        "email.delivery.allowed-recipients=log-context@example.test"
})
class RabbitLogContextIT extends AbstractIntegrationTest {

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

    @Autowired private RabbitListenerEndpointRegistry listeners;
    @Autowired private NotificationService notifications;
    @Autowired private NotificationProducerService producer;
    @MockitoBean private JavaMailSender mailSender;

    @Test
    void theRequestIdOfThePublisherReachesTheConsumerLogLines() {
        Logger consumerLog = (Logger) LoggerFactory.getLogger(EmailConsumer.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        consumerLog.addAppender(appender);

        when(mailSender.createMimeMessage())
                .thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));

        AbstractMessageListenerContainer listener = emailListener();
        listener.start();
        String requestId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();

        try {
            Notification notification = queuedEmail();
            MDC.put("requestId", requestId);
            MDC.put("userId", userId);
            try {
                producer.sendEmailTask(new EmailTaskDto(notification.getId(), notification.getDestinationEmail(),
                        notification.getSubject(), "raw-html-template", Map.of("htmlContent", "<p>Koala</p>")));
            } finally {
                MDC.clear();
            }

            await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                    assertEquals(NotificationStatus.SENT, notifications.getById(notification.getId()).getStatus()));

            ILoggingEvent consumed = appender.list.stream()
                    .filter(event -> event.getFormattedMessage().contains(notification.getId().toString()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Tuketici hicbir sey loglamadi: " + appender.list));

            assertNotNull(consumed.getMDCPropertyMap().get("requestId"),
                    "Kuyruktan gecen isin log satirinda requestId yok");
            assertEquals(requestId, consumed.getMDCPropertyMap().get("requestId"),
                    "Yayimlayanin requestId'si tuketiciye tasinmali");
            assertEquals(userId, consumed.getMDCPropertyMap().get("userId"),
                    "Yayimlayanin userId'si tuketiciye tasinmali");
        } finally {
            listener.stop();
            consumerLog.detachAppender(appender);
            appender.stop();
            MDC.clear();
        }
    }

    @Test
    void aMessageWithoutAPublishedContextStillGetsItsOwnRequestId() {
        Logger consumerLog = (Logger) LoggerFactory.getLogger(EmailConsumer.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        consumerLog.addAppender(appender);

        when(mailSender.createMimeMessage())
                .thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));

        AbstractMessageListenerContainer listener = emailListener();
        listener.start();

        try {
            Notification notification = queuedEmail();
            MDC.clear();
            producer.sendEmailTask(new EmailTaskDto(notification.getId(), notification.getDestinationEmail(),
                    notification.getSubject(), "raw-html-template", Map.of("htmlContent", "<p>Koala</p>")));

            await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                    assertEquals(NotificationStatus.SENT, notifications.getById(notification.getId()).getStatus()));

            ILoggingEvent consumed = appender.list.stream()
                    .filter(event -> event.getFormattedMessage().contains(notification.getId().toString()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Tuketici hicbir sey loglamadi: " + appender.list));

            assertNotNull(consumed.getMDCPropertyMap().get("requestId"),
                    "Baglamsiz gelen mesaj bile kendi requestId'sini almali");
        } finally {
            listener.stop();
            consumerLog.detachAppender(appender);
            appender.stop();
            MDC.clear();
        }
    }

    private AbstractMessageListenerContainer emailListener() {
        return listeners.getListenerContainers().stream()
                .map(AbstractMessageListenerContainer.class::cast)
                .filter(container -> Arrays.asList(container.getQueueNames()).contains(RabbitMQConfig.EMAIL_QUEUE))
                .findFirst().orElseThrow();
    }

    private Notification queuedEmail() {
        Notification notification = new Notification();
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setCategory(NotificationCategory.ACCOUNT_SECURITY);
        notification.setStatus(NotificationStatus.QUEUED);
        notification.setDestinationEmail("log-context@example.test");
        notification.setSubject("Baglam tasima");
        return notifications.save(notification);
    }
}
