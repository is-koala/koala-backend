package com.exskylab.koala.core.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;
    private int maxAttempts = 10;
    private Duration window = Duration.ofMinutes(1);

    private Bucket upload = new Bucket(20, Duration.ofMinutes(1));
    private Bucket message = new Bucket(30, Duration.ofMinutes(1));
    private Bucket write = new Bucket(60, Duration.ofMinutes(1));
    private Bucket addressSearch = new Bucket(60, Duration.ofMinutes(1));

    public static class Bucket {

        private int maxAttempts;
        private Duration window;

        public Bucket() {
        }

        public Bucket(int maxAttempts, Duration window) {
            this.maxAttempts = maxAttempts;
            this.window = window;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }
    }

    public Bucket getUpload() {
        return upload;
    }

    public void setUpload(Bucket upload) {
        this.upload = upload;
    }

    public Bucket getMessage() {
        return message;
    }

    public void setMessage(Bucket message) {
        this.message = message;
    }

    public Bucket getWrite() {
        return write;
    }

    public Bucket getAddressSearch() {
        return addressSearch;
    }

    public void setAddressSearch(Bucket addressSearch) {
        this.addressSearch = addressSearch;
    }

    public void setWrite(Bucket write) {
        this.write = write;
    }

    public Bucket getAuth() {
        return new Bucket(maxAttempts, window);
    }

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

    public Duration getWindow() {
        return window;
    }

    public void setWindow(Duration window) {
        this.window = window;
    }
}
