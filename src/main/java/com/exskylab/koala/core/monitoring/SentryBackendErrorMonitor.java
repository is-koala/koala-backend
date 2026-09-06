package com.exskylab.koala.core.monitoring;

import com.exskylab.koala.core.properties.ErrorMonitoringProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.SentryLevel;
import io.sentry.protocol.Message;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class SentryBackendErrorMonitor implements BackendErrorMonitor {

    private final ErrorMonitoringProperties properties;
    private final MeterRegistry meterRegistry;

    public SentryBackendErrorMonitor(ErrorMonitoringProperties properties, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        if (properties.isEnabled()) {
            if (properties.getDsn() == null || properties.getDsn().isBlank()) {
                throw new IllegalStateException("SENTRY_DSN is required when error monitoring is enabled");
            }
            Sentry.init(options -> {
                options.setDsn(properties.getDsn());
                options.setEnvironment(emptyToNull(properties.getEnvironment()));
                options.setRelease(emptyToNull(properties.getRelease()));
                options.setSendDefaultPii(false);
                options.setBeforeSend(new StrictSentryEventSanitizer());
                options.setAttachStacktrace(false);
                options.setAttachThreads(false);
            });
        }
    }

    @Override
    public void capture(Throwable error, String code) {
        String safeCode = normalize(code, "UNCLASSIFIED");
        String safeType = normalize(error == null ? null : error.getClass().getSimpleName(), "UNKNOWN");
        meterRegistry.counter("koala.backend.errors", "code", safeCode, "type", safeType).increment();
        if (!properties.isEnabled()) {
            return;
        }

        SentryEvent event = new SentryEvent();
        event.setLevel(SentryLevel.ERROR);
        Message message = new Message();
        message.setMessage("Backend error");
        event.setMessage(message);
        event.setTag("safe.code", safeCode);
        event.setTag("safe.type", safeType);
        Sentry.captureEvent(event);
    }

    private String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("[^A-Za-z0-9_.-]", "_")
                .toUpperCase(Locale.ROOT);
        return normalized.length() <= 96 ? normalized : normalized.substring(0, 96);
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
