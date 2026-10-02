package com.exskylab.koala.core.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Component
@Validated
@ConfigurationProperties(prefix = "transport.boarding")
public class TransportBoardingProperties {

    private boolean noticesEnabled = false;

    private Duration tokenTtl = Duration.ofMinutes(10);

    @Min(value = 0, message = "transport.boarding.far-from-stop-meters negatif olamaz")
    private int farFromStopMeters = 250;

    private Duration scheduleTolerance = Duration.ofMinutes(30);

    private Duration clockSkew = Duration.ofMinutes(2);

    private Duration lateSyncMax = Duration.ofHours(12);

    public boolean isNoticesEnabled() {
        return noticesEnabled;
    }

    public void setNoticesEnabled(boolean noticesEnabled) {
        this.noticesEnabled = noticesEnabled;
    }

    public Duration getTokenTtl() {
        return tokenTtl;
    }

    public void setTokenTtl(Duration tokenTtl) {
        this.tokenTtl = tokenTtl;
    }

    public int getFarFromStopMeters() {
        return farFromStopMeters;
    }

    public void setFarFromStopMeters(int farFromStopMeters) {
        this.farFromStopMeters = farFromStopMeters;
    }

    public Duration getScheduleTolerance() {
        return scheduleTolerance;
    }

    public void setScheduleTolerance(Duration scheduleTolerance) {
        this.scheduleTolerance = scheduleTolerance;
    }

    public Duration getClockSkew() {
        return clockSkew;
    }

    public void setClockSkew(Duration clockSkew) {
        this.clockSkew = clockSkew;
    }

    public Duration getLateSyncMax() {
        return lateSyncMax;
    }

    public void setLateSyncMax(Duration lateSyncMax) {
        this.lateSyncMax = lateSyncMax;
    }

    @AssertTrue(message = "transport.boarding.token-ttl en az bir saniye olmali")
    public boolean isTokenTtlPositive() {
        return tokenTtl != null && tokenTtl.toSeconds() >= 1;
    }

    @AssertTrue(message = "transport.boarding.schedule-tolerance negatif olamaz")
    public boolean isScheduleToleranceNotNegative() {
        return scheduleTolerance != null && !scheduleTolerance.isNegative();
    }

    @AssertTrue(message = "transport.boarding.clock-skew negatif olamaz")
    public boolean isClockSkewNotNegative() {
        return clockSkew != null && !clockSkew.isNegative();
    }

    @AssertTrue(message = "transport.boarding.late-sync-max pozitif bir sure olmali")
    public boolean isLateSyncMaxPositive() {
        return lateSyncMax != null && !lateSyncMax.isNegative() && !lateSyncMax.isZero();
    }
}
