package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.business.abstracts.PhoneVerificationService;
import com.exskylab.koala.core.dtos.notification.request.SendSmsDto;
import com.exskylab.koala.core.helpers.LogMasks;
import com.exskylab.koala.core.helpers.MessageResolver;
import com.exskylab.koala.core.constants.VerificationMessages;
import com.exskylab.koala.dataAccess.PhoneVerificationDao;
import com.exskylab.koala.entities.DispatchPriority;
import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.PhoneVerification;
import com.exskylab.koala.entities.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@Slf4j
public class PhoneVerificationManager implements PhoneVerificationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int EXPIRY_MINUTES = 15;

    private final PhoneVerificationDao phoneVerificationDao;
    private final NotificationService notificationService;
    private final MessageResolver messageResolver;

    public PhoneVerificationManager(PhoneVerificationDao phoneVerificationDao,
                                    NotificationService notificationService,
                                    MessageResolver messageResolver) {
        this.phoneVerificationDao = phoneVerificationDao;
        this.notificationService = notificationService;
        this.messageResolver = messageResolver;
    }

    @Override
    @Transactional
    public PhoneVerification startPhoneVerification(String newPhoneNumber, User user) {
        log.debug("Starting phone verification for user {}", user.getId());

        String verificationCode = String.valueOf(100000 + SECURE_RANDOM.nextInt(900000));

        PhoneVerification verification = new PhoneVerification();
        verification.setUser(user);
        verification.setVerified(false);
        verification.setOldPhoneNumber(user.getPhoneNumber());
        verification.setNewPhoneNumber(newPhoneNumber);
        verification.setToken(verificationCode);
        verification.setExpiresAt(Instant.now().plus(EXPIRY_MINUTES, ChronoUnit.MINUTES));

        PhoneVerification saved = phoneVerificationDao.save(verification);

        SendSmsDto sms = new SendSmsDto();
        sms.setRecipient(user);
        sms.setCategory(NotificationCategory.ACCOUNT_SECURITY);
        sms.setDestinationPhoneNumber(newPhoneNumber);
        sms.setMessageBody(messageResolver.resolve(VerificationMessages.PHONE_CODE_SMS) + " " + verificationCode);

        notificationService.sendSms(sms, DispatchPriority.CRITICAL, true);

        log.info("Phone verification {} started for user {} to {}",
                saved.getId(), user.getId(), LogMasks.phone(newPhoneNumber));
        return saved;
    }
}
