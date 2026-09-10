package com.exskylab.koala.core.properties;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SitemapManifestPropertiesTest {

    @Test
    void shardMathChecksTheBoundBeforeAnyResponseListCanBeAllocated() {
        SitemapManifestProperties properties = new SitemapManifestProperties();
        properties.setShardSize(2);
        properties.setMaxShards(3);

        assertEquals(0, properties.shardCount(0));
        assertEquals(1, properties.shardCount(1));
        assertEquals(3, properties.shardCount(6));
        assertThrows(IllegalStateException.class, () -> properties.shardCount(7));
    }

    @Test
    void refreshAndTimeoutSettingsMustLeavePositiveBoundedOperations() {
        SitemapManifestProperties properties = new SitemapManifestProperties();
        assertTrue(properties.isLifetimeValid());
        assertTrue(properties.isTimeoutValid());

        properties.setRefreshBeforeExpiry(properties.getTtl().dividedBy(2).plusMillis(1));
        assertFalse(properties.isLifetimeValid());

        properties.setQueryTimeout(Duration.ZERO);
        assertFalse(properties.isTimeoutValid());
    }
}
