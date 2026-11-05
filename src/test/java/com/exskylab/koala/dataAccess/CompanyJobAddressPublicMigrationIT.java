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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyJobAddressPublicMigrationIT extends AbstractIntegrationTest {

    private static final String PROBE_DATABASE = "company_job_address_public_probe";
    private static final String CHANGE_LOG =
            "classpath:/db/changelog/2026/11/04-01-company-job-address-public-by-default.xml";

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
                    id UUID primary key,
                    employer_type VARCHAR(255),
                    company_id UUID,
                    address_public BOOLEAN not null default false,
                    is_deleted BOOLEAN not null default false
                )
                """);
    }

    @AfterEach
    void dropProbeDatabase() {
        new JdbcTemplate(dataSource).execute("drop database if exists " + PROBE_DATABASE + " with (force)");
    }

    @Test
    void everyCompanyJobPublishesItsAddressAndIndividualJobsStayHidden() {
        UUID hiddenCompanyJob = insertJob("COMPANY", UUID.randomUUID(), false);
        UUID publicCompanyJob = insertJob("COMPANY", UUID.randomUUID(), true);
        UUID individualJob = insertJob("INDIVIDUAL", null, false);
        UUID anonymizedCompanyShell = insertJob("COMPANY", null, false);

        applyChangeLog();

        assertTrue(addressPublic(hiddenCompanyJob));
        assertTrue(addressPublic(publicCompanyJob));
        assertFalse(addressPublic(individualJob), "bireysel ilanin adresi geri donuk de acilmaz");
        assertFalse(addressPublic(anonymizedCompanyShell),
                "sirketi olmayan COMPANY satiri anonimlestiricinin bireysel saydigi kabuktur, acilmaz");
    }

    private UUID insertJob(String employerType, UUID companyId, boolean addressPublic) {
        UUID id = UUID.randomUUID();
        probe.update("insert into jobs (id, employer_type, company_id, address_public) values (?, ?, ?, ?)",
                id, employerType, companyId, addressPublic);
        return id;
    }

    private boolean addressPublic(UUID jobId) {
        return Boolean.TRUE.equals(probe.queryForObject(
                "select address_public from jobs where id = ?", Boolean.class, jobId));
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
