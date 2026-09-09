package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.SitemapManifestService;
import com.exskylab.koala.core.constants.SitemapMessages;
import com.exskylab.koala.core.dtos.sitemap.response.SitemapManifestDto;
import com.exskylab.koala.core.dtos.sitemap.response.SitemapJobDto;
import com.exskylab.koala.core.dtos.sitemap.response.SitemapShardDto;
import com.exskylab.koala.core.dtos.sitemap.response.SitemapShardMetadataDto;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.JobAvailability;
import com.exskylab.koala.core.monitoring.SitemapManifestMetrics;
import com.exskylab.koala.core.properties.SitemapManifestProperties;
import com.exskylab.koala.dataAccess.SitemapManifestDao;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class SitemapManifestManager implements SitemapManifestService {

    private final SitemapManifestDao sitemapManifestDao;
    private final SitemapManifestProperties properties;
    private final JobAvailability jobAvailability;
    private final SitemapManifestMetrics metrics;
    private final Clock clock;
    private final TransactionTemplate readTransaction;
    private final TransactionTemplate shardTransaction;
    private final TransactionTemplate snapshotTransaction;

    public SitemapManifestManager(SitemapManifestDao sitemapManifestDao, SitemapManifestProperties properties,
                                  JobAvailability jobAvailability, SitemapManifestMetrics metrics, Clock clock,
                                  PlatformTransactionManager transactionManager) {
        this.sitemapManifestDao = sitemapManifestDao;
        this.properties = properties;
        this.jobAvailability = jobAvailability;
        this.metrics = metrics;
        this.clock = clock;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
        this.readTransaction.setTimeout(30);
        this.shardTransaction = new TransactionTemplate(transactionManager);
        this.shardTransaction.setReadOnly(true);
        this.shardTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.shardTransaction.setTimeout(30);
        this.snapshotTransaction = new TransactionTemplate(transactionManager);
        this.snapshotTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        this.snapshotTransaction.setTimeout(30);
    }

    @Override
    public SitemapManifestDto getManifest() {
        try {
            Instant now = clock.instant();
            var current = current(now);
            if (current.isPresent()) {
                metrics.manifestHit();
                return toDto(current.get());
            }
            CreationResult result = createOrGet();
            if (result.created()) {
                metrics.manifestCreated();
            } else {
                metrics.manifestHit();
            }
            return toDto(result.manifest());
        } catch (RuntimeException exception) {
            metrics.manifestFailed();
            throw exception;
        }
    }

    @Override
    public SitemapShardDto getShard(UUID version, int shard) {
        ShardRecord found;
        try {
            var result = shard(version, shard, clock.instant());
            if (result.isEmpty()) {
                metrics.shardNotFound();
                throw new ResourceNotFoundException(SitemapMessages.MANIFEST_NOT_FOUND);
            }
            found = result.get();
        } catch (ResourceNotFoundException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            metrics.shardFailed();
            throw exception;
        }
        metrics.shardServed();
        return new SitemapShardDto(version, shard, found.entries());
    }

    private Optional<SitemapManifestDao.ManifestRecord> current(Instant now) {
        return Objects.requireNonNull(readTransaction.execute(status -> {
            applyTimeouts();
            return sitemapManifestDao.findCurrent(now);
        }));
    }

    private Optional<ShardRecord> shard(UUID version, int shard, Instant now) {
        return Objects.requireNonNull(shardTransaction.execute(status -> {
            applyTimeouts();
            Optional<SitemapManifestDao.ManifestRecord> found = sitemapManifestDao.findLive(version, now);
            if (found.isEmpty() || shard < 0 || shard >= found.get().shardCount()) {
                return Optional.empty();
            }
            SitemapManifestDao.ManifestRecord manifest = found.get();
            long firstOrdinal = Math.multiplyExact((long) shard, manifest.shardSize());
            long endOrdinal = Math.addExact(firstOrdinal, manifest.shardSize());
            List<SitemapManifestDao.SitemapEntryRecord> records =
                    sitemapManifestDao.readShard(version, firstOrdinal, endOrdinal);
            if (records.size() > manifest.shardSize()) {
                throw new IllegalStateException("Sitemap shard exceeded its declared bound");
            }
            List<SitemapJobDto> entries = records.stream()
                    .map(entry -> new SitemapJobDto(entry.id(), entry.updatedAt()))
                    .toList();
            return Optional.of(new ShardRecord(entries));
        }));
    }

    private CreationResult createOrGet() {
        try (var ignored = sitemapManifestDao.acquireCreationLock(properties.lockTimeoutMillis())) {
            var current = current(clock.instant());
            if (current.isPresent()) {
                return new CreationResult(current.get(), false);
            }
            SitemapManifestDao.ManifestRecord created = Objects.requireNonNull(
                    snapshotTransaction.execute(status -> create()));
            return new CreationResult(created, true);
        }
    }

    private SitemapManifestDao.ManifestRecord create() {
        applyTimeouts();
        Instant now = clock.instant();
        sitemapManifestDao.deleteExpired(now);
        LocalDateTime availableAfter = jobAvailability.currentTime();
        long entryCount = sitemapManifestDao.countAvailableJobs(availableAfter);
        int shardCount = properties.shardCount(entryCount);
        Instant expiresAt = now.plus(properties.getTtl());
        Instant refreshAfter = expiresAt.minus(properties.getRefreshBeforeExpiry());
        var manifest = new SitemapManifestDao.ManifestRecord(
                UUID.randomUUID(), now, refreshAfter, expiresAt, entryCount,
                properties.getShardSize(), shardCount);

        sitemapManifestDao.insertManifest(manifest);
        long materialized = sitemapManifestDao.materialize(manifest.version(), availableAfter);
        if (materialized != entryCount) {
            throw new IllegalStateException("Sitemap snapshot count changed inside its transaction");
        }
        log.info("Created sitemap manifest version={} entries={} shards={} expiresAt={}",
                manifest.version(), manifest.entryCount(), manifest.shardCount(), manifest.expiresAt());
        return manifest;
    }

    private void applyTimeouts() {
        sitemapManifestDao.applyTimeouts(properties.queryTimeoutMillis(), properties.lockTimeoutMillis());
    }

    private SitemapManifestDto toDto(SitemapManifestDao.ManifestRecord manifest) {
        List<SitemapShardMetadataDto> shards = new ArrayList<>(manifest.shardCount());
        for (int shard = 0; shard < manifest.shardCount(); shard++) {
            long first = Math.multiplyExact((long) shard, manifest.shardSize());
            int count = Math.toIntExact(Math.min(manifest.shardSize(), manifest.entryCount() - first));
            shards.add(new SitemapShardMetadataDto(shard, count));
        }
        return new SitemapManifestDto(manifest.version(), manifest.createdAt(), manifest.expiresAt(),
                manifest.entryCount(), manifest.shardSize(), List.copyOf(shards));
    }

    private record CreationResult(SitemapManifestDao.ManifestRecord manifest, boolean created) {
    }

    private record ShardRecord(List<SitemapJobDto> entries) {
    }
}
