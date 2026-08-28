package com.exskylab.koala.core.consumers;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.core.configs.RabbitMQConfig;
import com.exskylab.koala.core.producers.notification.EmailTaskDto;
import com.exskylab.koala.core.properties.AppProperties;
import com.exskylab.koala.entities.Notification;
import com.exskylab.koala.entities.NotificationStatus;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.time.Instant;

@Service
@Slf4j
public class EmailConsumer {

    private AppProperties appProperties;
    private final NotificationService notificationService;
    private final JavaMailSender javaMailSender;
    private final SpringTemplateEngine templateEngine;

    public EmailConsumer(NotificationService notificationService, JavaMailSender javaMailSender, SpringTemplateEngine templateEngine, AppProperties appProperties) {
        this.notificationService = notificationService;
        this.javaMailSender = javaMailSender;
        this.templateEngine = templateEngine;
        this.appProperties = appProperties;
    }


    @RabbitListener(queues = RabbitMQConfig.EMAIL_QUEUE)
    @Transactional
    public void handleEmailTask(EmailTaskDto emailTaskDto){
        log.info("Handling email task for id: {}", emailTaskDto.id());


        Notification notification;
        try{
            notification = notificationService.getById(emailTaskDto.id());
        }catch (Exception e){
            log.error("Notification not found to send mail ID: {}. Aborting request.", emailTaskDto.id(), e.getMessage());
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
            log.error("Email task could not be sent for id: {}, Error: {}", emailTaskDto.id(), e.getMessage());
            notification.setStatus(NotificationStatus.FAILED);
            notification.setFailureReason(e.getMessage().substring(0, Math.min(e.getMessage().length(), 255)));
        }finally {
            notificationService.save(notification);
        }

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
