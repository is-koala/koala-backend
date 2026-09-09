package com.exskylab.koala.core.consumers;

import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.core.configs.RabbitMQConfig;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.producers.notification.PushTaskDto;
import com.exskylab.koala.core.properties.NotificationDispatchProperties;
import com.exskylab.koala.core.utilities.push.PushService;
import com.exskylab.koala.entities.Notification;
import com.exskylab.koala.entities.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
public class PushConsumer {

    private final NotificationService notificationService;
    private final PushService pushService;
    private final NotificationDispatchProperties dispatchProperties;

    public PushConsumer(NotificationService notificationService, PushService pushService,
                        NotificationDispatchProperties dispatchProperties) {
        this.notificationService = notificationService;
        this.pushService = pushService;
        this.dispatchProperties = dispatchProperties;
    }

    @RabbitListener(queues = RabbitMQConfig.PUSH_QUEUE)
    public void handlePushTask(PushTaskDto pushTaskDto) {
        log.info("Handling push task for id: {}", pushTaskDto.id());

        Notification notification;
        try {
            notification = notificationService.getById(pushTaskDto.id());
        } catch (ResourceNotFoundException e) {
            log.error("Notification not found to send push ID: {}. Aborting request.", pushTaskDto.id(), e);
            return;
        }

        var claim = notificationService.claimForDelivery(pushTaskDto.id(), NotificationChannel.PUSH);
        if (claim.isEmpty()) {
            return;
        }
        UUID claimId = claim.orElseThrow();

        String messageId;
        try {
            messageId = pushService.sendPush(pushTaskDto.pushToken(), pushTaskDto.title(),
                    pushTaskDto.body(), pushTaskDto.dataPayload(), dispatchProperties.getProviderTimeout());
        } catch (Exception e) {
            log.error("Push task could not be sent for id: {}", pushTaskDto.id(), e);
            notificationService.failDelivery(pushTaskDto.id(), NotificationChannel.PUSH, claimId,
                    truncate(e.getMessage()));
            return;
        }
        notificationService.completeDelivery(pushTaskDto.id(), NotificationChannel.PUSH, claimId, messageId);
        log.info("Push task sent for id: {}", pushTaskDto.id());
    }

    private static String truncate(String reason) {
        return reason == null ? null : reason.substring(0, Math.min(reason.length(), 255));
    }
}
