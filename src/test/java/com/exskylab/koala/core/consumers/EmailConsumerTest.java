package com.exskylab.koala.core.consumers;

import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.core.producers.notification.EmailTaskDto;
import com.exskylab.koala.core.properties.AppProperties;
import com.exskylab.koala.core.properties.EmailDeliveryProperties;
import com.exskylab.koala.entities.Notification;
import com.exskylab.koala.entities.NotificationChannel;
import com.exskylab.koala.entities.NotificationStatus;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EmailConsumerTest {

    @Test
    void smtpFailuresEscapeTheListenerSoRabbitCanRetry() {
        UUID id = UUID.randomUUID();
        UUID claimId = UUID.randomUUID();
        Notification notification = new Notification();
        notification.setId(id);
        notification.setStatus(NotificationStatus.QUEUED);
        notification.setDestinationEmail("demo@example.test");
        NotificationService notifications = mock(NotificationService.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SpringTemplateEngine templates = mock(SpringTemplateEngine.class);
        AppProperties properties = mock(AppProperties.class);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));

        when(notifications.getById(id)).thenReturn(notification);
        when(notifications.claimForDelivery(id, NotificationChannel.EMAIL)).thenReturn(Optional.of(claimId));
        when(properties.apiUrl()).thenReturn("https://api.example.test");
        when(templates.process(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any())).thenReturn("<p>Koala</p>");
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        org.mockito.Mockito.doThrow(new MailSendException("SMTP unavailable"))
                .when(mailSender).send(mimeMessage);

        EmailDeliveryProperties delivery = new EmailDeliveryProperties();
        delivery.setAllowedRecipients(Set.of("demo@example.test"));
        EmailConsumer consumer = new EmailConsumer(notifications, mailSender, templates, properties, delivery);
        EmailTaskDto task = new EmailTaskDto(id, "demo@example.test", "Konu", "raw-html-template", Map.of());

        assertThrows(MailSendException.class, () -> consumer.handleEmailTask(task));
        assertEquals(NotificationStatus.QUEUED, notification.getStatus());
        verify(notifications).releaseDeliveryForRetry(id, NotificationChannel.EMAIL, claimId,
                "EMAIL_DELIVERY_RETRY_SCHEDULED");
    }

    @Test
    void aSuccessfulRetryClearsTheEarlierFailureReason() {
        UUID id = UUID.randomUUID();
        UUID firstClaimId = UUID.randomUUID();
        UUID secondClaimId = UUID.randomUUID();
        Notification notification = new Notification();
        notification.setId(id);
        notification.setStatus(NotificationStatus.QUEUED);
        notification.setDestinationEmail("demo@example.test");
        NotificationService notifications = mock(NotificationService.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SpringTemplateEngine templates = mock(SpringTemplateEngine.class);
        AppProperties properties = mock(AppProperties.class);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));

        when(notifications.getById(id)).thenReturn(notification);
        when(notifications.claimForDelivery(id, NotificationChannel.EMAIL))
                .thenReturn(Optional.of(firstClaimId), Optional.of(secondClaimId));
        doAnswer(invocation -> {
            notification.setStatus(NotificationStatus.QUEUED);
            notification.setFailureReason(invocation.getArgument(3));
            return null;
        }).when(notifications).releaseDeliveryForRetry(id, NotificationChannel.EMAIL, firstClaimId,
                "EMAIL_DELIVERY_RETRY_SCHEDULED");
        doAnswer(invocation -> {
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(java.time.Instant.now());
            notification.setFailureReason(null);
            return null;
        }).when(notifications).completeDelivery(id, NotificationChannel.EMAIL, secondClaimId, null);
        when(properties.apiUrl()).thenReturn("https://api.example.test");
        when(templates.process(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any())).thenReturn("<p>Koala</p>");
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("SMTP unavailable")).doNothing().when(mailSender).send(mimeMessage);

        EmailDeliveryProperties delivery = new EmailDeliveryProperties();
        delivery.setAllowedRecipients(Set.of("demo@example.test"));
        EmailConsumer consumer = new EmailConsumer(notifications, mailSender, templates, properties, delivery);
        EmailTaskDto task = new EmailTaskDto(id, "demo@example.test", "Konu", "raw-html-template", Map.of());

        assertThrows(MailSendException.class, () -> consumer.handleEmailTask(task));
        assertEquals("EMAIL_DELIVERY_RETRY_SCHEDULED", notification.getFailureReason());

        consumer.handleEmailTask(task);

        assertEquals(NotificationStatus.SENT, notification.getStatus());
        assertNull(notification.getFailureReason());
        verify(notifications).completeDelivery(id, NotificationChannel.EMAIL, secondClaimId, null);
    }

    @Test
    void aCompletionFailureAfterSmtpSuccessDoesNotReleaseTheDeliveryLease() {
        UUID id = UUID.randomUUID();
        UUID claimId = UUID.randomUUID();
        Notification notification = new Notification();
        notification.setId(id);
        notification.setStatus(NotificationStatus.QUEUED);
        notification.setDestinationEmail("demo@example.test");
        NotificationService notifications = mock(NotificationService.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SpringTemplateEngine templates = mock(SpringTemplateEngine.class);
        AppProperties properties = mock(AppProperties.class);
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));

        when(notifications.getById(id)).thenReturn(notification);
        when(notifications.claimForDelivery(id, NotificationChannel.EMAIL)).thenReturn(Optional.of(claimId));
        when(properties.apiUrl()).thenReturn("https://api.example.test");
        when(templates.process(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any())).thenReturn("<p>Koala</p>");
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new DataAccessResourceFailureException("database unavailable"))
                .when(notifications).completeDelivery(id, NotificationChannel.EMAIL, claimId, null);

        EmailDeliveryProperties delivery = new EmailDeliveryProperties();
        delivery.setAllowedRecipients(Set.of("demo@example.test"));
        EmailConsumer consumer = new EmailConsumer(notifications, mailSender, templates, properties, delivery);
        EmailTaskDto task = new EmailTaskDto(id, "demo@example.test", "Konu", "raw-html-template", Map.of());

        assertThrows(DataAccessResourceFailureException.class, () -> consumer.handleEmailTask(task));

        verify(mailSender).send(mimeMessage);
        verify(notifications, never()).releaseDeliveryForRetry(id, NotificationChannel.EMAIL, claimId,
                "EMAIL_DELIVERY_RETRY_SCHEDULED");
    }

    @Test
    void aTransientNotificationLookupFailureEscapesSoRabbitCanRetry() {
        UUID id = UUID.randomUUID();
        NotificationService notifications = mock(NotificationService.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SpringTemplateEngine templates = mock(SpringTemplateEngine.class);
        AppProperties properties = mock(AppProperties.class);
        when(notifications.getById(id))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));
        EmailDeliveryProperties delivery = new EmailDeliveryProperties();
        EmailConsumer consumer = new EmailConsumer(notifications, mailSender, templates, properties, delivery);
        EmailTaskDto task = new EmailTaskDto(id, "demo@example.test", "Konu", "raw-html-template", Map.of());

        assertThrows(DataAccessResourceFailureException.class, () -> consumer.handleEmailTask(task));
        verifyNoInteractions(mailSender);
    }

    @Test
    void aTaskForAnotherChannelCannotMutateThatNotification() {
        UUID id = UUID.randomUUID();
        Notification notification = new Notification();
        notification.setId(id);
        notification.setChannel(NotificationChannel.SMS);
        notification.setStatus(NotificationStatus.QUEUED);
        notification.setDestinationEmail("victim@example.test");
        NotificationService notifications = mock(NotificationService.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(notifications.getById(id)).thenReturn(notification);
        when(notifications.claimForDelivery(id, NotificationChannel.EMAIL)).thenReturn(Optional.empty());

        EmailConsumer consumer = new EmailConsumer(notifications, mailSender, mock(SpringTemplateEngine.class),
                mock(AppProperties.class), new EmailDeliveryProperties());

        assertDoesNotThrow(() -> consumer.handleEmailTask(new EmailTaskDto(id, "attacker@example.test", "Konu",
                "raw-html-template", Map.of())));

        verify(notifications).claimForDelivery(id, NotificationChannel.EMAIL);
        verify(notifications, org.mockito.Mockito.never()).failDelivery(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(mailSender);
    }

    @Test
    void anExhaustedEmailIsRetriedTowardTheDlqWithoutCallingTheProviderAgain() {
        UUID id = UUID.randomUUID();
        Notification notification = new Notification();
        notification.setId(id);
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setStatus(NotificationStatus.FAILED);
        notification.setFailureReason("EMAIL_DELIVERY_EXHAUSTED");
        NotificationService notifications = mock(NotificationService.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(notifications.getById(id)).thenReturn(notification);
        EmailConsumer consumer = new EmailConsumer(notifications, mailSender, mock(SpringTemplateEngine.class),
                mock(AppProperties.class), new EmailDeliveryProperties());

        assertThrows(IllegalStateException.class, () -> consumer.handleEmailTask(new EmailTaskDto(id,
                "demo@example.test", "Konu", "raw-html-template", Map.of())));

        verify(notifications, org.mockito.Mockito.never()).claimForDelivery(id, NotificationChannel.EMAIL);
        verifyNoInteractions(mailSender);
    }
}
