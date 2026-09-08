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

    private Duration stalledAfter = Duration.ofDays(10);

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

    public Duration getStalledAfter() {
        return stalledAfter;
    }

    public void setStalledAfter(Duration stalledAfter) {
        this.stalledAfter = stalledAfter;
    }

    @AssertTrue(message = "payout.settlement.overdue-after pozitif bir sure olmali")
    public boolean isOverdueAfterPositive() {
        return overdueAfter != null && !overdueAfter.isNegative() && !overdueAfter.isZero();
    }

    @AssertTrue(message = "payout.settlement.stalled-after payout.settlement.overdue-after degerinden buyuk olmali")
    public boolean isStalledAfterBeyondOverdueAfter() {
        return stalledAfter != null && overdueAfter != null && stalledAfter.compareTo(overdueAfter) > 0;
    }
}
