package com.exskylab.koala.webAPI;

import com.exskylab.koala.support.AbstractIntegrationTest;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "sitemap.manifest.shard-size=2",
        "sitemap.manifest.ttl=1h",
        "sitemap.manifest.refresh-before-expiry=15m"
})
class SitemapManifestIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DataSource dataSource;

    private final List<UUID> jobs = new ArrayList<>();

    @BeforeEach
    void clearManifests() {
        jdbc.update("delete from sitemap_manifests");
    }

    @AfterEach
    void cleanUp() {
        jdbc.execute("drop trigger if exists sitemap_manifest_test_pause on sitemap_manifest_entries");
        jdbc.execute("drop function if exists pause_sitemap_materialization()");
        jdbc.update("delete from sitemap_manifests");
        for (UUID job : jobs) {
            jdbc.update("delete from jobs where id = ?", job);
        }
        jobs.clear();
    }

    @Test
    void anAnonymousCallerCanCreateAnEmptyImmutableManifest() throws Exception {
        mockMvc.perform(get("/sitemap/jobs/manifest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").isNotEmpty())
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.data.entryCount").value(0))
                .andExpect(jsonPath("$.data.shardSize").value(2))
                .andExpect(jsonPath("$.data.shards").isEmpty());
    }

    @Test
    void tiedTimestampsProduceACompleteOrderedPrivacySafeShardUnion() throws Exception {
        Instant createdAt = Instant.parse("2026-09-09T08:00:00Z");
        UUID first = uuid(1);
        UUID second = uuid(2);
        UUID third = uuid(3);
        Instant secondUpdatedAt = Instant.parse("2026-09-09T09:15:00Z");
        insertJob(third, createdAt, null, "OPEN", Instant.now().plus(1, ChronoUnit.DAYS), false);
        insertJob(first, createdAt, null, "OPEN", Instant.now().plus(1, ChronoUnit.DAYS), false);
        insertJob(second, createdAt, secondUpdatedAt, "OPEN", Instant.now().plus(1, ChronoUnit.DAYS), false);
        insertJob(uuid(4), createdAt, null, "CLOSED", Instant.now().plus(1, ChronoUnit.DAYS), false);
        insertJob(uuid(5), createdAt, null, "OPEN", Instant.now().minus(1, ChronoUnit.MINUTES), false);
        insertJob(uuid(6), createdAt, null, "OPEN", Instant.now().plus(1, ChronoUnit.DAYS), true);

        JsonNode manifest = manifest();

        assertEquals(3, manifest.get("entryCount").asLong());
        assertEquals(2, manifest.get("shards").size());
        assertEquals(2, manifest.get("shards").get(0).get("entryCount").asInt());
        assertEquals(1, manifest.get("shards").get(1).get("entryCount").asInt());

        UUID version = UUID.fromString(manifest.get("version").asString());
        List<JsonNode> union = new ArrayList<>();
        union.addAll(entries(version, 0));
        union.addAll(entries(version, 1));

        assertEquals(List.of(first, second, third), union.stream()
                .map(entry -> UUID.fromString(entry.get("id").asString())).toList());
        assertEquals(3, new HashSet<>(union.stream().map(JsonNode::toString).toList()).size());
        assertTrue(union.stream().allMatch(entry -> entry.size() == 2
                && entry.has("id") && entry.has("updatedAt")));
        assertNull(union.get(0).get("updatedAt").asString(null));
        assertEquals(secondUpdatedAt, Instant.parse(union.get(1).get("updatedAt").asString()));
    }

    @Test
    void aManifestUnionDoesNotChangeAfterAvailabilityMutations() throws Exception {
        Instant createdAt = Instant.parse("2026-09-09T08:00:00Z");
        UUID open = uuid(11);
        UUID expiring = uuid(12);
        UUID closed = uuid(13);
        UUID insertedLater = uuid(14);
        insertJob(open, createdAt, null, "OPEN", Instant.now().plus(1, ChronoUnit.DAYS), false);
        insertJob(expiring, createdAt.plusSeconds(1), null, "OPEN", Instant.now().plus(1, ChronoUnit.DAYS), false);
        insertJob(closed, createdAt.plusSeconds(2), null, "CLOSED", Instant.now().plus(1, ChronoUnit.DAYS), false);

        UUID version = UUID.fromString(manifest().get("version").asString());
        List<UUID> before = idsIn(version);

        jdbc.update("update jobs set status = 'CLOSED' where id = ?", open);
        jdbc.update("update jobs set end_date_time = now() - interval '1 minute' where id = ?", expiring);
        jdbc.update("update jobs set status = 'OPEN' where id = ?", closed);
        insertJob(insertedLater, createdAt.plusSeconds(3), null, "OPEN",
                Instant.now().plus(1, ChronoUnit.DAYS), false);

        assertEquals(List.of(open, expiring), before);
        assertEquals(before, idsIn(version));
    }

    @Test
    void aRepeatableReadSnapshotSurvivesWritesThatOverlapMaterialization() throws Exception {
        Instant createdAt = Instant.parse("2026-09-09T08:00:00Z");
        UUID open = uuid(21);
        UUID expiring = uuid(22);
        UUID closed = uuid(23);
        UUID inserted = uuid(24);
        insertJob(open, createdAt, null, "OPEN", Instant.now().plus(1, ChronoUnit.DAYS), false);
        insertJob(expiring, createdAt.plusSeconds(1), null, "OPEN", Instant.now().plus(1, ChronoUnit.DAYS), false);
        insertJob(closed, createdAt.plusSeconds(2), null, "CLOSED", Instant.now().plus(1, ChronoUnit.DAYS), false);
        installMaterializationPause();

        HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);
        try (Connection blocker = DriverManager.getConnection(
                hikari.getJdbcUrl(), hikari.getUsername(), hikari.getPassword());
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            blocker.createStatement().execute("select pg_advisory_lock(99166)");
            Future<MvcResult> request = executor.submit(() -> mockMvc.perform(get("/sitemap/jobs/manifest"))
                    .andExpect(status().isOk()).andReturn());

            awaitBlockedMaterialization(blocker);
            blocker.createStatement().execute("update jobs set status = 'CLOSED' where id = '" + open + "'");
            blocker.createStatement().execute("update jobs set end_date_time = now() - interval '1 minute' where id = '"
                    + expiring + "'");
            blocker.createStatement().execute("update jobs set status = 'OPEN' where id = '" + closed + "'");
            insertJob(blocker, inserted, createdAt.plusSeconds(3), "OPEN");
            blocker.createStatement().execute("select pg_advisory_unlock(99166)");

            JsonNode manifest = objectMapper.readTree(request.get().getResponse().getContentAsByteArray()).get("data");
            UUID version = UUID.fromString(manifest.get("version").asString());
            assertEquals(2, manifest.get("entryCount").asInt());
            assertEquals(List.of(open, expiring), idsIn(version));
        }
    }

    @Test
    void anAdvertisedShardCannotBecomeEmptyDuringConcurrentExpiryCleanup() throws Exception {
        UUID job = uuid(25);
        insertJob(job, Instant.now().minusSeconds(60), null, "OPEN",
                Instant.now().plus(1, ChronoUnit.DAYS), false);
        UUID version = UUID.fromString(manifest().get("version").asString());

        HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);
        try (Connection blocker = DriverManager.getConnection(
                hikari.getJdbcUrl(), hikari.getUsername(), hikari.getPassword());
             ExecutorService executor = Executors.newSingleThreadExecutor()) {
            blocker.setAutoCommit(false);
            blocker.createStatement().execute("lock table sitemap_manifest_entries in access exclusive mode");
            Future<MvcResult> request = executor.submit(() -> mockMvc.perform(
                            get("/sitemap/jobs/manifests/{version}/shards/0", version))
                    .andExpect(status().isOk()).andReturn());

            awaitBlockedShardRead(blocker);
            try (var expire = blocker.prepareStatement("""
                    update sitemap_manifests
                    set created_at = now() - interval '3 hours',
                        refresh_after = now() - interval '2 hours',
                        expires_at = now() - interval '1 hour'
                    where version = ?
                    """)) {
                expire.setObject(1, version);
                expire.executeUpdate();
            }
            blocker.createStatement().execute("delete from sitemap_manifests where expires_at <= now()");
            blocker.commit();

            JsonNode data = objectMapper.readTree(request.get().getResponse().getContentAsByteArray()).get("data");
            assertEquals(List.of(job), data.get("entries").valueStream()
                    .map(entry -> UUID.fromString(entry.get("id").asString()))
                    .toList());
        }
    }

    @Test
    void unknownExpiredAndOutOfRangeShardsAreIndistinguishableNotFoundResponses() throws Exception {
        UUID job = uuid(31);
        insertJob(job, Instant.now().minusSeconds(60), null, "OPEN",
                Instant.now().plus(1, ChronoUnit.DAYS), false);
        UUID version = UUID.fromString(manifest().get("version").asString());

        assertManifestNotFound(UUID.randomUUID(), 0);
        assertManifestNotFound(version, 1);
        assertManifestNotFound(version, -1);

        jdbc.update("""
                update sitemap_manifests
                set created_at = now() - interval '3 hours',
                    refresh_after = now() - interval '2 hours',
                    expires_at = now() - interval '1 hour'
                where version = ?
                """, version);
        assertManifestNotFound(version, 0);

        mockMvc.perform(get("/sitemap/jobs/manifests/not-a-version/shards/0"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/sitemap/jobs/manifests/{version}/shards/not-a-shard", version))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aMissingFutureShardNeverAppearsInsideTheSameVersion() throws Exception {
        UUID first = uuid(41);
        insertJob(first, Instant.now().minusSeconds(60), null, "OPEN",
                Instant.now().plus(1, ChronoUnit.DAYS), false);
        UUID version = UUID.fromString(manifest().get("version").asString());

        assertManifestNotFound(version, 1);
        insertJob(uuid(42), Instant.now(), null, "OPEN", Instant.now().plus(1, ChronoUnit.DAYS), false);
        insertJob(uuid(43), Instant.now().plusSeconds(1), null, "OPEN",
                Instant.now().plus(1, ChronoUnit.DAYS), false);
        assertManifestNotFound(version, 1);
        assertEquals(List.of(first), idsIn(version));
    }

    @Test
    void aReplacementManifestDoesNotRetireAdvertisedShardsBeforeTheirTtl() throws Exception {
        UUID first = uuid(45);
        UUID second = uuid(46);
        insertJob(first, Instant.now().minusSeconds(60), null, "OPEN",
                Instant.now().plus(1, ChronoUnit.DAYS), false);
        UUID oldVersion = UUID.fromString(manifest().get("version").asString());
        jdbc.update("""
                update sitemap_manifests
                set created_at = now() - interval '2 hours',
                    refresh_after = now() - interval '1 minute',
                    expires_at = now() + interval '1 hour'
                where version = ?
                """, oldVersion);
        insertJob(second, Instant.now(), null, "OPEN", Instant.now().plus(1, ChronoUnit.DAYS), false);

        UUID replacement = UUID.fromString(manifest().get("version").asString());

        assertFalse(oldVersion.equals(replacement));
        assertEquals(List.of(first), idsIn(oldVersion));
        assertEquals(List.of(first, second), idsIn(replacement));
    }

    @Test
    void concurrentManifestRequestsReuseOneVersion() throws Exception {
        insertJob(uuid(51), Instant.now().minusSeconds(60), null, "OPEN",
                Instant.now().plus(1, ChronoUnit.DAYS), false);

        try (ExecutorService executor = Executors.newFixedThreadPool(8)) {
            List<Future<String>> requests = IntStream.range(0, 8)
                    .mapToObj(index -> executor.submit(() -> manifest().get("version").asString()))
                    .toList();
            Set<String> versions = new HashSet<>();
            for (Future<String> request : requests) {
                versions.add(request.get());
            }

            assertEquals(1, versions.size());
            assertEquals(1, jdbc.queryForObject("select count(*) from sitemap_manifests", Integer.class));
        }
    }

    private JsonNode manifest() throws Exception {
        MvcResult result = mockMvc.perform(get("/sitemap/jobs/manifest"))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsByteArray()).get("data");
    }

    private List<JsonNode> entries(UUID version, int shard) throws Exception {
        MvcResult result = mockMvc.perform(get("/sitemap/jobs/manifests/{version}/shards/{shard}", version, shard))
                .andExpect(status().isOk()).andReturn();
        List<JsonNode> entries = new ArrayList<>();
        objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .get("data").get("entries").forEach(entries::add);
        return entries;
    }

    private List<UUID> idsIn(UUID version) throws Exception {
        Integer shardCount = jdbc.queryForObject(
                "select shard_count from sitemap_manifests where version = ?", Integer.class, version);
        List<UUID> ids = new ArrayList<>();
        for (int shard = 0; shard < shardCount; shard++) {
            entries(version, shard).stream()
                    .map(entry -> UUID.fromString(entry.get("id").asString()))
                    .forEach(ids::add);
        }
        assertEquals(ids.size(), Set.copyOf(ids).size());
        return ids;
    }

    private void assertManifestNotFound(UUID version, int shard) throws Exception {
        mockMvc.perform(get("/sitemap/jobs/manifests/{version}/shards/{shard}", version, shard))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SITEMAP_MANIFEST_NOT_FOUND"));
    }

    private void insertJob(UUID id, Instant createdAt, Instant updatedAt, String status,
                           Instant endAt, boolean deleted) {
        jdbc.update("""
                insert into jobs
                    (id, created_at, updated_at, is_deleted, title, status, end_date_time, address_public)
                values (?, ?, ?, ?, ?, ?, ?, false)
                """, id, Timestamp.from(createdAt), updatedAt == null ? null : Timestamp.from(updatedAt),
                deleted, "Sitemap " + id, status, Timestamp.from(endAt));
        jobs.add(id);
    }

    private void insertJob(Connection connection, UUID id, Instant createdAt, String status) throws Exception {
        try (var statement = connection.prepareStatement("""
                insert into jobs
                    (id, created_at, is_deleted, title, status, end_date_time, address_public)
                values (?, ?, false, ?, ?, now() + interval '1 day', false)
                """)) {
            statement.setObject(1, id);
            statement.setTimestamp(2, Timestamp.from(createdAt));
            statement.setString(3, "Sitemap " + id);
            statement.setString(4, status);
            statement.executeUpdate();
        }
        jobs.add(id);
    }

    private void installMaterializationPause() {
        jdbc.execute("""
                create function pause_sitemap_materialization() returns trigger language plpgsql as $$
                begin
                    perform pg_advisory_xact_lock(99166);
                    return new;
                end
                $$
                """);
        jdbc.execute("""
                create trigger sitemap_manifest_test_pause
                before insert on sitemap_manifest_entries
                for each row execute function pause_sitemap_materialization()
                """);
    }

    private void awaitBlockedMaterialization(Connection connection) throws Exception {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(4).toNanos();
        while (System.nanoTime() < deadline) {
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("""
                         select exists(
                             select 1 from pg_locks
                             where locktype = 'advisory' and granted = false
                         )
                         """)) {
                result.next();
                if (result.getBoolean(1)) {
                    return;
                }
            }
            Thread.sleep(10);
        }
        assertFalse(true, "Manifest materialization never reached the controlled overlap seam");
    }

    private void awaitBlockedShardRead(Connection connection) throws Exception {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(4).toNanos();
        while (System.nanoTime() < deadline) {
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("""
                         select exists(
                             select 1
                             from pg_locks l
                             join pg_class c on c.oid = l.relation
                             where c.relname = 'sitemap_manifest_entries'
                               and l.mode = 'AccessShareLock'
                               and l.granted = false
                         )
                         """)) {
                result.next();
                if (result.getBoolean(1)) {
                    return;
                }
            }
            Thread.sleep(10);
        }
        assertFalse(true, "Shard read never reached the controlled expiry-cleanup seam");
    }

    private UUID uuid(long value) {
        return UUID.fromString("00000000-0000-0000-0000-%012d".formatted(value));
    }
}
