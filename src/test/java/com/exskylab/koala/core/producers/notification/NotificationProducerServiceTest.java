package com.exskylab.koala.core.producers.notification;

import com.exskylab.koala.core.configs.RabbitMQConfig;
import com.exskylab.koala.core.exceptions.ExternalServiceException;
import com.exskylab.koala.core.producers.ConfirmedRabbitPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class NotificationProducerServiceTest {

    private ConfirmedRabbitPublisher publisher;
    private NotificationProducerService producer;

    @BeforeEach
    void setUp() {
        publisher = mock(ConfirmedRabbitPublisher.class);
        producer = new NotificationProducerService(publisher);
    }

    @Test
    void emailUsesItsExactRoutingKeyAndWaitsForAnAck() {
        EmailTaskDto task = new EmailTaskDto(UUID.randomUUID(), "mail@koala.test", "Exact subject",
                "exact-template", Map.of("name", "Ada"));
        producer.sendEmailTask(task);

        verify(publisher).convertAndPublish(RabbitMQConfig.NOTIFICATION_EXCHANGE,
                RabbitMQConfig.EMAIL_ROUTING_KEY, task);
    }

    @Test
    void smsUsesItsExactRoutingKeyAndWaitsForAnAck() {
        SmsTaskDto task = new SmsTaskDto(UUID.randomUUID(), "+905551112233", "raw-sms-template",
                Map.of("textContent", "Kod 123456"));
        producer.sendSmsTask(task);

        verify(publisher).convertAndPublish(RabbitMQConfig.NOTIFICATION_EXCHANGE,
                RabbitMQConfig.SMS_ROUTING_KEY, task);
    }

    @Test
    void pushUsesItsExactRoutingKeyAndWaitsForAnAck() {
        PushTaskDto task = new PushTaskDto(UUID.randomUUID(), "token-7", "Exact title", "Exact body",
                Map.of("jobId", "job-7"));
        producer.sendPushTask(task);

        verify(publisher).convertAndPublish(RabbitMQConfig.NOTIFICATION_EXCHANGE,
                RabbitMQConfig.PUSH_ROUTING_KEY, task);
    }

    @Test
    void anAcknowledgedButUnroutablePublishIsReportedAsFailure() {
        org.mockito.Mockito.doThrow(new IllegalStateException("unroutable"))
                .when(publisher).convertAndPublish(anyString(), anyString(), any());

        EmailTaskDto task = new EmailTaskDto(UUID.randomUUID(), "mail@koala.test", "Subject",
                "template", Map.of());

        assertThrows(ExternalServiceException.class, () -> producer.sendEmailTask(task));
    }

}
