package com.exskylab.koala.core.properties;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Component
@Validated
@ConfigurationProperties(prefix = "sitemap.manifest")
public class SitemapManifestProperties {

    private static final int PROTOCOL_MAX_SHARD_SIZE = 50_000;
    private static final int PROTOCOL_MAX_SHARDS = 50_000;

    @NotNull
    private Duration ttl = Duration.ofHours(1);

    @NotNull
    private Duration refreshBeforeExpiry = Duration.ofMinutes(15);

    @Min(1)
    @Max(PROTOCOL_MAX_SHARD_SIZE)
    private int shardSize = PROTOCOL_MAX_SHARD_SIZE;

    @Min(1)
    @Max(PROTOCOL_MAX_SHARDS)
    private int maxShards = PROTOCOL_MAX_SHARDS;

    @NotNull
    private Duration queryTimeout = Duration.ofSeconds(20);

    @NotNull
    private Duration lockTimeout = Duration.ofSeconds(5);

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(Duration ttl) {
        this.ttl = ttl;
    }

    public Duration getRefreshBeforeExpiry() {
        return refreshBeforeExpiry;
    }

    public void setRefreshBeforeExpiry(Duration refreshBeforeExpiry) {
        this.refreshBeforeExpiry = refreshBeforeExpiry;
    }

    public int getShardSize() {
        return shardSize;
    }

    public void setShardSize(int shardSize) {
        this.shardSize = shardSize;
    }

    public int getMaxShards() {
        return maxShards;
    }

    public void setMaxShards(int maxShards) {
        this.maxShards = maxShards;
    }

    public Duration getQueryTimeout() {
        return queryTimeout;
    }

    public void setQueryTimeout(Duration queryTimeout) {
        this.queryTimeout = queryTimeout;
    }

    public Duration getLockTimeout() {
        return lockTimeout;
    }

    public void setLockTimeout(Duration lockTimeout) {
        this.lockTimeout = lockTimeout;
    }

    public long maxEntries() {
        return Math.multiplyExact((long) shardSize, maxShards);
    }

    public int shardCount(long entryCount) {
        if (entryCount < 0 || entryCount > maxEntries()) {
            throw new IllegalStateException("Sitemap manifest entry count exceeds its configured bound");
        }
        return entryCount == 0 ? 0 : Math.toIntExact(((entryCount - 1) / shardSize) + 1);
    }

    public long queryTimeoutMillis() {
        return queryTimeout.toMillis();
    }

    public long lockTimeoutMillis() {
        return lockTimeout.toMillis();
    }

    @AssertTrue(message = "sitemap.manifest TTL and refresh window must leave a positive publication lease")
    public boolean isLifetimeValid() {
        return ttl != null && refreshBeforeExpiry != null
                && !ttl.isZero() && !ttl.isNegative()
                && !refreshBeforeExpiry.isZero() && !refreshBeforeExpiry.isNegative()
                && refreshBeforeExpiry.compareTo(ttl.dividedBy(2)) <= 0;
    }

    @AssertTrue(message = "sitemap.manifest query and lock timeouts must be between 1 ms and 30 seconds")
    public boolean isTimeoutValid() {
        return boundedTimeout(queryTimeout) && boundedTimeout(lockTimeout);
    }

    private boolean boundedTimeout(Duration timeout) {
        return timeout != null && timeout.toMillis() >= 1 && timeout.compareTo(Duration.ofSeconds(30)) <= 0;
    }
}
