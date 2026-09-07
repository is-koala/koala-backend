package com.exskylab.koala.core.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "login-attempt.retention")
public class LoginAttemptRetentionProperties {

    private boolean enabled = true;

    private int attributedDays = 365;

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
}
