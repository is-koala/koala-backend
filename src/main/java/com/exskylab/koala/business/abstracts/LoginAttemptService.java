package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.entities.LoginAttempt;

import java.util.List;
import java.util.UUID;

public interface LoginAttemptService {
    LoginAttempt save(LoginAttempt attempt);

    List<LoginAttempt> recentForDevice(UUID userId, UUID deviceId, int limit);

    long countSuccessfulForDevice(UUID userId, UUID deviceId);

    int purgeExpiredUnattributed();

    int purgeExpiredAttributed();
}
