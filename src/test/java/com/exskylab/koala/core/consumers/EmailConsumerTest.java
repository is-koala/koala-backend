package com.exskylab.koala.core.consumers;

import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.core.producers.notification.EmailTaskDto;
import com.exskylab.koala.core.properties.AppProperties;
import com.exskylab.koala.core.properties.EmailDeliveryProperties;
import com.exskylab.koala.entities.Notification;
import com.exskylab.koala.entities.NotificationStatus;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailConsumerTest {

    @Test
    void smtpFailuresEscapeTheListenerSoRabbitCanRetry() {
        UUID id = UUID.randomUUID();
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
        assertEquals(NotificationStatus.FAILED, notification.getStatus());
        verify(notifications, times(2)).save(notification);
    }
}
