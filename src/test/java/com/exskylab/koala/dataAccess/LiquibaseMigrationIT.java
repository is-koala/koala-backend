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

    @Test
    void canonicalAddressSchemaAndIstanbulReferencesAreReady() {
        Integer legacyColumn = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = 'public' and table_name = 'addresses' and column_name = 'address_line'
                """, Integer.class);
        Integer canonicalColumns = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = 'public' and table_name = 'addresses'
                  and column_name in ('neighborhood', 'thoroughfare', 'building_number',
                                      'position_source', 'confirmed_at', 'canonical_version', 'needs_review')
                """, Integer.class);
        Integer activeIstanbulDistricts = jdbcTemplate.queryForObject("""
                select count(*) from districts d
                join cities c on c.id = d.city_id
                where c.name = 'İSTANBUL' and d.is_deleted = false
                """, Integer.class);
        String replacement = jdbcTemplate.queryForObject("""
                select d.name from district_aliases a
                join districts d on d.id = a.district_id
                where a.alias = 'EMİNÖNÜ'
                """, String.class);

        assertEquals(0, legacyColumn);
        assertEquals(7, canonicalColumns);
        assertEquals(39, activeIstanbulDistricts);
        assertEquals("FATİH", replacement);
    }
}
