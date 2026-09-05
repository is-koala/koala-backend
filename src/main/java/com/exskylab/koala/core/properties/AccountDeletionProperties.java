package com.exskylab.koala.core.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "account-deletion")
public class AccountDeletionProperties {

    private boolean enabled = true;
    private Duration waitingPeriod = Duration.ofDays(30);
    private int batchSize = 100;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Duration getWaitingPeriod() {
        return waitingPeriod;
    }

    public void setWaitingPeriod(Duration waitingPeriod) {
        this.waitingPeriod = waitingPeriod;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}
