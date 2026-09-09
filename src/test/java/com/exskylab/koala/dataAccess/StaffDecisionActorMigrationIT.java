package com.exskylab.koala.dataAccess;

import com.exskylab.koala.support.AbstractIntegrationTest;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ResourceLoader;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StaffDecisionActorMigrationIT extends AbstractIntegrationTest {

    private static final String PROBE_DATABASE = "staff_decision_actor_probe";
    private static final String CHANGE_LOG =
            "classpath:/db/changelog/2026/09/09-01-staff-decision-actors.xml";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ResourceLoader resourceLoader;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    private JdbcTemplate probe;

    private final UUID userId = UUID.randomUUID();
    private final UUID firstStaffId = UUID.randomUUID();
    private final UUID secondStaffId = UUID.randomUUID();
    private final UUID disputeId = UUID.randomUUID();
    private final UUID reportId = UUID.randomUUID();

    @BeforeEach
    void createPreMigrationSchema() {
        JdbcTemplate maintenance = new JdbcTemplate(dataSource);
        maintenance.execute("drop database if exists " + PROBE_DATABASE + " with (force)");
        maintenance.execute("create database " + PROBE_DATABASE);

        probe = new JdbcTemplate(probeDataSource());
        probe.execute("create table users (id UUID primary key)");
        probe.execute("create table staff_members (id UUID primary key)");
        probe.execute("""
                create table disputes (
                    id UUID primary key,
                    status VARCHAR(16) not null,
                    resolved_by_id UUID
                )
                """);
        probe.execute("""
                create table reports (
                    id UUID primary key,
                    status VARCHAR(16) not null,
                    reviewed_by_id UUID
                )
                """);
        probe.execute("""
                create table admin_audit_entries (
                    id UUID primary key,
                    occurred_at TIMESTAMPTZ not null,
                    actor_staff_id UUID,
                    action VARCHAR(96) not null,
                    resource_id VARCHAR(128) not null,
                    allowed BOOLEAN not null
                )
                """);

        probe.update("insert into users (id) values (?)", userId);
        probe.update("insert into staff_members (id) values (?), (?)", firstStaffId, secondStaffId);
        probe.update("insert into disputes (id, status) values (?, 'RESOLVED')", disputeId);
        probe.update("insert into reports (id, status) values (?, 'ACTIONED')", reportId);

        audit(firstStaffId, "dispute.resolve", disputeId, Instant.parse("2026-09-09T00:00:00Z"));
        audit(secondStaffId, "dispute.resolve", disputeId, Instant.parse("2026-09-09T00:01:00Z"));
        audit(userId, "report.review", reportId, Instant.parse("2026-09-09T00:02:00Z"));
    }

    @AfterEach
    void dropProbeDatabase() {
        new JdbcTemplate(dataSource)
                .execute("drop database if exists " + PROBE_DATABASE + " with (force)");
    }

    @Test
    void ambiguousAuditAttemptsStayNullAndFutureActorsAreConstrained() {
        assertDoesNotThrow(this::applyChangeLog,
                "Belirsiz audit geçmişi migration açılışını düşürmemeli.");

        assertEquals(0, actorCount("disputes", "resolved_by_staff_id", disputeId));
        assertEquals(0, actorCount("reports", "reviewed_by_staff_id", reportId));

        probe.update("update disputes set resolved_by_staff_id = ? where id = ?", firstStaffId, disputeId);
        assertThrows(DataIntegrityViolationException.class,
                () -> probe.update("update disputes set resolved_by_id = ? where id = ?", userId, disputeId));
        assertThrows(DataIntegrityViolationException.class,
                () -> probe.update("update reports set reviewed_by_staff_id = ? where id = ?",
                        UUID.randomUUID(), reportId));
    }

    private void audit(UUID actorId, String action, UUID resourceId, Instant occurredAt) {
        probe.update("""
                insert into admin_audit_entries
                    (id, occurred_at, actor_staff_id, action, resource_id, allowed)
                values (?, ?, ?, ?, ?, true)
                """, UUID.randomUUID(), Timestamp.from(occurredAt), actorId, action, resourceId.toString());
    }

    private int actorCount(String table, String column, UUID id) {
        Integer count = probe.queryForObject(
                "select count(" + column + ") from " + table + " where id = ?", Integer.class, id);
        return count == null ? 0 : count;
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
