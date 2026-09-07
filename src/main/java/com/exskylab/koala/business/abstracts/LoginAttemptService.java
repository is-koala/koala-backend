package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.entities.LoginAttempt;

public interface LoginAttemptService {
    LoginAttempt save(LoginAttempt attempt);

    int purgeExpiredUnattributed();

    int purgeExpiredAttributed();
}
