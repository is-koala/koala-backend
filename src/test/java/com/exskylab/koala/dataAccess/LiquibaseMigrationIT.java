package com.exskylab.koala.dataAccess;

import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiquibaseMigrationIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void allChangeSetsApplyToACleanDatabase() {
        Integer applied = jdbcTemplate.queryForObject(
                "select count(*) from databasechangelog", Integer.class);

        assertTrue(applied != null && applied > 0, "Hic changeset uygulanmamis.");

        Integer failed = jdbcTemplate.queryForObject(
                "select count(*) from databasechangelog where exectype not in ('EXECUTED', 'MARK_RAN')",
                Integer.class);

        assertEquals(0, failed, "Basarisiz ya da yeniden calistirilmis changeset var.");
    }

    @Test
    void coreTablesExist() {
        for (String table : new String[]{
                "users", "sessions", "devices", "jobs", "job_applications",
                "job_assignments", "companies", "addresses", "notifications"}) {
            Integer found = jdbcTemplate.queryForObject(
                    "select count(*) from information_schema.tables where table_schema = 'public' and table_name = ?",
                    Integer.class, table);
            assertEquals(1, found, "Tablo bulunamadi: " + table);
        }
    }

    @Test
    void referenceDataIsSeeded() {
        Integer countries = jdbcTemplate.queryForObject("select count(*) from countries", Integer.class);
        Integer cities = jdbcTemplate.queryForObject("select count(*) from cities", Integer.class);

        assertTrue(countries != null && countries > 0, "Ulke verisi yuklenmemis.");
        assertTrue(cities != null && cities > 0, "Sehir verisi yuklenmemis.");
    }
}
