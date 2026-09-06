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
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class LegacyPaymentReconciliationMigrationIT extends AbstractIntegrationTest {

    private static final String PROBE_DATABASE = "legacy_payment_reconciliation_probe";
    private static final String CHANGE_LOG =
            "classpath:/db/changelog/2026/09/06-85-legacy-payment-reconciliation.xml";

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
    void createLegacySchema() {
        JdbcTemplate maintenance = new JdbcTemplate(dataSource);
        maintenance.execute("drop database if exists " + PROBE_DATABASE + " with (force)");
        maintenance.execute("create database " + PROBE_DATABASE);

        probe = new JdbcTemplate(probeDataSource());
        probe.execute("""
                create table payments (
                    id uuid primary key,
                    status varchar(32) not null,
                    provider_token varchar(512),
                    provider_submerchant_key varchar(255),
                    next_verification_at timestamptz,
                    failure_reason varchar(512),
                    is_deleted boolean not null default false
                )
                """);
    }

    @AfterEach
    void dropProbeDatabase() {
        new JdbcTemplate(dataSource)
                .execute("drop database if exists " + PROBE_DATABASE + " with (force)");
    }

    @Test
    void legacyAttemptsWithoutAnImmutableSubmerchantSnapshotMoveToManualReview() {
        UUID legacy = UUID.randomUUID();
        UUID completeSnapshot = UUID.randomUUID();
        UUID terminal = UUID.randomUUID();
        probe.update("""
                insert into payments
                    (id, status, provider_token, provider_submerchant_key,
                     next_verification_at, failure_reason, is_deleted)
                values (?, 'IN_REVIEW', 'legacy-token', null, now(), null, false),
                       (?, 'VERIFY_PENDING', 'current-token', 'submerchant-key', now(), null, false),
                       (?, 'FAILED', 'terminal-token', null, now(), 'declined', false)
                """, legacy, completeSnapshot, terminal);

        applyChangeLog();

        assertEquals("VERIFY_PENDING", value(legacy, "status", String.class));
        assertEquals("{payment.verification.pending}", value(legacy, "failure_reason", String.class));
        assertNull(value(legacy, "next_verification_at", OffsetDateTime.class));

        assertEquals("VERIFY_PENDING", value(completeSnapshot, "status", String.class));
        assertNotNull(value(completeSnapshot, "next_verification_at", OffsetDateTime.class));

        assertEquals("FAILED", value(terminal, "status", String.class));
        assertEquals("declined", value(terminal, "failure_reason", String.class));
        assertNotNull(value(terminal, "next_verification_at", OffsetDateTime.class));
    }

    private <T> T value(UUID id, String column, Class<T> type) {
        return probe.queryForObject("select " + column + " from payments where id = ?", type, id);
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
