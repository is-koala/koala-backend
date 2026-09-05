package com.exskylab.koala.core.consumers;

import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.core.producers.notification.EmailTaskDto;
import com.exskylab.koala.entities.Notification;
import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.NotificationChannel;
import com.exskylab.koala.entities.NotificationStatus;
import com.exskylab.koala.support.AbstractIntegrationTest;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

@TestPropertySource(properties = {
        "management.health.mail.enabled=false",
        "qa.marketplace-seed.version=",
        "email.delivery.mode=ALLOWLIST",
        "email.delivery.allowed-recipients=qa-employer@example.test,qa-worker@example.test"
})
class EmailDeliveryIT extends AbstractIntegrationTest {

    @Autowired
    private NotificationService notifications;

    @Autowired
    private EmailConsumer consumer;

    @MockitoBean
    private JavaMailSender mailSender;

    @ParameterizedTest
    @ValueSource(strings = {"qa-employer@example.test", "QA-WORKER@example.test"})
    void aControlledRecipientReachesTheSmtpTransport(String address) throws Exception {
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        Notification notification = queuedEmail(address);

        consumer.handleEmailTask(taskFor(notification));

        Notification result = notifications.getById(notification.getId());
        assertEquals(NotificationStatus.SENT, result.getStatus());
        assertNotNull(result.getSentAt());
        assertEquals(address, message.getAllRecipients()[0].toString());
        verify(mailSender).send(message);
    }

    @Test
    void anAlreadySentNotificationIsNotReclassifiedWhenTheAllowlistChanges() {
        Notification notification = queuedEmail("former-qa@example.test");
        notification.setStatus(NotificationStatus.SENT);
        notification.setSentAt(java.time.Instant.now());
        notifications.save(notification);

        consumer.handleEmailTask(taskFor(notification));

        assertEquals(NotificationStatus.SENT, notifications.getById(notification.getId()).getStatus());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void aTaskCannotDeliverToADifferentAddressThanThePersistedNotification() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        Notification notification = queuedEmail("seed-user@example.test");
        EmailTaskDto redirectedTask = new EmailTaskDto(notification.getId(), "qa-employer@example.test",
                notification.getSubject(), "raw-html-template", Map.of("htmlContent", "<p>Koala</p>"));

        assertThrows(org.springframework.amqp.AmqpRejectAndDontRequeueException.class,
                () -> consumer.handleEmailTask(redirectedTask));

        Notification result = notifications.getById(notification.getId());
        assertEquals(NotificationStatus.FAILED, result.getStatus());
        assertEquals("EMAIL_RECIPIENT_MISMATCH", result.getFailureReason());
        assertNull(result.getSentAt());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void aRecipientOutsideTheQaAllowlistIsNotDeliveredOrReportedAsSent() {
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        Notification notification = queuedEmail("seed-user@example.test");

        consumer.handleEmailTask(taskFor(notification));

        Notification result = notifications.getById(notification.getId());
        assertEquals("SUPPRESSED", result.getStatus().name());
        assertNull(result.getSentAt());
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    private Notification queuedEmail(String address) {
        Notification notification = new Notification();
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setCategory(NotificationCategory.ACCOUNT_SECURITY);
        notification.setStatus(NotificationStatus.QUEUED);
        notification.setDestinationEmail(address);
        notification.setSubject("Hesap doğrulama");
        return notifications.save(notification);
    }

    private EmailTaskDto taskFor(Notification notification) {
        return new EmailTaskDto(notification.getId(), notification.getDestinationEmail(),
                notification.getSubject(), "raw-html-template", Map.of("htmlContent", "<p>Koala</p>"));
    }
}
