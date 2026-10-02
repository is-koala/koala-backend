package com.exskylab.koala.core.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "transport.boarding")
public class TransportBoardingProperties {

    private Duration tokenTtl = Duration.ofMinutes(10);

    private int farFromStopMeters = 250;

    private Duration scheduleTolerance = Duration.ofMinutes(30);

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
}
