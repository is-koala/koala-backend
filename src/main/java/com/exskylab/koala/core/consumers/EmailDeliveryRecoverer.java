package com.exskylab.koala.core.consumers;

import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.producers.notification.EmailTaskDto;
import com.exskylab.koala.entities.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
public class EmailDeliveryRecoverer implements MessageRecoverer {

    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final MessageRecoverer deadLetterRecoverer;

    public EmailDeliveryRecoverer(NotificationService notificationService, ObjectMapper objectMapper,
                                  MessageRecoverer deadLetterRecoverer) {
        this.notificationService = notificationService;
        this.objectMapper = objectMapper;
        this.deadLetterRecoverer = deadLetterRecoverer;
    }

    @Override
    public void recover(Message message, Throwable cause) {
        EmailTaskDto task;
        try {
            task = objectMapper.readValue(message.getBody(), EmailTaskDto.class);
        } catch (JacksonException exception) {
            log.warn("Exhausted email task payload could not be decoded before dead-lettering");
            deadLetterRecoverer.recover(message, cause);
            return;
        }
        if (wasRejectedBecauseNotificationIsMissing(cause)) {
            log.warn("Orphan email task {} will be dead-lettered without a notification state transition", task.id());
        } else {
            try {
                notificationService.terminalizeDelivery(task.id(), NotificationChannel.EMAIL,
                        "EMAIL_DELIVERY_EXHAUSTED");
            } catch (RuntimeException exception) {
                log.error("Exhausted email task {} could not be durably terminalized", task.id(), exception);
                throw exception;
            }
        }
        deadLetterRecoverer.recover(message, cause);
    }

    private boolean wasRejectedBecauseNotificationIsMissing(Throwable cause) {
        Throwable current = cause;
        while (current != null) {
            if (current instanceof AmqpRejectAndDontRequeueException
                    && current.getCause() instanceof ResourceNotFoundException) {
                return true;
            }
            if (current == current.getCause()) {
                return false;
            }
            current = current.getCause();
        }
        return false;
    }
}
