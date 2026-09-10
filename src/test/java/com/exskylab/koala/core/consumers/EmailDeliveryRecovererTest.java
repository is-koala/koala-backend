package com.exskylab.koala.core.consumers;

import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.core.producers.notification.EmailTaskDto;
import com.exskylab.koala.entities.NotificationChannel;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.dao.DataAccessResourceFailureException;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class EmailDeliveryRecovererTest {

    @Test
    void terminalizationFailureEscapesWithoutAcknowledgingOrDeadLetteringTheMessage() {
        UUID id = UUID.randomUUID();
        NotificationService notifications = mock(NotificationService.class);
        MessageRecoverer deadLetter = mock(MessageRecoverer.class);
        ObjectMapper objectMapper = new ObjectMapper();
        EmailTaskDto task = new EmailTaskDto(id, "retry@example.test", "Konu",
                "raw-html-template", Map.of("htmlContent", "<p>Koala</p>"));
        Message message = new Message(objectMapper.writeValueAsBytes(task), new MessageProperties());
        doThrow(new DataAccessResourceFailureException("database unavailable"))
                .when(notifications).terminalizeDelivery(id, NotificationChannel.EMAIL,
                        "EMAIL_DELIVERY_EXHAUSTED");

        EmailDeliveryRecoverer recoverer = new EmailDeliveryRecoverer(notifications, objectMapper, deadLetter);

        assertThrows(DataAccessResourceFailureException.class,
                () -> recoverer.recover(message, new IllegalStateException("SMTP unavailable")));
        verify(notifications).terminalizeDelivery(id, NotificationChannel.EMAIL,
                "EMAIL_DELIVERY_EXHAUSTED");
        verifyNoInteractions(deadLetter);
    }

    @Test
    void aDeadLetterPublishFailureEscapesAfterDurableTerminalization() {
        UUID id = UUID.randomUUID();
        NotificationService notifications = mock(NotificationService.class);
        MessageRecoverer deadLetter = mock(MessageRecoverer.class);
        ObjectMapper objectMapper = new ObjectMapper();
        EmailTaskDto task = new EmailTaskDto(id, "retry@example.test", "Konu",
                "raw-html-template", Map.of("htmlContent", "<p>Koala</p>"));
        Message message = new Message(objectMapper.writeValueAsBytes(task), new MessageProperties());
        IllegalStateException failure = new IllegalStateException("DLQ route missing");
        doThrow(failure).when(deadLetter).recover(message, failure);

        EmailDeliveryRecoverer recoverer = new EmailDeliveryRecoverer(notifications, objectMapper, deadLetter);

        assertThrows(IllegalStateException.class, () -> recoverer.recover(message, failure));
        verify(notifications).terminalizeDelivery(id, NotificationChannel.EMAIL,
                "EMAIL_DELIVERY_EXHAUSTED");
        verify(deadLetter).recover(message, failure);
    }
}
