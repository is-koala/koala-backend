package com.exskylab.koala.core.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Component
@Validated
@ConfigurationProperties(prefix = "user-profile-sync.dispatch")
public class UserProfileSyncProperties {

    private boolean enabled = true;

    @Min(1)
    private int maxAttempts = 8;

    @NotNull
    private Duration initialBackoff = Duration.ofSeconds(5);

    @NotNull
    private Duration maxBackoff = Duration.ofMinutes(5);

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double jitterFactor = 0.5;

    @NotNull
    private Duration claimTimeout = Duration.ofMinutes(5);

    @NotNull
    private Duration publisherConfirmTimeout = Duration.ofSeconds(10);

    @NotNull
    private Duration processingClaimTimeout = Duration.ofMinutes(5);

    @Min(1)
    private int batchSize = 50;

    @AssertTrue
    public boolean isDurationConfigurationValid() {
        return initialBackoff != null && !initialBackoff.isNegative() && !initialBackoff.isZero()
                && maxBackoff != null && maxBackoff.compareTo(initialBackoff) >= 0
                && claimTimeout != null && !claimTimeout.isNegative() && !claimTimeout.isZero()
                && publisherConfirmTimeout != null
                && publisherConfirmTimeout.compareTo(Duration.ofMillis(1)) >= 0
                && publisherConfirmTimeout.compareTo(claimTimeout) < 0
                && processingClaimTimeout != null
                && processingClaimTimeout.compareTo(Duration.ofMillis(1)) >= 0;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public Duration getInitialBackoff() {
        return initialBackoff;
    }

    public void setInitialBackoff(Duration initialBackoff) {
        this.initialBackoff = initialBackoff;
    }

    public Duration getMaxBackoff() {
        return maxBackoff;
    }

    public void setMaxBackoff(Duration maxBackoff) {
        this.maxBackoff = maxBackoff;
    }

    public double getJitterFactor() {
        return jitterFactor;
    }

    public void setJitterFactor(double jitterFactor) {
        this.jitterFactor = jitterFactor;
    }

    public Duration getClaimTimeout() {
        return claimTimeout;
    }

    public void setClaimTimeout(Duration claimTimeout) {
        this.claimTimeout = claimTimeout;
    }

    public Duration getPublisherConfirmTimeout() {
        return publisherConfirmTimeout;
    }

    public void setPublisherConfirmTimeout(Duration publisherConfirmTimeout) {
        this.publisherConfirmTimeout = publisherConfirmTimeout;
    }

    public Duration getProcessingClaimTimeout() {
        return processingClaimTimeout;
    }

    public void setProcessingClaimTimeout(Duration processingClaimTimeout) {
        this.processingClaimTimeout = processingClaimTimeout;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}
