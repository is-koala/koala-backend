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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageReportIdempotencyMigrationIT extends AbstractIntegrationTest {

    private static final String PROBE_DATABASE = "message_report_idempotency_probe";
    private static final String CHANGE_LOG =
            "classpath:/db/changelog/2026/09/09-02-message-report-idempotency.xml";

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
                create table messages (
                    id UUID primary key,
                    sender_id UUID not null
                )
                """);
        probe.execute("""
                create table reports (
                    id UUID primary key,
                    reporter_id UUID not null,
                    reported_user_id UUID,
                    message_id UUID,
                    is_deleted BOOLEAN not null
                )
                """);
    }

    @AfterEach
    void dropProbeDatabase() {
        new JdbcTemplate(dataSource)
                .execute("drop database if exists " + PROBE_DATABASE + " with (force)");
    }

    @Test
    void historicalActiveDuplicatesHaltWithoutDeletingEvidence() {
        UUID reporterId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        insertReport(reporterId, messageId, false);
        insertReport(reporterId, messageId, false);

        IllegalStateException failure = assertThrows(IllegalStateException.class, this::applyChangeLog);

        assertTrue(messageChain(failure).contains(
                "reconcile duplicate active reporter/message pairs before deployment"));
        assertEquals(2, probe.queryForObject("select count(*) from reports", Integer.class));
        assertEquals(0, indexCount());
    }

    @Test
    void distinctAndSoftDeletedHistoryAllowsTheUniqueIndex() {
        UUID reporterId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        insertReport(reporterId, messageId, false);
        insertReport(reporterId, messageId, true);
        insertReport(UUID.randomUUID(), messageId, false);

        assertDoesNotThrow(this::applyChangeLog);

        assertEquals(1, indexCount());
    }

    @Test
    void legacyMessageReportSenderIsBackfilledBeforeTheUniqueIndex() {
        UUID reporterId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID senderId = UUID.randomUUID();
        probe.update("insert into messages (id, sender_id) values (?, ?)", messageId, senderId);
        insertReport(reporterId, messageId, false);

        assertDoesNotThrow(this::applyChangeLog);

        assertEquals(senderId, probe.queryForObject(
                "select reported_user_id from reports where message_id = ?", UUID.class, messageId));
        assertEquals(1, indexCount());
    }

    private void insertReport(UUID reporterId, UUID messageId, boolean deleted) {
        probe.update("""
                insert into reports (id, reporter_id, reported_user_id, message_id, is_deleted)
                values (?, ?, null, ?, ?)
                """,
                UUID.randomUUID(), reporterId, messageId, deleted);
    }

    private int indexCount() {
        Integer count = probe.queryForObject("""
                select count(*) from pg_indexes
                where schemaname = 'public' and indexname = 'uk_reports_active_reporter_message'
                """, Integer.class);
        return count == null ? 0 : count;
    }

    private String messageChain(Throwable failure) {
        StringBuilder messages = new StringBuilder();
        Throwable current = failure;
        while (current != null) {
            messages.append(current.getMessage()).append('\n');
            current = current.getCause();
        }
        return messages.toString();
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
