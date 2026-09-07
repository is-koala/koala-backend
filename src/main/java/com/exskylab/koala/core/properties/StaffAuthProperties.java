package com.exskylab.koala.core.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "koala.staff.auth")
public class StaffAuthProperties {

    private Duration accessTokenValidity = Duration.ofMinutes(15);

    private Duration refreshTokenValidity = Duration.ofHours(12);

    private int maxFailedAttempts = 5;

    private Duration lockDuration = Duration.ofMinutes(15);

    public Duration getAccessTokenValidity() {
        return accessTokenValidity;
    }

    public void setAccessTokenValidity(Duration accessTokenValidity) {
        this.accessTokenValidity = accessTokenValidity;
    }

    public Duration getRefreshTokenValidity() {
        return refreshTokenValidity;
    }

    public void setRefreshTokenValidity(Duration refreshTokenValidity) {
        this.refreshTokenValidity = refreshTokenValidity;
    }

    public int getMaxFailedAttempts() {
        return maxFailedAttempts;
    }

    public void setMaxFailedAttempts(int maxFailedAttempts) {
        this.maxFailedAttempts = maxFailedAttempts;
    }

    public Duration getLockDuration() {
        return lockDuration;
    }

    public void setLockDuration(Duration lockDuration) {
        this.lockDuration = lockDuration;
    }
}
