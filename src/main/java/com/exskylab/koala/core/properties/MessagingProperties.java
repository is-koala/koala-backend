package com.exskylab.koala.core.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

@Component
@ConfigurationProperties(prefix = "messaging")
public class MessagingProperties {

    private int graceDaysAfterJob = 7;

    private String zoneId = "Europe/Istanbul";

    public int getGraceDaysAfterJob() {
        return graceDaysAfterJob;
    }

    public void setGraceDaysAfterJob(int graceDaysAfterJob) {
        this.graceDaysAfterJob = graceDaysAfterJob;
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public ZoneId zone() {
        return ZoneId.of(zoneId);
    }
}
