package com.exskylab.koala.core.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "session")
public class SessionProperties {

    private int refreshValidityDays = 30;
    private int refreshSlidingWindowDays = 7;

    public int getRefreshValidityDays() {
        return refreshValidityDays;
    }

    public void setRefreshValidityDays(int refreshValidityDays) {
        this.refreshValidityDays = refreshValidityDays;
    }

    public int getRefreshSlidingWindowDays() {
        return refreshSlidingWindowDays;
    }

    public void setRefreshSlidingWindowDays(int refreshSlidingWindowDays) {
        this.refreshSlidingWindowDays = refreshSlidingWindowDays;
    }
}
