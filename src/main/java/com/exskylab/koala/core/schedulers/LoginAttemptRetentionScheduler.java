package com.exskylab.koala.core.schedulers;

import com.exskylab.koala.business.abstracts.LoginAttemptService;
import com.exskylab.koala.core.properties.LoginAttemptRetentionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class LoginAttemptRetentionScheduler {

    private final LoginAttemptService loginAttemptService;
    private final LoginAttemptRetentionProperties properties;

    public LoginAttemptRetentionScheduler(LoginAttemptService loginAttemptService,
                                          LoginAttemptRetentionProperties properties) {
        this.loginAttemptService = loginAttemptService;
        this.properties = properties;
    }

    @Scheduled(cron = "${login-attempt.retention.cron:0 50 3 * * *}")
    public void enforceRetention() {
        if (!properties.isEnabled()) {
            return;
        }

        try {
            int unattributed = loginAttemptService.purgeExpiredUnattributed();
            int attributed = loginAttemptService.purgeExpiredAttributed();
            log.info("Login attempt retention run finished: {} ownerless rows and {} account rows removed.",
                    unattributed, attributed);
        } catch (Exception exception) {
            log.error("Login attempt retention run failed", exception);
        }
    }
}
