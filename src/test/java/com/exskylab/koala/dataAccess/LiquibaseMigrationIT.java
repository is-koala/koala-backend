package com.exskylab.koala.dataAccess;

import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiquibaseMigrationIT extends AbstractIntegrationTest {

    private static final String SYSTEM_USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String SEED_EMAIL_DOMAIN = "qa.iskoala.invalid";

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
    void accountDeletionSubjectExpansionRemainsCompatibleWithThePreviousBinary() {
        String nullable = jdbcTemplate.queryForObject("""
                select is_nullable from information_schema.columns
                where table_schema = 'public' and table_name = 'account_deletion_requests'
                  and column_name = 'subject_id'
                """, String.class);
        UUID userId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into users
                    (id, first_name, username, email, email_verified, phone_verified,
                     identity_verified, is_employer, created_at, is_deleted)
                values (?, 'Eski Binary', ?, ?, false, false, false, false, now(), false)
                """, userId, "old-binary-" + userId, "old-binary-" + userId + "@example.test");

        jdbcTemplate.update("""
                insert into account_deletion_requests
                    (id, user_id, status, requested_at, created_at, is_deleted)
                values (?, ?, 'REQUESTED', now(), now(), false)
                """, requestId, userId);

        assertEquals("YES", nullable);
        assertEquals(userId, jdbcTemplate.queryForObject(
                "select subject_id from account_deletion_requests where id = ?", UUID.class, requestId));
    }

    @Test
    void coreTablesExist() {
        for (String table : new String[]{
                "users", "sessions", "devices", "jobs", "job_applications",
                "job_assignments", "companies", "addresses", "notifications"}) {
            assertEquals(1, tableCount(table), "Tablo bulunamadi: " + table);
        }
    }

    @Test
    void databaseShipsWithoutSampleData() {
        Integer system = jdbcTemplate.queryForObject(
                "select count(*) from users where id = ?::uuid",
                Integer.class, SYSTEM_USER_ID);
        assertEquals(1, system, "Denetim icin gereken SYSTEM kullanicisi yok.");

        Integer synthetic = jdbcTemplate.queryForObject(
                "select count(*) from users where email like ?",
                Integer.class, "%@" + SEED_EMAIL_DOMAIN);
        assertEquals(0, synthetic, "Kaldirilan seed'in sentetik hesaplari geri gelmis.");
    }

    @Test
    void theJobCategorySeedIsGone() {
        Integer categories = jdbcTemplate.queryForObject(
                "select count(*) from job_categories", Integer.class);

        assertEquals(0, categories, "Kaldirilan is kategorisi seed'i hala duruyor.");
    }

    @Test
    void qaSeedHistoryTableIsDropped() {
        assertEquals(0, tableCount("qa_marketplace_seed_versions"), "QA seed surum tablosu hala duruyor.");
    }

    @Test
    void referenceDataIsSeeded() {
        Integer countries = jdbcTemplate.queryForObject("select count(*) from countries", Integer.class);
        Integer cities = jdbcTemplate.queryForObject("select count(*) from cities", Integer.class);

        assertTrue(countries != null && countries > 0, "Ulke verisi yuklenmemis.");
        assertTrue(cities != null && cities > 0, "Sehir verisi yuklenmemis.");
    }

    @Test
    void everyAgreementTypeHasAVersionedWebContentUrl() {
        Map<String, String> contentUrls = new HashMap<>();
        jdbcTemplate.queryForList(
                        "select type, content_url from agreement_versions where version = '1.0' and locale = 'tr'")
                .forEach(row -> contentUrls.put(
                        (String) row.get("type"), (String) row.get("content_url")));

        assertEquals(Map.of(
                "TERMS_OF_SERVICE", "https://iskoala.com/kullanim-kosullari/v/1.0",
                "PRIVACY_POLICY", "https://iskoala.com/gizlilik-politikasi/v/1.0",
                "DISTANCE_SERVICE_CONTRACT", "https://iskoala.com/mesafeli-hizmet-sozlesmesi/v/1.0",
                "EXPLICIT_CONSENT", "https://iskoala.com/kvkk-aydinlatma/v/1.0#acik-riza",
                "COMMERCIAL_MESSAGE", "https://iskoala.com/kvkk-aydinlatma/v/1.0#ticari-ileti"),
                contentUrls);

    }

    @Test
    void distanceServiceContractIsPublishedWithoutBecomingARegistrationGate() {
        Boolean distanceContractMandatory = jdbcTemplate.queryForObject("""
                select mandatory from agreement_versions
                where type = 'DISTANCE_SERVICE_CONTRACT' and version = '1.0' and locale = 'tr'
                """, Boolean.class);
        assertEquals(false, distanceContractMandatory);
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

    private int tableCount(String table) {
        Integer found = jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_schema = 'public' and table_name = ?",
                Integer.class, table);
        return found == null ? 0 : found;
    }
}
