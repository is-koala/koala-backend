package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.core.helpers.LogMasks;
import com.exskylab.koala.business.abstracts.EmailVerificationService;
import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.core.dtos.notification.request.SendEmailDto;
import com.exskylab.koala.dataAccess.EmailVerificationDao;
import com.exskylab.koala.entities.DispatchPriority;
import com.exskylab.koala.entities.EmailVerification;
import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@Slf4j
public class EmailVerificationManager implements EmailVerificationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final EmailVerificationDao emailVerificationDao;
    private final NotificationService notificationService;


    public EmailVerificationManager(EmailVerificationDao emailVerificationDao, NotificationService notificationService) {
        this.emailVerificationDao = emailVerificationDao;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public EmailVerification startEmailVerification(String newEmail, User user) {
        log.debug("Starting email verification for user {}", user.getId());

        if (existsByNewEmailAndNotVerified(newEmail)) {
            emailVerificationDao.deleteByNewEmail(newEmail);
            log.debug("Deleted existing unverified email verification.");
        }

        EmailVerification emailVerification = new EmailVerification();
        emailVerification.setVerified(false);
        emailVerification.setUser(user);
        emailVerification.setNewEmail(newEmail);
        emailVerification.setOldEmail(user.getEmail());
        int verificationCode = 100000 + SECURE_RANDOM.nextInt(900000);
        emailVerification.setToken(String.valueOf(verificationCode));
        emailVerification.setExpiresAt(Instant.now().plus(15, ChronoUnit.MINUTES));
        emailVerification = emailVerificationDao.save(emailVerification);

        log.info("Email verification started successfully for user with id: {}", user.getId());

        SendEmailDto newEmailDto = new SendEmailDto();
        newEmailDto.setRecipient(user);
        newEmailDto.setCategory(NotificationCategory.ACCOUNT_SECURITY);
        newEmailDto.setDestinationEmail(newEmail);
        newEmailDto.setTemplateName("email-verification-template");
        newEmailDto.setTemplateParameters(
                java.util.Map.of(
                        "firstName", user.getFirstName(),
                        "lastName", user.getLastName(),
                        "email", newEmail,
                        "verificationCode", verificationCode
                )
        );

        notificationService.sendEmail(newEmailDto, DispatchPriority.HIGH, true);

        log.info("Email verification mail queued for {}", LogMasks.email(newEmail));
        return emailVerification;
    }

    private boolean existsByNewEmailAndNotVerified(String newEmail) {
        return emailVerificationDao.existsByNewEmailAndVerifiedFalse(newEmail);
    }
}
