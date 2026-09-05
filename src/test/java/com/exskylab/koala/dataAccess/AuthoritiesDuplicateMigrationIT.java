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

class AuthoritiesDuplicateMigrationIT extends AbstractIntegrationTest {

    private static final String PROBE_DATABASE = "authorities_duplicate_probe";
    private static final String CHANGE_LOG =
            "classpath:/db/changelog/2026/09/06-34-authorities-key-and-grant-provenance.xml";
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000aa");

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
    void createTheProbeDatabaseWithThePreMigrationSchema() {
        JdbcTemplate maintenance = new JdbcTemplate(dataSource);
        maintenance.execute("drop database if exists " + PROBE_DATABASE + " with (force)");
        maintenance.execute("create database " + PROBE_DATABASE);

        probe = new JdbcTemplate(probeDataSource());
        probe.execute("create table users (id UUID not null, constraint pk_users primary key (id))");
        probe.execute("""
                create table authorities (
                    user_id UUID not null,
                    role VARCHAR(255) not null
                )
                """);
        probe.execute("""
                alter table authorities add constraint fk_authorities_on_user
                    foreign key (user_id) references users (id)
                """);
        probe.update("insert into users (id) values (?)", USER_ID);
    }

    @AfterEach
    void dropTheProbeDatabase() {
        new JdbcTemplate(dataSource)
                .execute("drop database if exists " + PROBE_DATABASE + " with (force)");
    }

    @Test
    void theChangeLogRunsCleanOnADatabaseThatAlreadyCarriesDuplicateGrants() {
        grant("ROLE_USER");
        grant("ROLE_USER");
        grant("ROLE_ADMIN");
        grant("ROLE_ADMIN");
        grant("ROLE_ADMIN");

        assertDoesNotThrow(this::applyChangeLog,
                "Mukerrer satir tasiyan veritabaninda changelog patladi.");

        assertEquals(1, grantCount("ROLE_USER"), "Mukerrer ROLE_USER satiri temizlenmedi.");
        assertEquals(1, grantCount("ROLE_ADMIN"), "Mukerrer ROLE_ADMIN satiri temizlenmedi.");
        assertEquals(List.of("user_id", "role"), primaryKeyColumns(),
                "Bilesik birincil anahtar kurulmadi.");

        Integer withoutTimestamp = probe.queryForObject(
                "select count(*) from authorities where granted_at is null", Integer.class);
        assertEquals(0, withoutTimestamp, "Temizlikten sonra kalan satirlarin granted_at degeri bos.");
    }

    @Test
    void theChangeLogAlsoRunsOnADatabaseWithoutDuplicates() {
        grant("ROLE_USER");

        assertDoesNotThrow(this::applyChangeLog);

        assertEquals(1, grantCount("ROLE_USER"));
        assertEquals(List.of("user_id", "role"), primaryKeyColumns());
    }

    private void grant(String role) {
        probe.update("insert into authorities (user_id, role) values (?, ?)", USER_ID, role);
    }

    private int grantCount(String role) {
        Integer found = probe.queryForObject(
                "select count(*) from authorities where user_id = ? and role = ?",
                Integer.class, USER_ID, role);
        return found == null ? 0 : found;
    }

    private List<String> primaryKeyColumns() {
        return probe.queryForList("""
                select kcu.column_name
                from information_schema.table_constraints tc
                join information_schema.key_column_usage kcu
                  on kcu.constraint_name = tc.constraint_name and kcu.table_schema = tc.table_schema
                where tc.table_schema = 'public'
                  and tc.table_name = 'authorities'
                  and tc.constraint_type = 'PRIMARY KEY'
                order by kcu.ordinal_position
                """, String.class);
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
