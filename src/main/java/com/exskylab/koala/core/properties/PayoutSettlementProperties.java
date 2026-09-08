package com.exskylab.koala.core.properties;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Component
@Validated
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

    @AssertTrue(message = "payout.settlement.overdue-after pozitif bir sure olmali")
    public boolean isOverdueAfterPositive() {
        return overdueAfter != null && !overdueAfter.isNegative() && !overdueAfter.isZero();
    }
}
