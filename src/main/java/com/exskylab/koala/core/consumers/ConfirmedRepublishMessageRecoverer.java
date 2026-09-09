package com.exskylab.koala.core.consumers;

import com.exskylab.koala.core.producers.ConfirmedRabbitPublisher;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;

public class ConfirmedRepublishMessageRecoverer extends RepublishMessageRecoverer {

    private final ConfirmedRabbitPublisher publisher;

    public ConfirmedRepublishMessageRecoverer(RabbitTemplate rabbitTemplate, ConfirmedRabbitPublisher publisher,
                                               String exchange, String routingKey) {
        super(rabbitTemplate, exchange, routingKey);
        this.publisher = publisher;
    }

    @Override
    protected void doSend(String exchange, String routingKey, Message message) {
        publisher.publish(exchange, routingKey, message);
    }
}
