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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BoardingEventDirectionBackfillMigrationIT extends AbstractIntegrationTest {

    private static final String PROBE_DATABASE = "boarding_direction_backfill_probe";
    private static final String CHANGE_LOG = "classpath:/db/changelog/2026/10/03-03-boarding-event-direction.xml";
    private static final String SOFT_DELETE = ", is_deleted BOOLEAN not null default false";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private ResourceLoader resourceLoader;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    private JdbcTemplate probe;

    private final UUID outboundShuttle = UUID.randomUUID();
    private final UUID returnShuttle = UUID.randomUUID();

    @BeforeEach
    void createPreMigrationSchema() {
        JdbcTemplate maintenance = new JdbcTemplate(dataSource);
        maintenance.execute("drop database if exists " + PROBE_DATABASE + " with (force)");
        maintenance.execute("create database " + PROBE_DATABASE);

        probe = new JdbcTemplate(probeDataSource());
        probe.execute("create table shuttles (id UUID primary key, direction VARCHAR(16) not null" + SOFT_DELETE + ")");
        probe.execute("create table seat_assignments (id UUID primary key, direction VARCHAR(16) not null"
                + SOFT_DELETE + ")");
        probe.execute("create table boarding_events (id UUID primary key, seat_assignment_id UUID not null, "
                + "shuttle_id UUID not null" + SOFT_DELETE + ")");
        probe.update("insert into shuttles (id, direction) values (?, 'OUTBOUND'), (?, 'RETURN')",
                outboundShuttle, returnShuttle);
    }

    @AfterEach
    void dropProbeDatabase() {
        new JdbcTemplate(dataSource).execute("drop database if exists " + PROBE_DATABASE + " with (force)");
    }

    @Test
    void anEventTakesTheDirectionOfItsSeatEvenAfterThePlanDroppedTheSeat() {
        UUID liveSeat = UUID.randomUUID();
        UUID droppedSeat = UUID.randomUUID();
        probe.update("insert into seat_assignments (id, direction, is_deleted) values (?, 'RETURN', false), "
                + "(?, 'OUTBOUND', true)", liveSeat, droppedSeat);
        UUID onTheLiveSeat = boarding(liveSeat, returnShuttle);
        UUID onTheDroppedSeat = boarding(droppedSeat, outboundShuttle);

        applyChangeLog();

        assertEquals("RETURN", directionOf(onTheLiveSeat));
        assertEquals("OUTBOUND", directionOf(onTheDroppedSeat));
    }

    @Test
    void anEventWhoseSeatRowIsGoneFallsBackToItsShuttle() {
        UUID orphan = boarding(UUID.randomUUID(), returnShuttle);
        UUID nowhere = boarding(UUID.randomUUID(), UUID.randomUUID());

        applyChangeLog();

        assertEquals("RETURN", directionOf(orphan));
        assertNull(directionOf(nowhere), "with neither seat nor shuttle there is nothing to derive, so nothing is made up");
    }

    private UUID boarding(UUID seatId, UUID shuttleId) {
        UUID id = UUID.randomUUID();
        probe.update("insert into boarding_events (id, seat_assignment_id, shuttle_id) values (?, ?, ?)",
                id, seatId, shuttleId);
        return id;
    }

    private String directionOf(UUID eventId) {
        return probe.queryForObject("select direction from boarding_events where id = ?", String.class, eventId);
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
