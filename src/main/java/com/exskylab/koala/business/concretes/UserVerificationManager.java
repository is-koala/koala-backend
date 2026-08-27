package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.business.abstracts.UserVerificationService;
import com.exskylab.koala.core.constants.UserVerificationMessages;
import com.exskylab.koala.core.dtos.userVerification.request.VerificationRequestDto;
import com.exskylab.koala.core.exceptions.UserVerificationNotAssociatedWithUserException;
import com.exskylab.koala.core.exceptions.UserVerificationNotFoundException;
import com.exskylab.koala.dataAccess.UserVerificationDao;
import com.exskylab.koala.entities.EmailVerification;
import com.exskylab.koala.entities.PhoneVerification;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.entities.UserVerification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class UserVerificationManager implements UserVerificationService {

    private final UserVerificationDao userVerificationDao;
    private final UserService userService;
    private final SecurityService securityService;

    private final Logger logger = LoggerFactory.getLogger(UserVerificationManager.class);

    public UserVerificationManager(UserVerificationDao userVerificationDao, UserService userService, SecurityService securityService) {
        this.userVerificationDao = userVerificationDao;
        this.userService = userService;
        this.securityService = securityService;
    }


    @Override
    @Transactional
    public UserVerification verify(UUID verificationId, VerificationRequestDto verificationRequestDto) {
        logger.info("Verifying verification with ID: {}", verificationId);

        UserVerification userVerification = userVerificationDao.findById(verificationId).orElseThrow(() -> {
            logger.error("UserVerification not found with ID: {}", verificationId);
            return new UserVerificationNotFoundException(UserVerificationMessages.USER_VERIFICATION_NOT_FOUND_BY_ID);
        });

        User currentUser = securityService.getAuthenticatedUser();
        if (!userVerification.getUser().getId().equals(currentUser.getId())) {
            logger.error("Verification ID: {} does not belong to the authenticated user", verificationId);
            throw new UserVerificationNotAssociatedWithUserException(UserVerificationMessages.VERIFICATION_NOT_ASSOCIATED_WITH_USER);
        }

        if (userVerification.isVerified()) {
            logger.warn("Verification ID: {} is already verified", verificationId);
            return userVerification;
        }

        if (userVerification instanceof PhoneVerification phoneVerification){
            if (!phoneVerification.getToken().equals(verificationRequestDto.getToken())) {
                logger.error("Invalid token provided for PhoneVerification ID: {}", verificationId);
                throw new IllegalArgumentException(UserVerificationMessages.INVALID_TOKEN);
            }

            if (phoneVerification.getExpiresAt().isBefore(Instant.now())) {
                logger.error("Token for PhoneVerification ID: {} has expired", verificationId);
                throw new IllegalArgumentException(UserVerificationMessages.TOKEN_EXPIRED);
            }
        }

        if (userVerification instanceof EmailVerification emailVerification){
            if (!emailVerification.getToken().equals(verificationRequestDto.getToken())) {
                logger.error("Invalid token provided for EmailVerification ID: {}", verificationId);
                throw new IllegalArgumentException(UserVerificationMessages.INVALID_TOKEN);
            }

            if (emailVerification.getExpiresAt().isBefore(Instant.now())) {
                logger.error("Token for EmailVerification ID: {} has expired", verificationId);
                throw new IllegalArgumentException(UserVerificationMessages.TOKEN_EXPIRED);
            }
        }

        userVerification.setVerified(true);
        userVerification.setVerifiedBy(currentUser);
        userVerification.setVerifiedAt(Instant.now());
        var savedVerification = userVerificationDao.save(userVerification);
        if (userVerification instanceof PhoneVerification phoneVerification) {
            logger.info("Updating user phone number to: {}", phoneVerification.getNewPhoneNumber());
            userService.updatePhoneVerification(currentUser, phoneVerification.getNewPhoneNumber());
        }else if (userVerification instanceof EmailVerification emailVerification){
            logger.info("Applying verified email change for user id: {}", emailVerification.getUser().getId());
            userService.updateEmailVerification(currentUser, emailVerification.getNewEmail());
        }

        logger.info("Verification ID: {} successfully verified", verificationId);

        return savedVerification;

    }
}
