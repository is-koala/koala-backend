package com.exskylab.koala.core.producers;

import com.exskylab.koala.core.properties.NotificationDispatchProperties;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Component
public class ConfirmedRabbitPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final NotificationDispatchProperties dispatchProperties;

    public ConfirmedRabbitPublisher(RabbitTemplate rabbitTemplate,
                                    NotificationDispatchProperties dispatchProperties) {
        this.rabbitTemplate = rabbitTemplate;
        this.dispatchProperties = dispatchProperties;
    }

    public void convertAndPublish(String exchange, String routingKey, Object payload) {
        convertAndPublish(exchange, routingKey, payload, dispatchProperties.getPublisherConfirmTimeout());
    }

    public void convertAndPublish(String exchange, String routingKey, Object payload, Duration confirmTimeout) {
        CorrelationData correlation = new CorrelationData();
        rabbitTemplate.convertAndSend(exchange, routingKey, payload, correlation);
        awaitRoutedConfirmation(correlation, confirmTimeout);
    }

    public void publish(String exchange, String routingKey, Message message) {
        CorrelationData correlation = new CorrelationData();
        rabbitTemplate.send(exchange, routingKey, message, correlation);
        awaitRoutedConfirmation(correlation, dispatchProperties.getPublisherConfirmTimeout());
    }

    private void awaitRoutedConfirmation(CorrelationData correlation, Duration confirmTimeout) {
        try {
            CorrelationData.Confirm confirm = correlation.getFuture().get(
                    confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.ack() || correlation.getReturned() != null) {
                throw new IllegalStateException("Rabbit publish was not confirmed and routed");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Rabbit publish confirmation was interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Rabbit publish confirmation failed", exception);
        }
    }
}
