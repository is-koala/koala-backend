package com.exskylab.koala.core.utilities.mail;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.core.constants.EmailMessages;
import com.exskylab.koala.core.exceptions.ExternalServiceException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import com.exskylab.koala.core.helpers.LogMasks;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class JavaMailSenderManager implements EmailService {

    private final JavaMailSender javaMailsender;

    public JavaMailSenderManager(JavaMailSender javaMailsender) {
        this.javaMailsender = javaMailsender;
    }

    @Value("${spring.mail.username}")
    private String from;


    @Override
    public boolean sendMail(String to, String subject, String body) {

        MimeMessage mimeMessage = javaMailsender.createMimeMessage();

        try{
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);
            javaMailsender.send(mimeMessage);
            return true;
        }catch (Exception e){
            log.error("Mail delivery failed for {}: {}", LogMasks.email(to), e.getMessage(), e);
            throw new ExternalServiceException(EmailMessages.EMAIL_SEND_FAILED);
        }


    }
}