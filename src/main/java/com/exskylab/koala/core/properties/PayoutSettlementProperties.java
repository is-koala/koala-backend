package com.exskylab.koala.core.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "payout.settlement")
public class PayoutSettlementProperties {

    private boolean overdueMonitoringEnabled = true;

    private Duration overdueAfter = Duration.ofDays(3);

    public boolean isOverdueMonitoringEnabled() {
        return overdueMonitoringEnabled;
    }

    public void setOverdueMonitoringEnabled(boolean overdueMonitoringEnabled) {
        this.overdueMonitoringEnabled = overdueMonitoringEnabled;
    }

    public Duration getOverdueAfter() {
        return overdueAfter;
    }

    public void setOverdueAfter(Duration overdueAfter) {
        this.overdueAfter = overdueAfter;
    }
}
