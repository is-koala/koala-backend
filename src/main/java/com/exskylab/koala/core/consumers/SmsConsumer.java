package com.exskylab.koala.core.consumers;

import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.core.configs.RabbitMQConfig;
import com.exskylab.koala.core.constants.SmsMessages;
import com.exskylab.koala.core.producers.notification.SmsTaskDto;
import com.exskylab.koala.core.utilities.sms.SmsService;
import com.exskylab.koala.entities.Notification;
import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.NotificationStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class SmsConsumer {

    private static final String RAW_TEMPLATE = "raw-sms-template";
    private static final String RAW_TEXT_PARAMETER = "textContent";

    private static final Set<NotificationCategory> COMMERCIAL =
            EnumSet.of(NotificationCategory.MARKETINGS, NotificationCategory.NEWSLETTER);

    private final NotificationService notificationService;
    private final SmsService smsService;

    public SmsConsumer(NotificationService notificationService, SmsService smsService) {
        this.notificationService = notificationService;
        this.smsService = smsService;
    }

    @RabbitListener(queues = RabbitMQConfig.SMS_QUEUE)
    @Transactional
    public void handleSmsTask(SmsTaskDto smsTaskDto) {
        log.info("Handling sms task for id: {}", smsTaskDto.id());

        Notification notification;
        try {
            notification = notificationService.getById(smsTaskDto.id());
        } catch (Exception e) {
            log.error("Notification not found to send sms ID: {}. Aborting request.", smsTaskDto.id(), e);
            return;
        }

        notification.setStatus(NotificationStatus.PROCESSING);
        notification.setProcessedAt(Instant.now());
        notificationService.save(notification);

        try {
            String body = renderBody(smsTaskDto);
            boolean commercial = COMMERCIAL.contains(notification.getCategory());

            String orderId = smsService.sendSms(new String[]{smsTaskDto.to()}, body, commercial);

            notification.setExternalId(orderId);
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(Instant.now());
            log.info("Sms task sent for id: {}", smsTaskDto.id());
        } catch (Exception e) {
            log.error("Sms task could not be sent for id: {}", smsTaskDto.id(), e);
            notification.setStatus(NotificationStatus.FAILED);
            notification.setFailureReason(truncate(e.getMessage()));
        } finally {
            notificationService.save(notification);
        }
    }

    private String renderBody(SmsTaskDto smsTaskDto) {
        if (!RAW_TEMPLATE.equals(smsTaskDto.templateName())) {
            throw new IllegalArgumentException("Unknown sms template: " + smsTaskDto.templateName());
        }

        Map<String, Object> parameters = smsTaskDto.templateParameters();
        Object text = parameters == null ? null : parameters.get(RAW_TEXT_PARAMETER);

        if (text == null || text.toString().isBlank()) {
            throw new IllegalArgumentException(SmsMessages.SMS_BODY_MISSING);
        }

        return text.toString();
    }

    private static String truncate(String reason) {
        return reason == null ? null : reason.substring(0, Math.min(reason.length(), 255));
    }
}
