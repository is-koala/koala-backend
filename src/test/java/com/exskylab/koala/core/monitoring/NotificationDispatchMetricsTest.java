package com.exskylab.koala.core.monitoring;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificationDispatchMetricsTest {

    @Test
    void backlogAgeAndTerminalBrokerFailuresAreExposedWithoutUnboundedTags() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        NotificationDispatchMetrics metrics = new NotificationDispatchMetrics(registry);

        metrics.refresh(7, Duration.ofSeconds(23), 2);

        assertEquals(7, registry.get(NotificationDispatchMetrics.BACKLOG).gauge().value());
        assertEquals(23, registry.get(NotificationDispatchMetrics.OLDEST_AGE).gauge().value());
        assertEquals(2, registry.get(NotificationDispatchMetrics.TERMINAL_BROKER_FAILURES).gauge().value());
        assertTrue(registry.getMeters().stream().allMatch(meter -> meter.getId().getTags().isEmpty()));
    }

    @Test
    void anEmptyBacklogCannotPublishANegativeAge() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        NotificationDispatchMetrics metrics = new NotificationDispatchMetrics(registry);

        metrics.refresh(0, Duration.ofSeconds(-8), 0);

        assertEquals(0, registry.get(NotificationDispatchMetrics.OLDEST_AGE).gauge().value());
    }
}
