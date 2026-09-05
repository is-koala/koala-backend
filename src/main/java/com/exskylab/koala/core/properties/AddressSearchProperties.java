package com.exskylab.koala.core.properties;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Component
@Validated
@ConfigurationProperties(prefix = "address-search")
public class AddressSearchProperties {
    private boolean enabled;
    private String apiKey = "";
    private String placesBaseUrl = "https://places.googleapis.com";
    private String geocodingBaseUrl = "https://maps.googleapis.com";
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

    public String getGeocodingBaseUrl() {
        return geocodingBaseUrl;
    }

    public void setGeocodingBaseUrl(String geocodingBaseUrl) {
        this.geocodingBaseUrl = geocodingBaseUrl;
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

    @AssertTrue(message = "address-search.api-key must be configured when address search is enabled")
    public boolean isProviderSecretConfigured() {
        return !enabled || (apiKey != null && !apiKey.isBlank());
    }
}
