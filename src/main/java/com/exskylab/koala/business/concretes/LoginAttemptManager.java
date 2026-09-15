package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.business.abstracts.LoginAttemptService;
import com.exskylab.koala.core.properties.LoginAttemptRetentionProperties;
import com.exskylab.koala.dataAccess.LoginAttemptDao;
import com.exskylab.koala.entities.LoginAttempt;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class LoginAttemptManager implements LoginAttemptService {

    private final LoginAttemptDao loginAttemptDao;
    private final LoginAttemptRetentionProperties retentionProperties;

    public LoginAttemptManager(LoginAttemptDao loginAttemptDao,
                               LoginAttemptRetentionProperties retentionProperties) {
        this.loginAttemptDao = loginAttemptDao;
        this.retentionProperties = retentionProperties;
    }

    @Override
    public LoginAttempt save(LoginAttempt attempt) {
        var result = loginAttemptDao.save(attempt);
        log.info("Saved login attempt with id: {}", result.getId());
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LoginAttempt> recentForDevice(UUID userId, UUID deviceId, int limit) {
        return loginAttemptDao.findAllByUser_IdAndDevice_IdOrderByTimeStampDesc(userId, deviceId,
                PageRequest.of(0, limit));
    }

    @Override
    @Transactional(readOnly = true)
    public long countSuccessfulForDevice(UUID userId, UUID deviceId) {
        return loginAttemptDao.countSuccessfulForDevice(userId, deviceId);
    }

    @Override
    @Transactional
    public int purgeExpiredUnattributed() {
        int purged = loginAttemptDao.deleteUnattributedBefore(
                retentionProperties.unattributedCutoff(Instant.now()));
        if (purged > 0) {
            log.info("Login attempts belonging to no account past their retention window removed: {}", purged);
        }
        return purged;
    }

    @Override
    @Transactional
    public int purgeExpiredAttributed() {
        int purged = loginAttemptDao.deleteAttributedBefore(
                retentionProperties.attributedCutoff(Instant.now()));
        if (purged > 0) {
            log.info("Login attempts tied to an account past their retention window removed: {}", purged);
        }
        return purged;
    }
}
