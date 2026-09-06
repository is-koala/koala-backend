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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CancellationRefundMigrationIT extends AbstractIntegrationTest {

    private static final String PROBE_DATABASE = "cancellation_refund_probe";
    private static final String CHANGE_LOG =
            "classpath:/db/changelog/2026/09/06-83-cancellation-refund.xml";

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
        probe.execute("create table refunds (id UUID primary key)");
        probe.execute("create table cancellations (id UUID primary key)");
    }

    @AfterEach
    void dropProbeDatabase() {
        new JdbcTemplate(dataSource)
                .execute("drop database if exists " + PROBE_DATABASE + " with (force)");
    }

    @Test
    void migrationKeepsLegacyRowsNullAndEnforcesFutureCausalLinks() {
        UUID refundId = UUID.randomUUID();
        UUID legacyCancellationId = UUID.randomUUID();
        UUID secondCancellationId = UUID.randomUUID();
        probe.update("insert into refunds (id) values (?)", refundId);
        probe.update("insert into cancellations (id) values (?)", legacyCancellationId);

        applyChangeLog();

        assertEquals(0, linkedRefundCount(legacyCancellationId));

        probe.update("update cancellations set refund_id = ? where id = ?", refundId, legacyCancellationId);
        probe.update("insert into cancellations (id) values (?)", secondCancellationId);
        assertThrows(DataIntegrityViolationException.class,
                () -> probe.update("update cancellations set refund_id = ? where id = ?",
                        refundId, secondCancellationId));
        assertThrows(DataIntegrityViolationException.class,
                () -> probe.update("update cancellations set refund_id = ? where id = ?",
                        UUID.randomUUID(), legacyCancellationId));
    }

    private int linkedRefundCount(UUID cancellationId) {
        Integer count = probe.queryForObject(
                "select count(refund_id) from cancellations where id = ?", Integer.class, cancellationId);
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
