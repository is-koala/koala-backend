package com.exskylab.koala.dataAccess;

import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Repository
public class SitemapManifestDao {

    private static final long CREATION_LOCK_KEY = 5_419_166L;

    private final JdbcTemplate jdbc;
    private final DataSourceProperties dataSourceProperties;
    private final ReentrantLock localLock = new ReentrantLock();

    public SitemapManifestDao(JdbcTemplate jdbc, DataSourceProperties dataSourceProperties) {
        this.jdbc = jdbc;
        this.dataSourceProperties = dataSourceProperties;
    }

    public CreationLock acquireCreationLock(long timeoutMillis) {
        acquireLocalLock(timeoutMillis);
        Connection connection = null;
        try {
            connection = DriverManager.getConnection(dataSourceProperties.determineUrl(),
                    dataSourceProperties.determineUsername(), dataSourceProperties.determinePassword());
            connection.setNetworkTimeout(Runnable::run, Math.toIntExact(timeoutMillis));
            try (var timeout = connection.prepareStatement("""
                    select set_config('lock_timeout', ?, false),
                           set_config('statement_timeout', ?, false)
                    """)) {
                timeout.setString(1, timeoutMillis + "ms");
                timeout.setString(2, timeoutMillis + "ms");
                timeout.execute();
            }
            try (var lock = connection.prepareStatement("select pg_advisory_lock(?)")) {
                lock.setLong(1, CREATION_LOCK_KEY);
                lock.execute();
            }
            return new CreationLock(connection, localLock);
        } catch (SQLException | RuntimeException exception) {
            closeFailedConnection(connection);
            localLock.unlock();
            throw new CannotAcquireLockException("Could not acquire the sitemap manifest creation lock", exception);
        }
    }

    public void applyTimeouts(long queryTimeoutMillis, long lockTimeoutMillis) {
        jdbc.queryForObject("select set_config('statement_timeout', ?, true)", String.class,
                queryTimeoutMillis + "ms");
        jdbc.queryForObject("select set_config('lock_timeout', ?, true)", String.class,
                lockTimeoutMillis + "ms");
    }

    public Optional<ManifestRecord> findCurrent(Instant now) {
        return findOne("""
                select version, created_at, refresh_after, expires_at, entry_count, shard_size, shard_count
                from sitemap_manifests
                where refresh_after > ? and expires_at > ?
                order by created_at desc
                limit 1
                """, Timestamp.from(now), Timestamp.from(now));
    }

    public Optional<ManifestRecord> findLive(UUID version, Instant now) {
        return findOne("""
                select version, created_at, refresh_after, expires_at, entry_count, shard_size, shard_count
                from sitemap_manifests
                where version = ? and expires_at > ?
                """, version, Timestamp.from(now));
    }

    public int deleteExpired(Instant now) {
        return jdbc.update("delete from sitemap_manifests where expires_at <= ?", Timestamp.from(now));
    }

    public long countAvailableJobs(LocalDateTime availableAfter) {
        Long count = jdbc.queryForObject("""
                select count(*)
                from jobs
                where status = 'OPEN'
                  and is_deleted = false
                  and end_date_time is not null
                  and end_date_time > ?
                """, Long.class, availableAfter);
        return count == null ? 0 : count;
    }

    public void insertManifest(ManifestRecord manifest) {
        jdbc.update("""
                insert into sitemap_manifests
                    (version, created_at, refresh_after, expires_at, entry_count, shard_size, shard_count)
                values (?, ?, ?, ?, ?, ?, ?)
                """,
                manifest.version(), Timestamp.from(manifest.createdAt()), Timestamp.from(manifest.refreshAfter()),
                Timestamp.from(manifest.expiresAt()), manifest.entryCount(), manifest.shardSize(),
                manifest.shardCount());
    }

    public long materialize(UUID version, LocalDateTime availableAfter) {
        Long materialized = jdbc.execute((ConnectionCallback<Long>) connection -> {
            try (var statement = connection.prepareStatement("""
                    insert into sitemap_manifest_entries (manifest_version, ordinal, job_id, updated_at)
                    select ?, row_number() over (order by created_at asc, id asc) - 1, id, updated_at
                    from jobs
                    where status = 'OPEN'
                      and is_deleted = false
                      and end_date_time is not null
                      and end_date_time > ?
                    order by created_at asc, id asc
                    """)) {
                statement.setObject(1, version);
                statement.setObject(2, availableAfter);
                return statement.executeLargeUpdate();
            }
        });
        return materialized == null ? 0 : materialized;
    }

    public List<SitemapEntryRecord> readShard(UUID version, long firstOrdinal, long endOrdinal) {
        return jdbc.query("""
                        select job_id, updated_at
                        from sitemap_manifest_entries
                        where manifest_version = ? and ordinal >= ? and ordinal < ?
                        order by ordinal asc
                        """,
                (resultSet, rowNumber) -> new SitemapEntryRecord(
                        resultSet.getObject("job_id", UUID.class),
                        instantOrNull(resultSet.getTimestamp("updated_at"))),
                version, firstOrdinal, endOrdinal);
    }

    private Optional<ManifestRecord> findOne(String sql, Object... arguments) {
        List<ManifestRecord> rows = jdbc.query(sql, (resultSet, rowNumber) -> new ManifestRecord(
                resultSet.getObject("version", UUID.class),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("refresh_after").toInstant(),
                resultSet.getTimestamp("expires_at").toInstant(),
                resultSet.getLong("entry_count"),
                resultSet.getInt("shard_size"),
                resultSet.getInt("shard_count")), arguments);
        return rows.stream().findFirst();
    }

    private static Instant instantOrNull(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private void acquireLocalLock(long timeoutMillis) {
        try {
            if (!localLock.tryLock(timeoutMillis, TimeUnit.MILLISECONDS)) {
                throw new CannotAcquireLockException("Timed out waiting for the local sitemap creation lock");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CannotAcquireLockException("Interrupted while waiting for the local sitemap creation lock",
                    exception);
        }
    }

    private static void closeFailedConnection(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.abort(Runnable::run);
        } catch (SQLException ignored) {
            try {
                connection.close();
            } catch (SQLException closeIgnored) {
                ignored.addSuppressed(closeIgnored);
            }
        }
    }

    public record ManifestRecord(UUID version, Instant createdAt, Instant refreshAfter, Instant expiresAt,
                                 long entryCount, int shardSize, int shardCount) {
    }

    public record SitemapEntryRecord(UUID id, Instant updatedAt) {
    }

    public static final class CreationLock implements AutoCloseable {

        private final Connection connection;
        private final ReentrantLock localLock;
        private boolean closed;

        private CreationLock(Connection connection, ReentrantLock localLock) {
            this.connection = connection;
            this.localLock = localLock;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                try (var unlock = connection.prepareStatement("select pg_advisory_unlock(?)")) {
                    unlock.setLong(1, CREATION_LOCK_KEY);
                    unlock.execute();
                }
                connection.close();
            } catch (SQLException exception) {
                closeFailedConnection(connection);
                throw new IllegalStateException("Could not release the sitemap manifest creation lock", exception);
            } finally {
                localLock.unlock();
            }
        }
    }
}
