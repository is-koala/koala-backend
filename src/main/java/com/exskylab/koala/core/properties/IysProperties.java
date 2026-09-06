package com.exskylab.koala.core.properties;

import com.exskylab.koala.core.helpers.ClientPlatform;
import com.exskylab.koala.entities.CommercialConsentSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

@Component
@ConfigurationProperties(prefix = "iys")
public class IysProperties {

    private boolean enabled;

    private String apiUrl = "https://api.iletimerkezi.com/v1/consent";

    private String apiKey;

    private String apiHash;

    private String brandCode;

    private Sources sources = new Sources();

    private Duration permissionMaxAge = Duration.ofHours(1);

    private Duration claimTimeout = Duration.ofMinutes(5);

    private String recipientEncryptionKey;

    private Duration deletionWithdrawalRetention = Duration.ofDays(3);

    private Retry retry = new Retry();

    private Reconciliation reconciliation = new Reconciliation();

    public boolean isConfigured() {
        return enabled && hasText(apiKey) && hasText(apiHash) && hasText(brandCode)
                && sources.isConfigured();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getApiHash() {
        return apiHash;
    }

    public void setApiHash(String apiHash) {
        this.apiHash = apiHash;
    }

    public String getBrandCode() {
        return brandCode;
    }

    public void setBrandCode(String brandCode) {
        this.brandCode = brandCode;
    }

    public Sources getSources() {
        return sources;
    }

    public void setSources(Sources sources) {
        this.sources = sources;
    }

    public String sourceFor(CommercialConsentSource decisionSource, ClientPlatform platform) {
        if (decisionSource == CommercialConsentSource.UNSUBSCRIBE_LINK) {
            return sources.unsubscribe.strip();
        }
        return (switch (platform == null ? ClientPlatform.UNKNOWN : platform) {
            case WEB -> sources.web;
            case IOS, ANDROID -> sources.mobile;
            case UNKNOWN -> sources.unknown;
        }).strip();
    }

    public boolean isAllowedSource(String source) {
        return Sources.allowed(source);
    }

    public Duration getPermissionMaxAge() {
        return permissionMaxAge;
    }

    public Duration getClaimTimeout() { return claimTimeout; }

    public void setClaimTimeout(Duration claimTimeout) { this.claimTimeout = claimTimeout; }

    public String getRecipientEncryptionKey() { return recipientEncryptionKey; }

    public void setRecipientEncryptionKey(String recipientEncryptionKey) {
        this.recipientEncryptionKey = recipientEncryptionKey;
    }

    public Duration getDeletionWithdrawalRetention() { return deletionWithdrawalRetention; }

    public void setDeletionWithdrawalRetention(Duration deletionWithdrawalRetention) {
        this.deletionWithdrawalRetention = deletionWithdrawalRetention;
    }

    public void setPermissionMaxAge(Duration permissionMaxAge) {
        this.permissionMaxAge = permissionMaxAge;
    }

    public Retry getRetry() {
        return retry;
    }

    public void setRetry(Retry retry) {
        this.retry = retry;
    }

    public Reconciliation getReconciliation() {
        return reconciliation;
    }

    public void setReconciliation(Reconciliation reconciliation) {
        this.reconciliation = reconciliation;
    }

    public static class Retry {

        private boolean enabled = true;

        private int maxAttempts = 8;

        private Duration initialBackoff = Duration.ofMinutes(1);

        private Duration maxBackoff = Duration.ofHours(6);

        private int batchSize = 50;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getInitialBackoff() {
            return initialBackoff;
        }

        public void setInitialBackoff(Duration initialBackoff) {
            this.initialBackoff = initialBackoff;
        }

        public Duration getMaxBackoff() {
            return maxBackoff;
        }

        public void setMaxBackoff(Duration maxBackoff) {
            this.maxBackoff = maxBackoff;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }

    public static class Sources {

        private static final Set<String> ALLOWED = Set.of(
                "HS_FIZIKSEL_ORTAM", "HS_ISLAK_IMZA", "HS_ETKINLIK", "HS_EORTAM", "HS_WEB",
                "HS_MOBIL", "HS_MESAJ", "HS_EPOSTA", "HS_CAGRI_MERKEZI", "HS_SOSYAL_MEDYA",
                "HS_ATM");

        private String web = "HS_WEB";
        private String mobile = "HS_MOBIL";
        private String unknown = "HS_EORTAM";
        private String unsubscribe = "HS_EPOSTA";

        private boolean isConfigured() {
            return allowed(web) && allowed(mobile) && allowed(unknown) && allowed(unsubscribe);
        }

        private static boolean allowed(String value) {
            return value != null && ALLOWED.contains(value.strip());
        }

        public String getWeb() { return web; }
        public void setWeb(String web) { this.web = web; }
        public String getMobile() { return mobile; }
        public void setMobile(String mobile) { this.mobile = mobile; }
        public String getUnknown() { return unknown; }
        public void setUnknown(String unknown) { this.unknown = unknown; }
        public String getUnsubscribe() { return unsubscribe; }
        public void setUnsubscribe(String unsubscribe) { this.unsubscribe = unsubscribe; }
    }

    public static class Reconciliation {

        private boolean enabled = true;

        private Duration fixedDelay = Duration.ofMinutes(15);

        private int batchSize = 50;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getFixedDelay() {
            return fixedDelay;
        }

        public void setFixedDelay(Duration fixedDelay) {
            this.fixedDelay = fixedDelay;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }
    }
}
