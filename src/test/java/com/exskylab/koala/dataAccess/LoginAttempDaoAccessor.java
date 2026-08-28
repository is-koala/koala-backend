package com.exskylab.koala.dataAccess;

import com.exskylab.koala.entities.LoginAttempt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Component
public class LoginAttempDaoAccessor {

    private final LoginAttemptDao loginAttemptDao;

    public LoginAttempDaoAccessor(LoginAttemptDao loginAttemptDao) {
        this.loginAttemptDao = loginAttemptDao;
    }

    @Transactional
    public UUID save(Instant timestamp) {
        LoginAttempt attempt = new LoginAttempt();
        attempt.setEmailAttempted("tz@example.com");
        attempt.setSuccess(false);
        attempt.setTimeStamp(timestamp);
        return loginAttemptDao.save(attempt).getId();
    }

    @Transactional(readOnly = true)
    public Instant findTimestamp(UUID id) {
        return loginAttemptDao.findById(id).orElseThrow().getTimeStamp();
    }
}
