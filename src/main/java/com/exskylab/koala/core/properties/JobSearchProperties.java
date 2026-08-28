package com.exskylab.koala.core.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.ZoneId;

@Component
@ConfigurationProperties(prefix = "job-search")
public class JobSearchProperties {

    private String zoneId = "Europe/Istanbul";

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
