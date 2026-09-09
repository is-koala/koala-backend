package com.exskylab.koala.core.consumers;

import com.exskylab.koala.business.abstracts.PushDeliveryService;
import com.exskylab.koala.core.configs.RabbitMQConfig;
import com.exskylab.koala.core.producers.notification.PushTaskDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
@Slf4j
public class PushConsumer {

    private final PushDeliveryService pushDeliveryService;

    public PushConsumer(PushDeliveryService pushDeliveryService) {
        this.pushDeliveryService = pushDeliveryService;
    }

    @RabbitListener(queues = RabbitMQConfig.PUSH_QUEUE, containerFactory = "pushListenerContainerFactory")
    public void handlePushTask(PushTaskDto pushTaskDto) {
        PushTaskDto task = Objects.requireNonNull(pushTaskDto, "Push task is required");
        UUID id = Objects.requireNonNull(task.id(), "Push task notification ID is required");
        log.info("Handling push task for id: {}", id);
        pushDeliveryService.deliver(id);
    }
}
