package com.exskylab.koala.dataAccess;

import com.exskylab.koala.support.AbstractIntegrationTest;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SitemapManifestMigrationIT extends AbstractIntegrationTest {

    private static final String PROBE_DATABASE = "sitemap_manifest_probe";
    private static final String CHANGE_LOG = "classpath:/db/changelog/2026/09/09-02-sitemap-manifest.xml";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ResourceLoader resourceLoader;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    private JdbcTemplate probe;

    @BeforeEach
    void createPreMigrationSchema() {
        JdbcTemplate maintenance = new JdbcTemplate(dataSource);
        maintenance.execute("drop database if exists " + PROBE_DATABASE + " with (force)");
        maintenance.execute("create database " + PROBE_DATABASE);

        probe = new JdbcTemplate(probeDataSource());
        probe.execute("""
                create table jobs (
                    id uuid primary key,
                    created_at timestamptz not null,
                    updated_at timestamptz,
                    end_date_time timestamp,
                    status varchar(32) not null,
                    is_deleted boolean not null
                )
                """);
    }

    @AfterEach
    void dropProbeDatabase() {
        new JdbcTemplate(dataSource).execute("drop database if exists " + PROBE_DATABASE + " with (force)");
    }

    @Test
    void forwardAndRollbackProduceTheBoundedSnapshotSchema() {
        assertDoesNotThrow(this::applyChangeLog);

        assertEquals(2, probe.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema = 'public'
                  and table_name in ('sitemap_manifests', 'sitemap_manifest_entries')
                """, Integer.class));
        assertEquals(List.of("timestamp with time zone", "timestamp with time zone", "timestamp with time zone"),
                probe.queryForList("""
                        select data_type from information_schema.columns
                        where table_schema = 'public' and table_name = 'sitemap_manifests'
                          and column_name in ('created_at', 'refresh_after', 'expires_at')
                        order by column_name
                        """, String.class));

        String projectionIndex = probe.queryForObject(
                "select indexdef from pg_indexes where indexname = 'ix_jobs_sitemap_projection'", String.class);
        assertTrue(projectionIndex.contains("(created_at, id)"), projectionIndex);
        assertTrue(projectionIndex.contains("INCLUDE (updated_at, end_date_time)"), projectionIndex);
        assertTrue(projectionIndex.contains("status") && projectionIndex.contains("'OPEN'")
                && projectionIndex.contains("is_deleted = false"), projectionIndex);

        assertEquals(6, probe.queryForObject("""
                select count(*) from pg_constraint
                where conname in ('ck_sitemap_manifest_lifetime', 'ck_sitemap_manifest_entry_count',
                    'ck_sitemap_manifest_shard_size', 'ck_sitemap_manifest_shard_count',
                    'ck_sitemap_manifest_shard_math', 'ck_sitemap_manifest_entry_ordinal')
                """, Integer.class));
        assertEquals(1, probe.queryForObject("""
                select count(*) from information_schema.table_constraints
                where table_schema = 'public' and table_name = 'sitemap_manifest_entries'
                  and constraint_type = 'UNIQUE'
                  and constraint_name = 'uq_sitemap_manifest_entries_job'
                """, Integer.class));
        assertEquals(1, probe.queryForObject("""
                select count(*) from information_schema.table_constraints
                where table_schema = 'public' and table_name = 'sitemap_manifest_entries'
                  and constraint_type = 'FOREIGN KEY'
                  and constraint_name = 'fk_sitemap_manifest_entries_manifest'
                """, Integer.class));
    }

    @Test
    void sitemapSnapshotsArePhysicallyRemovedWithTheirEntries() {
        applyChangeLog();
        UUID version = UUID.randomUUID();

        assertEquals(0, probe.queryForObject("""
                select count(*)
                from information_schema.columns
                where table_schema = 'public'
                  and table_name in ('sitemap_manifests', 'sitemap_manifest_entries')
                  and column_name in ('is_deleted', 'deleted_at', 'deleted_by_id')
                """, Integer.class));

        probe.update("""
                insert into sitemap_manifests
                    (version, created_at, refresh_after, expires_at, entry_count, shard_size, shard_count)
                values (?, now(), now() + interval '30 minutes', now() + interval '1 hour', 1, 1, 1)
                """, version);
        probe.update("""
                insert into sitemap_manifest_entries (manifest_version, ordinal, job_id, updated_at)
                values (?, 0, ?, null)
                """, version, UUID.randomUUID());

        probe.update("delete from sitemap_manifests where version = ?", version);

        assertEquals(0, probe.queryForObject(
                "select count(*) from sitemap_manifests where version = ?", Integer.class, version));
        assertEquals(0, probe.queryForObject(
                "select count(*) from sitemap_manifest_entries where manifest_version = ?",
                Integer.class, version));
    }

    private void applyChangeLog() {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(probeDataSource());
        liquibase.setChangeLog(CHANGE_LOG);
        liquibase.setResourceLoader(resourceLoader);
        liquibase.setTestRollbackOnUpdate(true);
        try {
            liquibase.afterPropertiesSet();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private DataSource probeDataSource() {
        DriverManagerDataSource probeSource = new DriverManagerDataSource();
        probeSource.setUrl(probeUrl());
        probeSource.setUsername(username);
        probeSource.setPassword(password);
        return probeSource;
    }

    private String probeUrl() {
        try (Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            int database = url.lastIndexOf('/');
            int query = url.indexOf('?', database);
            String suffix = query < 0 ? "" : url.substring(query);
            return url.substring(0, database + 1) + PROBE_DATABASE + suffix;
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
