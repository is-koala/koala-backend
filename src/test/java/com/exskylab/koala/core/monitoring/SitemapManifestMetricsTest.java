package com.exskylab.koala.core.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SitemapManifestMetricsTest {

    @Test
    void operationsUseOneBoundedMetricWithTheDocumentedTagMatrix() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        SitemapManifestMetrics metrics = new SitemapManifestMetrics(registry);

        metrics.manifestHit();
        metrics.manifestCreated();
        metrics.manifestFailed();
        metrics.shardServed();
        metrics.shardNotFound();
        metrics.shardFailed();

        Map<String, Double> counts = registry.find("koala.sitemap.manifest.operations").counters().stream()
                .collect(Collectors.toMap(this::series, Counter::count));

        assertEquals(Map.of(
                "manifest:hit", 1.0,
                "manifest:created", 1.0,
                "manifest:failed", 1.0,
                "shard:served", 1.0,
                "shard:not_found", 1.0,
                "shard:failed", 1.0), counts);
        registry.find("koala.sitemap.manifest.operations").counters().forEach(counter -> assertEquals(
                Set.of("operation", "outcome"),
                counter.getId().getTags().stream().map(Tag::getKey).collect(Collectors.toSet())));
    }

    private String series(Counter counter) {
        return counter.getId().getTag("operation") + ":" + counter.getId().getTag("outcome");
    }
}
