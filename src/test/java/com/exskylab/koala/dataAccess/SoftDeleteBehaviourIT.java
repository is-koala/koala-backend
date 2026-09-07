package com.exskylab.koala.dataAccess;

import com.exskylab.koala.support.AbstractIntegrationTest;
import com.exskylab.koala.entities.LoginAttempt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoftDeleteBehaviourIT extends AbstractIntegrationTest {

    private static final Map<String, String> HARD_DELETE_TABLE_EXEMPTIONS = Map.of(
            "account_media_deletion_outbox",
            "geçici operasyon kuyruğu; storage silme onayından sonra satır fiziksel olarak kaldırılır",
            "staff_member_roles",
            "üyelik satırı; rol geri alınınca fiziksel olarak silinir ve iz denetim kaydında kalır (§2au)");

    @Autowired
    private LoginAttemptDao loginAttemptDao;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID persistAttempt() {
        LoginAttempt attempt = new LoginAttempt();
        attempt.setEmailAttempted("soft@example.com");
        attempt.setSuccess(false);
        return loginAttemptDao.save(attempt).getId();
    }

    @Test
    @Transactional
    void deletingAnEntityLeavesTheRowInTheTable() {
        UUID id = persistAttempt();
        loginAttemptDao.deleteById(id);
        loginAttemptDao.flush();

        Integer rowsInTable = jdbcTemplate.queryForObject(
                "select count(*) from login_attempts where id = ?", Integer.class, id);
        assertEquals(1, rowsInTable, "Satir tablodan fiziksel olarak silinmemeliydi.");

        Boolean flag = jdbcTemplate.queryForObject(
                "select is_deleted from login_attempts where id = ?", Boolean.class, id);
        assertTrue(Boolean.TRUE.equals(flag), "is_deleted true olmaliydi.");
    }

    @Test
    @Transactional
    void aDeletedRowDisappearsFromNormalQueries() {
        UUID id = persistAttempt();
        loginAttemptDao.deleteById(id);
        loginAttemptDao.flush();
        loginAttemptDao.findAll();

        assertTrue(loginAttemptDao.findById(id).isEmpty(),
                "Silinmis kayit varsayilan sorgudan dusmeliydi.");
    }

    @Test
    @Transactional
    void aLiveRowIsStillVisible() {
        UUID id = persistAttempt();
        loginAttemptDao.flush();

        assertTrue(loginAttemptDao.findById(id).isPresent());
    }

    @Test
    void reconciliationCanStillReachDeletedRowsThroughNativeSql() {
        UUID id = persistAttempt();
        loginAttemptDao.deleteById(id);
        loginAttemptDao.flush();

        List<String> emails = jdbcTemplate.queryForList(
                "select email_attempted from login_attempts where id = ?", String.class, id);

        assertEquals(1, emails.size(), "Mutabakat sorgusu silinmis kaydi hala gorebilmeli.");
        assertEquals("soft@example.com", emails.get(0));
    }

    @Test
    void thePartialUniqueIndexOnUserEmailOnlyCoversLiveRows() {
        List<String> indexes = jdbcTemplate.queryForList("""
                select indexdef from pg_indexes
                where schemaname = 'public' and indexname = 'uk_users_email_active'
                """, String.class);

        assertEquals(1, indexes.size(), "Kismi benzersizlik indeksi bulunamadi.");
        assertTrue(indexes.get(0).contains("is_deleted = false"),
                "Indeks yalnizca silinmemis satirlari kapsamali: " + indexes.get(0));
    }

    @Test
    void everyAuditedTableGotTheSoftDeleteColumns() {
        List<String> withoutFlag = jdbcTemplate.queryForList("""
                select c.table_name
                from information_schema.columns c
                join information_schema.tables t
                  on t.table_schema = c.table_schema and t.table_name = c.table_name
                where c.table_schema = 'public'
                  and t.table_type = 'BASE TABLE'
                  and c.column_name = 'created_at'
                  and c.table_name not in ('databasechangelog', 'databasechangeloglock')
                  and not exists (
                    select 1 from information_schema.columns d
                    where d.table_schema = 'public' and d.table_name = c.table_name
                      and d.column_name = 'is_deleted')
                """, String.class);
        withoutFlag.removeAll(HARD_DELETE_TABLE_EXEMPTIONS.keySet());

        assertTrue(withoutFlag.isEmpty(), "is_deleted kolonu olmayan tablolar: " + withoutFlag);
    }

    @Test
    void everyHardDeleteExemptionStillPointsAtALiveTable() {
        List<String> stale = HARD_DELETE_TABLE_EXEMPTIONS.keySet().stream()
                .filter(table -> Boolean.FALSE.equals(jdbcTemplate.queryForObject("""
                        select exists (
                            select 1 from information_schema.tables
                            where table_schema = 'public' and table_name = ?
                        )
                        """, Boolean.class, table)))
                .toList();

        assertTrue(stale.isEmpty(), "artık var olmayan hard-delete tablosu muaf: " + stale);
    }

    @Test
    void softDeleteIsNotAppliedToShortLivedRegistrationRecords() {
        Integer hasFlag = jdbcTemplate.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = 'public' and table_name = 'pending_registrations'
                  and column_name = 'is_deleted'
                """, Integer.class);

        assertEquals(1, hasFlag, "Kolon var ama entity uzerinde @SQLDelete olmamali.");

        assertFalse(readEntitySource("PendingRegistration").contains("@SQLDelete"),
                "PendingRegistration bilerek hard delete kaliyor.");
    }

    private String readEntitySource(String name) {
        try {
            return java.nio.file.Files.readString(java.nio.file.Path.of(
                    "src", "main", "java", "com", "exskylab", "koala", "entities", name + ".java"));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
