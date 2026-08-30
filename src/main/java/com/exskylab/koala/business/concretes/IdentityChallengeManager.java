package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.IdentityChallengeService;
import com.exskylab.koala.core.constants.IdentityMessages;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.properties.IdentityVerificationProperties;
import com.exskylab.koala.core.utilities.identity.ActiveAuthentication;
import com.exskylab.koala.core.utilities.identity.PassiveAuthenticationException;
import com.exskylab.koala.dataAccess.IdentityChallengeDao;
import com.exskylab.koala.entities.IdentityChallenge;
import com.exskylab.koala.entities.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Service
@Slf4j
public class IdentityChallengeManager implements IdentityChallengeService {

    private static final int CHALLENGE_BYTES = 8;

    private final IdentityChallengeDao identityChallengeDao;
    private final IdentityVerificationProperties properties;
    private final SecureRandom random = new SecureRandom();

    public IdentityChallengeManager(IdentityChallengeDao identityChallengeDao,
                                    IdentityVerificationProperties properties) {
        this.identityChallengeDao = identityChallengeDao;
        this.properties = properties;
    }

    @Override
    @Transactional
    public String issue(User user) {
        byte[] bytes = new byte[CHALLENGE_BYTES];
        random.nextBytes(bytes);
        String challenge = Base64.getEncoder().encodeToString(bytes);

        IdentityChallenge record = new IdentityChallenge();
        record.setUser(user);
        record.setChallenge(challenge);
        record.setExpiresAt(Instant.now().plus(properties.getChallengeValidityMinutes(), ChronoUnit.MINUTES));

        identityChallengeDao.save(record);
        log.info("Identity challenge issued for user {}", user.getId());
        return challenge;
    }

    @Override
    @Transactional
    public void consume(User user, String challenge, byte[] dataGroup15, byte[] signature) {
        IdentityChallenge record = identityChallengeDao
                .findByUserIdAndChallengeAndConsumedAtIsNull(user.getId(), challenge)
                .orElseThrow(() -> new ValidationException(IdentityMessages.CHALLENGE_INVALID));

        if (record.getExpiresAt().isBefore(Instant.now())) {
            throw new ValidationException(IdentityMessages.CHALLENGE_INVALID);
        }

        byte[] challengeBytes;
        try {
            challengeBytes = Base64.getDecoder().decode(challenge);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(IdentityMessages.CHALLENGE_INVALID);
        }

        try {
            ActiveAuthentication.verify(dataGroup15, challengeBytes, signature);
        } catch (PassiveAuthenticationException e) {
            log.warn("Active authentication failed for user {}: {}", user.getId(), e.getMessage());
            throw new ValidationException(e.getMessageKey());
        }

        record.setConsumedAt(Instant.now());
        identityChallengeDao.save(record);
        log.info("Chip presence proven for user {}", user.getId());
    }
}
