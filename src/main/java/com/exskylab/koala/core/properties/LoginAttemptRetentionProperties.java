package com.exskylab.koala.core.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.time.Instant;

@Component
@Validated
@ConfigurationProperties(prefix = "login-attempt.retention")
public class LoginAttemptRetentionProperties {

    private boolean enabled = true;

    @Min(value = 1, message = "login-attempt.retention.attributed-days must be at least 1")
    private int attributedDays = 365;

    @Min(value = 1, message = "login-attempt.retention.unattributed-days must be at least 1")
    private int unattributedDays = 30;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getAttributedDays() {
        return attributedDays;
    }

    public void setAttributedDays(int attributedDays) {
        this.attributedDays = attributedDays;
    }

    public int getUnattributedDays() {
        return unattributedDays;
    }

    public void setUnattributedDays(int unattributedDays) {
        this.unattributedDays = unattributedDays;
    }

    public Instant attributedCutoff(Instant now) {
        return now.minus(Duration.ofDays(attributedDays));
    }

    public Instant unattributedCutoff(Instant now) {
        return now.minus(Duration.ofDays(unattributedDays));
    }

    @AssertTrue(message = "login-attempt.retention.unattributed-days must not exceed "
            + "login-attempt.retention.attributed-days: a row that belongs to no account "
            + "cannot outlive an account's own history")
    public boolean isUnattributedWindowWithinTheAttributedOne() {
        return unattributedDays <= attributedDays;
    }
}
