package com.exskylab.koala.core.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "iyzico.payment-verification")
public class PaymentVerificationProperties {

    private boolean enabled = true;
    private int maxAttempts = 8;
    private Duration initialReconciliationDelay = Duration.ofMinutes(35);
    private Duration initiationDeadline = Duration.ofMinutes(15);
    private Duration initialBackoff = Duration.ofMinutes(1);
    private Duration maxBackoff = Duration.ofHours(6);
    private int batchSize = 50;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public Duration getInitialReconciliationDelay() { return initialReconciliationDelay; }
    public void setInitialReconciliationDelay(Duration initialReconciliationDelay) {
        this.initialReconciliationDelay = initialReconciliationDelay;
    }
    public Duration getInitialBackoff() { return initialBackoff; }
    public void setInitialBackoff(Duration initialBackoff) { this.initialBackoff = initialBackoff; }
    public Duration getMaxBackoff() { return maxBackoff; }
    public void setMaxBackoff(Duration maxBackoff) { this.maxBackoff = maxBackoff; }
    public Duration getInitiationDeadline() { return initiationDeadline; }
    public void setInitiationDeadline(Duration initiationDeadline) {
        this.initiationDeadline = initiationDeadline;
    }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
}
