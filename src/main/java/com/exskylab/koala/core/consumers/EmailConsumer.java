package com.exskylab.koala.core.consumers;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.core.configs.RabbitMQConfig;
import com.exskylab.koala.core.producers.notification.EmailTaskDto;
import com.exskylab.koala.core.properties.AppProperties;
import com.exskylab.koala.core.properties.EmailDeliveryProperties;
import com.exskylab.koala.entities.Notification;
import com.exskylab.koala.entities.NotificationStatus;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.time.Instant;

@Service
@Slf4j
public class EmailConsumer {

    private final AppProperties appProperties;
    private final EmailDeliveryProperties deliveryProperties;
    private final NotificationService notificationService;
    private final JavaMailSender javaMailSender;
    private final SpringTemplateEngine templateEngine;

    public EmailConsumer(NotificationService notificationService, JavaMailSender javaMailSender, SpringTemplateEngine templateEngine, AppProperties appProperties, EmailDeliveryProperties deliveryProperties) {
        this.notificationService = notificationService;
        this.javaMailSender = javaMailSender;
        this.templateEngine = templateEngine;
        this.appProperties = appProperties;
        this.deliveryProperties = deliveryProperties;
    }


    @RabbitListener(
            queues = RabbitMQConfig.EMAIL_QUEUE,
            containerFactory = "emailListenerContainerFactory")
    public void handleEmailTask(EmailTaskDto emailTaskDto){
        log.info("Handling email task for id: {}", emailTaskDto.id());


        Notification notification;
        try{
            notification = notificationService.getById(emailTaskDto.id());
        }catch (Exception e){
            log.error("Notification not found to send mail ID: {}. Aborting request.", emailTaskDto.id(), e);
            throw new AmqpRejectAndDontRequeueException(
                    "Email notification does not exist: " + emailTaskDto.id(), e);
        }

        if (notification.getSentAt() != null || notification.getStatus() == NotificationStatus.SUPPRESSED) {
            return;
        }

        if (notification.getDestinationEmail() == null || emailTaskDto.to() == null
                || !notification.getDestinationEmail().strip().equalsIgnoreCase(emailTaskDto.to().strip())) {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setProcessedAt(Instant.now());
            notification.setFailureReason("EMAIL_RECIPIENT_MISMATCH");
            notificationService.save(notification);
            throw new AmqpRejectAndDontRequeueException("Email task recipient does not match its notification");
        }

        if (!deliveryProperties.allows(emailTaskDto.to())) {
            notification.setStatus(NotificationStatus.SUPPRESSED);
            notification.setProcessedAt(Instant.now());
            notification.setFailureReason("EMAIL_RECIPIENT_NOT_ALLOWED");
            notificationService.save(notification);
            log.info("Email delivery suppressed for notification id: {}", notification.getId());
            return;
        }

        notification.setStatus(NotificationStatus.PROCESSING);
        notification.setProcessedAt(Instant.now());
        notificationService.save(notification);

        try{
            String trackingUrl = appProperties.apiUrl() + "/notification-tracking/" + emailTaskDto.id() + ".png";
            Context context = new Context();
            context.setVariables(emailTaskDto.templateParameters());
            context.setVariable("trackingUrl", trackingUrl);
            String htmlBody = templateEngine.process("emails/"+emailTaskDto.templateName(), context);

            sendHtmlEmail(emailTaskDto.to(), emailTaskDto.subject(), htmlBody);

            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(Instant.now());
            log.info("Email task sent for id: {}", emailTaskDto.id());
        }catch (Exception e){
            log.error("Email task could not be sent for id: {}", emailTaskDto.id(), e);
            notification.setStatus(NotificationStatus.FAILED);
            String failureMessage = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            notification.setFailureReason(failureMessage.substring(0, Math.min(failureMessage.length(), 255)));
            notificationService.save(notification);
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Email delivery failed", e);
        }

        notificationService.save(notification);

    }

    private void sendHtmlEmail(String to, String subject, String htmlBody) throws MessagingException {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(htmlBody, true);
        javaMailSender.send(mimeMessage);
    }
}
