package com.exskylab.koala.core.helpers;

import com.exskylab.koala.core.properties.RateLimitProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimiter {

    private final RateLimitProperties properties;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    public RateLimiter(RateLimitProperties properties) {
        this(properties, Clock.systemUTC());
    }

    RateLimiter(RateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public boolean tryAcquire(String key) {
        if (!properties.isEnabled()) {
            return true;
        }

        Instant now = Instant.now(clock);
        Window window = windows.compute(key, (ignored, current) -> {
            if (current == null || current.isExpired(now)) {
                return new Window(now.plus(properties.getWindow()));
            }
            return current;
        });

        return window.count().incrementAndGet() <= properties.getMaxAttempts();
    }

    public void evictExpired() {
        Instant now = Instant.now(clock);
        windows.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    int trackedKeys() {
        return windows.size();
    }

    private record Window(Instant expiresAt, AtomicInteger count) {

        Window(Instant expiresAt) {
            this(expiresAt, new AtomicInteger());
        }

        boolean isExpired(Instant now) {
            return now.isAfter(expiresAt);
        }
    }
}
