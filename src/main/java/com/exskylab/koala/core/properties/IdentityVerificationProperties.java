package com.exskylab.koala.core.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "identity.passive-authentication")
public class IdentityVerificationProperties {

    private boolean enabled = false;

    private String trustAnchorDirectory = "";

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
}
