package com.exskylab.koala.core.monitoring;

import com.exskylab.koala.core.properties.ErrorMonitoringProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BackendErrorMonitorTest {

    @Test
    void everyCapturedBackendErrorIncrementsTheOperationalMetricWithoutRequiringSentry() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ErrorMonitoringProperties properties = new ErrorMonitoringProperties();
        properties.setEnabled(false);
        BackendErrorMonitor monitor = new SentryBackendErrorMonitor(properties, registry);

        monitor.capture(new IllegalStateException("person@example.test"), "PAYMENT_CALLBACK_FAILED");

        assertEquals(1.0, registry.get("koala.backend.errors")
                .tag("code", "PAYMENT_CALLBACK_FAILED")
                .tag("type", "ILLEGAL_STATE_EXCEPTION")
                .counter().count());
    }
}
