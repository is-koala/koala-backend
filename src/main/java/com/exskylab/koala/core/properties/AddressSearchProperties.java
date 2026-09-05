package com.exskylab.koala.core.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "address-search")
public class AddressSearchProperties {
    private boolean enabled;
    private String apiKey = "";
    private String placesBaseUrl = "https://places.googleapis.com";
    private Duration sessionTtl = Duration.ofMinutes(5);
    private Duration candidateTtl = Duration.ofMinutes(10);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getPlacesBaseUrl() {
        return placesBaseUrl;
    }

    public void setPlacesBaseUrl(String placesBaseUrl) {
        this.placesBaseUrl = placesBaseUrl;
    }

    public Duration getSessionTtl() {
        return sessionTtl;
    }

    public void setSessionTtl(Duration sessionTtl) {
        this.sessionTtl = sessionTtl;
    }

    public Duration getCandidateTtl() {
        return candidateTtl;
    }

    public void setCandidateTtl(Duration candidateTtl) {
        this.candidateTtl = candidateTtl;
    }
}
