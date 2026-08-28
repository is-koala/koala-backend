package com.exskylab.koala.core.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

@Component
@ConfigurationProperties(prefix = "assignment.unpaid-expiry")
public class UnpaidExpiryProperties {

    private boolean enabled = true;

    private int hoursBeforeStart = 6;

    private int batchSize = 100;

    private ZoneId zoneId = ZoneId.of("Europe/Istanbul");

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getHoursBeforeStart() {
        return hoursBeforeStart;
    }

    public void setHoursBeforeStart(int hoursBeforeStart) {
        this.hoursBeforeStart = hoursBeforeStart;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public ZoneId getZoneId() {
        return zoneId;
    }

    public void setZoneId(ZoneId zoneId) {
        this.zoneId = zoneId;
    }
}
