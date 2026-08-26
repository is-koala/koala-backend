package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.PhoneVerificationService;
import com.exskylab.koala.core.utilities.sms.SmsService;
import com.exskylab.koala.dataAccess.PhoneVerificationDao;
import com.exskylab.koala.entities.PhoneVerification;
import com.exskylab.koala.entities.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;

@Service
public class PhoneVerificationManager implements PhoneVerificationService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final PhoneVerificationDao phoneVerificationDao;

    private final SmsService smsService;

    private final Logger logger = LoggerFactory.getLogger(PhoneVerificationManager.class);

    public PhoneVerificationManager(PhoneVerificationDao phoneVerificationDao, SmsService smsService) {
        this.phoneVerificationDao = phoneVerificationDao;
        this.smsService = smsService;
    }

    @Override
    public PhoneVerification startPhoneVerification(String newPhoneNumber, User user) {
        logger.info("Starting phone verification for user with id: {}, phoneNumber: {}", user.getId(), newPhoneNumber);

        int code = 100000 + RANDOM.nextInt(900000);
        String verificationCode = String.valueOf(code);


        //TODO IMPLEMENT THESE SMS LOGICS AFTER CREATING COMPANY


        return null;


    }
}
