package com.exskylab.koala.core.consumers;

import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.core.configs.RabbitMQConfig;
import com.exskylab.koala.core.producers.notification.PushTaskDto;
import com.exskylab.koala.core.utilities.push.PushService;
import com.exskylab.koala.entities.Notification;
import com.exskylab.koala.entities.NotificationStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Slf4j
public class PushConsumer {

    private final NotificationService notificationService;
    private final PushService pushService;

    public PushConsumer(NotificationService notificationService, PushService pushService) {
        this.notificationService = notificationService;
        this.pushService = pushService;
    }

    @RabbitListener(queues = RabbitMQConfig.PUSH_QUEUE)
    @Transactional
    public void handlePushTask(PushTaskDto pushTaskDto) {
        log.info("Handling push task for id: {}", pushTaskDto.id());

        Notification notification;
        try {
            notification = notificationService.getById(pushTaskDto.id());
        } catch (Exception e) {
            log.error("Notification not found to send push ID: {}. Aborting request.", pushTaskDto.id());
            return;
        }

        notification.setStatus(NotificationStatus.PROCESSING);
        notification.setProcessedAt(Instant.now());
        notificationService.save(notification);

        try {
            String messageId = pushService.sendPush(pushTaskDto.pushToken(), pushTaskDto.title(),
                    pushTaskDto.body(), pushTaskDto.dataPayload());

            notification.setExternalId(messageId);
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(Instant.now());
            log.info("Push task sent for id: {}", pushTaskDto.id());
        } catch (Exception e) {
            log.error("Push task could not be sent for id: {}, Error: {}", pushTaskDto.id(), e.getMessage());
            notification.setStatus(NotificationStatus.FAILED);
            notification.setFailureReason(truncate(e.getMessage()));
        } finally {
            notificationService.save(notification);
        }
    }

    private static String truncate(String reason) {
        return reason == null ? null : reason.substring(0, Math.min(reason.length(), 255));
    }
}
