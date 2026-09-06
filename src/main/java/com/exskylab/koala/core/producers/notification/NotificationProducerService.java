package com.exskylab.koala.core.producers.notification;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.core.configs.RabbitMQConfig;
import com.exskylab.koala.core.constants.NotificationMessages;
import com.exskylab.koala.core.exceptions.ExternalServiceException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class NotificationProducerService {

    private final RabbitTemplate rabbitTemplate;

    public NotificationProducerService(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void sendEmailTask(EmailTaskDto emailTaskDto) {
        log.info("Sending email task to queue for id: {}", emailTaskDto.id());

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.NOTIFICATION_EXCHANGE,
                    RabbitMQConfig.EMAIL_ROUTING_KEY,
                    emailTaskDto
            );
            log.info("Email task sent to queue for id: {}", emailTaskDto.id());
        } catch (Exception e) {
            log.error("Email task could not be sent to queue for id: {}", emailTaskDto.id(), e);
            throw new ExternalServiceException(NotificationMessages.EMAIL_TASK_CANNOT_BE_SENT_TO_QUEUE);

        }
    }

    public void sendSmsTask(SmsTaskDto smsTaskDto) {
        log.info("Sending sms task to queue for id: {}", smsTaskDto.id());

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.NOTIFICATION_EXCHANGE,
                    RabbitMQConfig.SMS_ROUTING_KEY,
                    smsTaskDto
            );
            log.info("Sms task sent to queue for id: {}", smsTaskDto.id());
        } catch (Exception e) {
            log.error("Sms task could not be sent to queue for id: {}", smsTaskDto.id(), e);

            throw new ExternalServiceException(NotificationMessages.SMS_TASK_CANNOT_BE_SENT_TO_QUEUE);
        }
    }

    public void sendPushTask(PushTaskDto pushTaskDto) {
        log.info("Sending push task to queue for id: {}", pushTaskDto.id());

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.NOTIFICATION_EXCHANGE,
                    RabbitMQConfig.PUSH_ROUTING_KEY,
                    pushTaskDto
            );
            log.info("Push task sent to queue for id: {}", pushTaskDto.id());
        } catch (Exception e) {
            log.error("Push task could not be sent to queue for id: {}", pushTaskDto.id(), e);

            throw new ExternalServiceException(NotificationMessages.PUSH_TASK_CANNOT_BE_SENT_TO_QUEUE);
        }
    }

}
