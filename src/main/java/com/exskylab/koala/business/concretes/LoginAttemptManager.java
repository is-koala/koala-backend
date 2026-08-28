package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.business.abstracts.LoginAttemptService;
import com.exskylab.koala.dataAccess.LoginAttemptDao;
import com.exskylab.koala.entities.LoginAttempt;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class LoginAttemptManager implements LoginAttemptService {

    private final LoginAttemptDao loginAttemptDao;


    public LoginAttemptManager(LoginAttemptDao loginAttemptDao) {
        this.loginAttemptDao = loginAttemptDao;
    }

    @Override
    public LoginAttempt save(LoginAttempt attempt) {
        var result = loginAttemptDao.save(attempt);
        log.info("Saved login attempt with id: {}", result.getId());
        return result;
    }
}
