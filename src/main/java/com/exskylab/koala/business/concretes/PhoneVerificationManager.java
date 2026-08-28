package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.business.abstracts.PhoneVerificationService;
import com.exskylab.koala.core.utilities.sms.SmsService;
import com.exskylab.koala.dataAccess.PhoneVerificationDao;
import com.exskylab.koala.entities.PhoneVerification;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.core.helpers.LogMasks;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
@Slf4j
public class PhoneVerificationManager implements PhoneVerificationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PhoneVerificationDao phoneVerificationDao;

    private final SmsService smsService;

    public PhoneVerificationManager(PhoneVerificationDao phoneVerificationDao, SmsService smsService) {
        this.phoneVerificationDao = phoneVerificationDao;
        this.smsService = smsService;
    }

    @Override
    public PhoneVerification startPhoneVerification(String newPhoneNumber, User user) {
        log.debug("Starting phone verification for user {} to {}", user.getId(), LogMasks.phone(newPhoneNumber));

        int code = 100000 + RANDOM.nextInt(900000);
        String verificationCode = String.valueOf(code);


        //TODO IMPLEMENT THESE SMS LOGICS AFTER CREATING COMPANY


        return null;


    }
}
