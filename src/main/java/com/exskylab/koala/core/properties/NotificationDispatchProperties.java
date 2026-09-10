package com.exskylab.koala.core.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Component
@Validated
@ConfigurationProperties(prefix = "notification.dispatch")
public class NotificationDispatchProperties {

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
    private Duration deliveryClaimTimeout = Duration.ofMinutes(5);

    @NotNull
    private Duration providerTimeout = Duration.ofSeconds(45);

    @NotNull
    private Duration deliveryBusyRetryInterval = Duration.ofSeconds(5);

    @Min(1)
    @Max(1000)
    private int deliveryBusyMaxRetries = 61;

    @NotNull
    private Duration legacyGracePeriod = Duration.ofMinutes(15);

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
                && deliveryClaimTimeout != null
                && deliveryClaimTimeout.compareTo(Duration.ofMillis(1)) >= 0
                && providerTimeout != null
                && providerTimeout.compareTo(Duration.ofMillis(1)) >= 0
                && providerTimeout.compareTo(deliveryClaimTimeout) < 0
                && deliveryBusyRetryInterval != null
                && deliveryBusyRetryInterval.compareTo(Duration.ofMillis(1)) >= 0
                && deliveryBusyRetryWindowCoversClaimTimeout()
                && legacyGracePeriod != null && !legacyGracePeriod.isNegative();
    }

    private boolean deliveryBusyRetryWindowCoversClaimTimeout() {
        try {
            return deliveryBusyRetryInterval.multipliedBy(deliveryBusyMaxRetries)
                    .compareTo(deliveryClaimTimeout) >= 0;
        } catch (ArithmeticException exception) {
            return false;
        }
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

    public Duration getDeliveryClaimTimeout() {
        return deliveryClaimTimeout;
    }

    public void setDeliveryClaimTimeout(Duration deliveryClaimTimeout) {
        this.deliveryClaimTimeout = deliveryClaimTimeout;
    }

    public Duration getProviderTimeout() {
        return providerTimeout;
    }

    public void setProviderTimeout(Duration providerTimeout) {
        this.providerTimeout = providerTimeout;
    }

    public Duration getDeliveryBusyRetryInterval() {
        return deliveryBusyRetryInterval;
    }

    public void setDeliveryBusyRetryInterval(Duration deliveryBusyRetryInterval) {
        this.deliveryBusyRetryInterval = deliveryBusyRetryInterval;
    }

    public int getDeliveryBusyMaxRetries() {
        return deliveryBusyMaxRetries;
    }

    public void setDeliveryBusyMaxRetries(int deliveryBusyMaxRetries) {
        this.deliveryBusyMaxRetries = deliveryBusyMaxRetries;
    }

    public Duration getLegacyGracePeriod() {
        return legacyGracePeriod;
    }

    public void setLegacyGracePeriod(Duration legacyGracePeriod) {
        this.legacyGracePeriod = legacyGracePeriod;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}
