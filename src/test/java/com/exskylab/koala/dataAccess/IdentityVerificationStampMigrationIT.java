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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class IdentityVerificationStampMigrationIT extends AbstractIntegrationTest {

    private static final String PROBE_DATABASE = "identity_verification_stamp_probe";
    private static final String CHANGE_LOG = "classpath:/db/changelog/2026/09/15-04-identity-reveals.xml";

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
    void createProbeDatabase() {
        JdbcTemplate maintenance = new JdbcTemplate(dataSource);
        maintenance.execute("drop database if exists " + PROBE_DATABASE + " with (force)");
        maintenance.execute("create database " + PROBE_DATABASE);

        probe = new JdbcTemplate(probeDataSource());
        probe.execute("""
                create table users (
                    id UUID primary key,
                    identity_verified BOOLEAN not null,
                    identity_evidence_digest VARCHAR(64)
                )
                """);
        probe.execute("""
                create table user_verifications (
                    id UUID primary key,
                    verification_type VARCHAR(31) not null,
                    user_id UUID,
                    is_verified BOOLEAN not null,
                    verified_at TIMESTAMPTZ,
                    is_deleted BOOLEAN not null
                )
                """);
        probe.execute("create table job_assignments (id UUID primary key)");
    }

    @AfterEach
    void dropProbeDatabase() {
        new JdbcTemplate(dataSource)
                .execute("drop database if exists " + PROBE_DATABASE + " with (force)");
    }

    @Test
    void documentAndChipVerificationsAreBackfilledAndLegacyRowsStayNull() {
        UUID documentUser = insertUser(true, null);
        UUID chipUser = insertUser(true, "a".repeat(64));
        UUID legacyUser = insertUser(true, null);
        UUID unverifiedUser = insertUser(false, null);
        UUID bothUser = insertUser(true, "b".repeat(64));
        Instant earlier = Instant.parse("2026-08-01T08:00:00Z");
        Instant later = Instant.parse("2026-09-01T10:15:30Z");
        insertReview(documentUser, true, earlier, false);
        insertReview(documentUser, true, later, false);
        insertReview(documentUser, true, Instant.parse("2026-09-10T00:00:00Z"), true);
        insertReview(legacyUser, false, null, false);
        insertReview(unverifiedUser, true, later, false);
        insertReview(bothUser, true, earlier, false);

        assertDoesNotThrow(this::applyChangeLog);

        assertStamp(documentUser, "DOCUMENT", later);
        assertStamp(chipUser, "CHIP", null);
        assertStamp(legacyUser, null, null);
        assertStamp(unverifiedUser, null, null);
        assertStamp(bothUser, "DOCUMENT", earlier);
    }

    @Test
    void reRunningTheBackfillDoesNotOverwriteAStampThatIsAlreadyThere() {
        UUID documentUser = insertUser(true, null);
        UUID chipUser = insertUser(true, "c".repeat(64));
        insertReview(documentUser, true, Instant.parse("2026-09-01T10:15:30Z"), false);
        applyChangeLog();
        Instant stampedByHand = Instant.parse("2026-09-02T00:00:00Z");
        probe.update("update users set identity_verified_at = ? where id = ?",
                Timestamp.from(stampedByHand), documentUser);
        probe.update("update users set identity_verification_method = 'DOCUMENT' where id = ?", chipUser);
        probe.update("delete from databasechangelog where id = '2026-09-15-04-02-backfill-identity-verification-stamp'");

        assertDoesNotThrow(this::applyChangeLog);

        assertStamp(documentUser, "DOCUMENT", stampedByHand);
        assertStamp(chipUser, "DOCUMENT", null);
        assertEquals(1, probe.queryForObject(
                "select count(*) from pg_indexes where indexname = 'idx_identity_reveals_worker_revealed_at'",
                Integer.class));
    }

    private void assertStamp(UUID userId, String method, Instant verifiedAt) {
        Map<String, Object> row = probe.queryForMap(
                "select identity_verification_method, identity_verified_at from users where id = ?", userId);
        assertEquals(method, row.get("identity_verification_method"));
        if (verifiedAt == null) {
            assertNull(row.get("identity_verified_at"));
        } else {
            assertEquals(verifiedAt, ((Timestamp) row.get("identity_verified_at")).toInstant());
        }
    }

    private UUID insertUser(boolean verified, String digest) {
        UUID id = UUID.randomUUID();
        probe.update("insert into users (id, identity_verified, identity_evidence_digest) values (?, ?, ?)",
                id, verified, digest);
        return id;
    }

    private void insertReview(UUID userId, boolean verified, Instant verifiedAt, boolean deleted) {
        probe.update("""
                insert into user_verifications (id, verification_type, user_id, is_verified, verified_at, is_deleted)
                values (?, 'IDENTITY', ?, ?, ?, ?)
                """, UUID.randomUUID(), userId, verified,
                verifiedAt == null ? null : Timestamp.from(verifiedAt), deleted);
    }

    private void applyChangeLog() {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(probeDataSource());
        liquibase.setChangeLog(CHANGE_LOG);
        liquibase.setResourceLoader(resourceLoader);
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
