package com.exskylab.koala.core.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "realtime")
public class RealtimeProperties {

    private boolean enabled = true;

    private String endpoint = "/ws";

    private Duration typingTimeout = Duration.ofSeconds(5);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public Duration getTypingTimeout() {
        return typingTimeout;
    }

    public void setTypingTimeout(Duration typingTimeout) {
        this.typingTimeout = typingTimeout;
    }
}
