package com.exskylab.koala.core.producers;

import com.exskylab.koala.core.properties.NotificationDispatchProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class ConfirmedRabbitPublisherTest {

    private RabbitTemplate rabbit;
    private ConfirmedRabbitPublisher publisher;

    @BeforeEach
    void setUp() {
        rabbit = mock(RabbitTemplate.class);
        NotificationDispatchProperties properties = new NotificationDispatchProperties();
        properties.setPublisherConfirmTimeout(Duration.ofMillis(50));
        publisher = new ConfirmedRabbitPublisher(rabbit, properties);
    }

    @Test
    void aConfirmedAndRoutedConvertedMessageSucceeds() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).convertAndSend(anyString(), anyString(), any(Object.class), any(CorrelationData.class));

        assertDoesNotThrow(() -> publisher.convertAndPublish("exchange", "route", "body"));
    }

    @Test
    void aConfirmedButReturnedRawDeadLetterFails() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.setReturned(new ReturnedMessage(invocation.getArgument(2), 312, "NO_ROUTE",
                    "dead-letter", "missing"));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));

        assertThrows(IllegalStateException.class,
                () -> publisher.publish("dead-letter", "missing", new Message(new byte[0])));
    }

    @Test
    void aPublisherNackFails() {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.getFuture().complete(new CorrelationData.Confirm(false, "broker nack"));
            return null;
        }).when(rabbit).convertAndSend(anyString(), anyString(), any(Object.class), any(CorrelationData.class));

        assertThrows(IllegalStateException.class,
                () -> publisher.convertAndPublish("exchange", "route", "body"));
    }

    @Test
    void aMissingConfirmationIsBoundedByTimeout() {
        assertThrows(IllegalStateException.class,
                () -> publisher.convertAndPublish("exchange", "route", "body"));
    }
}
