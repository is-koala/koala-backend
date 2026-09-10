package com.exskylab.koala.core.configs;

import com.exskylab.koala.core.properties.PushDeliveryProperties;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PushRabbitTopologyTest {

    @Test
    void existingPushQueueKeepsItsArgumentFreeDeclaration() {
        RabbitMQConfig config = new RabbitMQConfig();

        Queue queue = config.pushQueue();

        assertEquals("push.queue", queue.getName());
        assertTrue(queue.isDurable());
        assertTrue(queue.getArguments().isEmpty());
    }

    @Test
    void unexpectedListenerFailuresHaveABoundedRepublishDlq() {
        RabbitMQConfig config = new RabbitMQConfig();

        Queue deadLetterQueue = config.pushDeadLetterQueue();
        Binding binding = config.pushDeadLetterBinding();

        assertEquals("push.queue.dlq", deadLetterQueue.getName());
        assertEquals("notification.push.dead-letter", binding.getRoutingKey());
        assertNotNull(config.pushRetryInterceptor(mock(RabbitTemplate.class), new PushDeliveryProperties()));
    }
}
