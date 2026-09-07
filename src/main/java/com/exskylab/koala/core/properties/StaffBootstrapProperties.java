package com.exskylab.koala.core.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "koala.staff.bootstrap")
public class StaffBootstrapProperties {

    private String ownerEmail = "";

    private String ownerPassword = "";

    public String getOwnerEmail() {
        return ownerEmail;
    }

    public void setOwnerEmail(String ownerEmail) {
        this.ownerEmail = ownerEmail;
    }

    public String getOwnerPassword() {
        return ownerPassword;
    }

    public void setOwnerPassword(String ownerPassword) {
        this.ownerPassword = ownerPassword;
    }

    public boolean isConfigured() {
        return ownerEmail != null && !ownerEmail.isBlank()
                && ownerPassword != null && !ownerPassword.isBlank();
    }
}
