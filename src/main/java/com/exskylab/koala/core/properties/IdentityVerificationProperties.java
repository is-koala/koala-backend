package com.exskylab.koala.core.properties;

import jakarta.validation.constraints.AssertTrue;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "identity.passive-authentication")
public class IdentityVerificationProperties {

    private boolean enabled = false;

    private String trustAnchorDirectory = "";

    private boolean activeAuthenticationRequired = false;

    private int challengeValidityMinutes = 10;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTrustAnchorDirectory() {
        return trustAnchorDirectory;
    }

    public void setTrustAnchorDirectory(String trustAnchorDirectory) {
        this.trustAnchorDirectory = trustAnchorDirectory;
    }

    public boolean isActiveAuthenticationRequired() {
        return activeAuthenticationRequired;
    }

    public void setActiveAuthenticationRequired(boolean activeAuthenticationRequired) {
        this.activeAuthenticationRequired = activeAuthenticationRequired;
    }

    public int getChallengeValidityMinutes() {
        return challengeValidityMinutes;
    }

    public void setChallengeValidityMinutes(int challengeValidityMinutes) {
        this.challengeValidityMinutes = challengeValidityMinutes;
    }

    @AssertTrue(message = "identity.passive-authentication.enabled must be true when active authentication is required")
    public boolean isActiveAuthenticationBackedByPassiveAuthentication() {
        return !activeAuthenticationRequired || enabled;
    }
}
