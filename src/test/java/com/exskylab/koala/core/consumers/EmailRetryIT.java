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
import com.exskylab.koala.support.TestRabbit;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Binding;
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
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@TestPropertySource(properties = {
        "management.health.mail.enabled=false",
        "email.delivery.mode=ALLOWLIST",
        "email.delivery.allowed-recipients=qa-employer@example.test"
})
class EmailRetryIT extends AbstractIntegrationTest {

    static {
        TestRabbit.declareDurableQueue(RabbitMQConfig.EMAIL_QUEUE);
    }

    @DynamicPropertySource
    static void rabbitProperties(DynamicPropertyRegistry registry) {
        TestRabbit.register(registry);
    }

    @Autowired private RabbitTemplate rabbit;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RabbitListenerEndpointRegistry listeners;
    @Autowired private NotificationService notifications;
    @Autowired private NotificationProducerService producer;
    @Autowired @Qualifier("emailQueue") private Queue emailQueue;
    @Autowired @Qualifier("emailDeadLetterBinding") private Binding emailDeadLetterBinding;
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

    @Test
    void aMissingDeadLetterRouteCannotAcknowledgeTheSourceMessage() throws Exception {
        RabbitAdmin admin = new RabbitAdmin(rabbit);
        admin.purgeQueue(RabbitMQConfig.EMAIL_QUEUE, false);
        admin.purgeQueue(RabbitMQConfig.EMAIL_DEAD_LETTER_QUEUE, false);
        admin.removeBinding(emailDeadLetterBinding);
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

            await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                    assertEquals(NotificationStatus.FAILED, notifications.getById(failed.getId()).getStatus()));
            Thread.sleep(300);
            admin.declareBinding(emailDeadLetterBinding);

            Message parked = rabbit.receive(RabbitMQConfig.EMAIL_DEAD_LETTER_QUEUE, 15_000);
            assertNotNull(parked);
            assertEquals(task, objectMapper.readValue(parked.getBody(), EmailTaskDto.class));
            verify(mailSender, times(3)).send(any(MimeMessage.class));
        } finally {
            admin.declareBinding(emailDeadLetterBinding);
            listener.stop();
        }
    }

    @Test
    void anOrphanEmailTaskIsConfirmedIntoDeadLetterWithoutHotRequeue() {
        RabbitAdmin admin = new RabbitAdmin(rabbit);
        admin.purgeQueue(RabbitMQConfig.EMAIL_QUEUE, false);
        admin.purgeQueue(RabbitMQConfig.EMAIL_DEAD_LETTER_QUEUE, false);
        AbstractMessageListenerContainer listener = listeners.getListenerContainers().stream()
                .map(AbstractMessageListenerContainer.class::cast)
                .filter(container -> Arrays.asList(container.getQueueNames()).contains(RabbitMQConfig.EMAIL_QUEUE))
                .findFirst().orElseThrow();
        EmailTaskDto orphan = new EmailTaskDto(UUID.randomUUID(), "qa-employer@example.test", "Konu",
                "raw-html-template", Map.of("htmlContent", "<p>Koala</p>"));

        listener.start();
        try {
            producer.sendEmailTask(orphan);

            Message parked = rabbit.receive(RabbitMQConfig.EMAIL_DEAD_LETTER_QUEUE, 10_000);
            assertNotNull(parked, "An orphan task must leave the source queue after confirmed dead-lettering");
            assertEquals(orphan, objectMapper.readValue(parked.getBody(), EmailTaskDto.class));
            assertNull(rabbit.receive(RabbitMQConfig.EMAIL_QUEUE, 500),
                    "An orphan task must not remain in a hot requeue loop");
            verifyNoInteractions(mailSender);
        } finally {
            listener.stop();
            admin.purgeQueue(RabbitMQConfig.EMAIL_QUEUE, false);
            admin.purgeQueue(RabbitMQConfig.EMAIL_DEAD_LETTER_QUEUE, false);
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
