package com.exskylab.koala.core.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZoneId;

@Component
@ConfigurationProperties(prefix = "timesheet")
public class TimesheetProperties {

    private Duration maxOfflineAge = Duration.ofHours(72);

    private Duration maxFutureSkew = Duration.ofMinutes(5);

    private ZoneId zoneId = ZoneId.of("Europe/Istanbul");

    public Duration getMaxOfflineAge() {
        return maxOfflineAge;
    }

    public void setMaxOfflineAge(Duration maxOfflineAge) {
        this.maxOfflineAge = maxOfflineAge;
    }

    public Duration getMaxFutureSkew() {
        return maxFutureSkew;
    }

    public void setMaxFutureSkew(Duration maxFutureSkew) {
        this.maxFutureSkew = maxFutureSkew;
    }

    public ZoneId getZoneId() {
        return zoneId;
    }

    public void setZoneId(ZoneId zoneId) {
        this.zoneId = zoneId;
    }
}
