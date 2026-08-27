package com.exskylab.koala.core.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.ZoneId;

@Component
@ConfigurationProperties(prefix = "cancellation")
public class CancellationProperties {

    private int freeWindowHours = 24;

    private BigDecimal lateFeeRate = new BigDecimal("0.50");

    private ZoneId zoneId = ZoneId.of("Europe/Istanbul");

    public int getFreeWindowHours() {
        return freeWindowHours;
    }

    public void setFreeWindowHours(int freeWindowHours) {
        this.freeWindowHours = freeWindowHours;
    }

    public BigDecimal getLateFeeRate() {
        return lateFeeRate;
    }

    public void setLateFeeRate(BigDecimal lateFeeRate) {
        this.lateFeeRate = lateFeeRate;
    }

    public ZoneId getZoneId() {
        return zoneId;
    }

    public void setZoneId(ZoneId zoneId) {
        this.zoneId = zoneId;
    }
}
