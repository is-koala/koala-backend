package com.exskylab.koala.core.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.ZoneId;

@Component
@ConfigurationProperties(prefix = "transport.reminders")
public class TransportReminderProperties {

    private boolean enabled = true;

    private ZoneId zoneId = ZoneId.of("Europe/Istanbul");

    private LocalTime eveningAt = LocalTime.of(19, 0);

    private int departureLeadMinutes = 60;

    private int boardingLeadMinutes = 15;

    private int notBoardedAfterMinutes = 10;

    private int notBoardedLookbackMinutes = 60;

    private int batchSize = 200;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public ZoneId getZoneId() {
        return zoneId;
    }

    public void setZoneId(ZoneId zoneId) {
        this.zoneId = zoneId;
    }

    public LocalTime getEveningAt() {
        return eveningAt;
    }

    public void setEveningAt(LocalTime eveningAt) {
        this.eveningAt = eveningAt;
    }

    public int getDepartureLeadMinutes() {
        return departureLeadMinutes;
    }

    public void setDepartureLeadMinutes(int departureLeadMinutes) {
        this.departureLeadMinutes = departureLeadMinutes;
    }

    public int getBoardingLeadMinutes() {
        return boardingLeadMinutes;
    }

    public void setBoardingLeadMinutes(int boardingLeadMinutes) {
        this.boardingLeadMinutes = boardingLeadMinutes;
    }

    public int getNotBoardedAfterMinutes() {
        return notBoardedAfterMinutes;
    }

    public void setNotBoardedAfterMinutes(int notBoardedAfterMinutes) {
        this.notBoardedAfterMinutes = notBoardedAfterMinutes;
    }

    public int getNotBoardedLookbackMinutes() {
        return notBoardedLookbackMinutes;
    }

    public void setNotBoardedLookbackMinutes(int notBoardedLookbackMinutes) {
        this.notBoardedLookbackMinutes = notBoardedLookbackMinutes;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}
