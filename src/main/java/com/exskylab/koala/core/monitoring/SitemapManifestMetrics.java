package com.exskylab.koala.core.monitoring;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class SitemapManifestMetrics {

    private static final String METRIC_NAME = "koala.sitemap.manifest.operations";

    private final MeterRegistry meterRegistry;

    public SitemapManifestMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        register("manifest", "hit");
        register("manifest", "created");
        register("manifest", "failed");
        register("shard", "served");
        register("shard", "not_found");
        register("shard", "failed");
    }

    public void manifestHit() {
        record("manifest", "hit");
    }

    public void manifestCreated() {
        record("manifest", "created");
    }

    public void manifestFailed() {
        record("manifest", "failed");
    }

    public void shardServed() {
        record("shard", "served");
    }

    public void shardNotFound() {
        record("shard", "not_found");
    }

    public void shardFailed() {
        record("shard", "failed");
    }

    private void record(String operation, String outcome) {
        meterRegistry.counter(METRIC_NAME, "operation", operation, "outcome", outcome).increment();
    }

    private void register(String operation, String outcome) {
        meterRegistry.counter(METRIC_NAME, "operation", operation, "outcome", outcome);
    }
}
