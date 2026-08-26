package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.LoginAttemptService;
import com.exskylab.koala.dataAccess.LoginAttemptDao;
import com.exskylab.koala.entities.LoginAttempt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LoginAttemptManager implements LoginAttemptService {

    private final LoginAttemptDao loginAttemptDao;

    private final Logger logger = LoggerFactory.getLogger(LoginAttemptManager.class);


    public LoginAttemptManager(LoginAttemptDao loginAttemptDao) {
        this.loginAttemptDao = loginAttemptDao;
    }

    @Override
    public LoginAttempt save(LoginAttempt attempt) {
        var result = loginAttemptDao.save(attempt);
        logger.info("Saved login attempt with id: {}", result.getId());
        return result;
    }
}
