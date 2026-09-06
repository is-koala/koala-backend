package com.exskylab.koala.core.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "audit.retention")
public class AuditRetentionProperties {

    private boolean enabled = true;

    private int coreMonths = 24;

    private int financialCoreMonths = 120;

    private int detailMonths = 6;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getCoreMonths() {
        return coreMonths;
    }

    public void setCoreMonths(int coreMonths) {
        this.coreMonths = coreMonths;
    }

    public int getFinancialCoreMonths() {
        return financialCoreMonths;
    }

    public void setFinancialCoreMonths(int financialCoreMonths) {
        this.financialCoreMonths = financialCoreMonths;
    }

    public int getDetailMonths() {
        return detailMonths;
    }

    public void setDetailMonths(int detailMonths) {
        this.detailMonths = detailMonths;
    }
}
