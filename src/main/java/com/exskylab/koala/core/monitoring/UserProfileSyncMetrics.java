package com.exskylab.koala.core.monitoring;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class UserProfileSyncMetrics {

    static final String BACKLOG = "koala.user.profile.sync.backlog";
    static final String OLDEST_AGE = "koala.user.profile.sync.oldest.age";
    static final String TERMINAL_BROKER_FAILURES = "koala.user.profile.sync.terminal.broker.failures";

    private final AtomicLong backlog = new AtomicLong();
    private final AtomicLong oldestAgeSeconds = new AtomicLong();
    private final AtomicLong terminalBrokerFailures = new AtomicLong();

    public UserProfileSyncMetrics(MeterRegistry meterRegistry) {
        Gauge.builder(BACKLOG, backlog, AtomicLong::doubleValue).register(meterRegistry);
        Gauge.builder(OLDEST_AGE, oldestAgeSeconds, AtomicLong::doubleValue)
                .baseUnit("seconds")
                .register(meterRegistry);
        Gauge.builder(TERMINAL_BROKER_FAILURES, terminalBrokerFailures, AtomicLong::doubleValue)
                .register(meterRegistry);
    }

    public void refresh(long backlogCount, Duration oldestAge, long terminalFailureCount) {
        backlog.set(Math.max(0, backlogCount));
        oldestAgeSeconds.set(Math.max(0, oldestAge.toSeconds()));
        terminalBrokerFailures.set(Math.max(0, terminalFailureCount));
    }
}
