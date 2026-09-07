package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.LoginAttemptService;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginAttemptRetentionIT extends AbstractIntegrationTest {

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    @AfterEach
    void emptyTheTable() {
        jdbcTemplate.update("delete from login_attempts");
    }

    private UUID seedUser() {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into users
                    (id, first_name, username, email, email_verified, phone_verified,
                     identity_verified, is_employer, created_at, is_deleted)
                values (?, 'Saklama', ?, ?, false, false, false, false, now(), false)
                """, userId, "retention-" + userId, "retention-" + userId + "@example.test");
        return userId;
    }

    private UUID agedAttempt(UUID userId, String age, boolean softDeleted) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into login_attempts
                    (id, created_at, user_id, email_attempted, is_success, failure_reason,
                     ip_address, device_name, time_stamp, is_deleted)
                values (?, now() - (?)::interval, ?, ?, false, 'No account for email',
                        '203.0.113.4', 'koala-web/1.0', now() - (?)::interval, ?)
                """, id, age, userId, "deneme-" + id + "@example.test", age, softDeleted);
        return id;
    }

    private UUID agedAttempt(UUID userId, String age) {
        return agedAttempt(userId, age, false);
    }

    private boolean stillOnDisk(UUID id) {
        return jdbcTemplate.queryForObject(
                "select count(*) from login_attempts where id = ?", Integer.class, id) == 1;
    }

    @Test
    void anAddressThatBelongsToNoAccountIsDroppedAfterThirtyDays() {
        UUID expired = agedAttempt(null, "31 days");
        UUID fresh = agedAttempt(null, "29 days");

        assertEquals(1, loginAttemptService.purgeExpiredUnattributed());

        assertFalse(stillOnDisk(expired), "31 günlük sahipsiz satır silinmeliydi.");
        assertTrue(stillOnDisk(fresh), "29 günlük sahipsiz satır penceredeydi.");
    }

    @Test
    void anAttemptTiedToAnAccountOutlivesTheUnattributedWindow() {
        UUID userId = seedUser();
        UUID attributed = agedAttempt(userId, "200 days");

        assertEquals(0, loginAttemptService.purgeExpiredUnattributed());
        assertEquals(0, loginAttemptService.purgeExpiredAttributed());

        assertTrue(stillOnDisk(attributed),
                "Hesaba bağlı satırın ömrü sahipsiz pencereden uzun olmalıydı.");
    }

    @Test
    void anAttemptTiedToAnAccountIsDroppedAfterOneYear() {
        UUID userId = seedUser();
        UUID expired = agedAttempt(userId, "366 days");
        UUID fresh = agedAttempt(userId, "364 days");

        assertEquals(1, loginAttemptService.purgeExpiredAttributed());

        assertFalse(stillOnDisk(expired), "366 günlük bağlı satır silinmeliydi.");
        assertTrue(stillOnDisk(fresh), "364 günlük bağlı satır penceredeydi.");
    }

    @Test
    void theUnattributedSweepNeverReachesAnAccountsOwnHistory() {
        UUID userId = seedUser();
        UUID veryOld = agedAttempt(userId, "400 days");

        assertEquals(0, loginAttemptService.purgeExpiredUnattributed());

        assertTrue(stillOnDisk(veryOld),
                "Sahipsiz süpürme hesaba bağlı satıra hiç dokunmamalıydı.");
    }

    @Test
    void purgingRemovesTheRowFromDiskInsteadOfFlaggingIt() {
        UUID expired = agedAttempt(null, "60 days");

        loginAttemptService.purgeExpiredUnattributed();

        assertEquals(0, (int) jdbcTemplate.queryForObject(
                "select count(*) from login_attempts where id = ?", Integer.class, expired),
                "Saklama işi soft delete değil fiziksel silme yapmalı; işaretlenen satır adresi tutmaya devam ederdi.");
    }

    @Test
    void anAlreadySoftDeletedRowIsPurgedAsWell() {
        UUID expired = agedAttempt(null, "60 days", true);

        assertEquals(1, loginAttemptService.purgeExpiredUnattributed());

        assertFalse(stillOnDisk(expired),
                "is_deleted = true satır da süresi dolunca fiziksel olarak gitmeliydi.");
    }

    @Test
    void aRowWithoutAnEventTimestampFallsBackToItsCreationTime() {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into login_attempts
                    (id, created_at, user_id, email_attempted, is_success, time_stamp, is_deleted)
                values (?, now() - interval '90 days', null, 'zamansiz@example.test', false, null, false)
                """, id);

        assertEquals(1, loginAttemptService.purgeExpiredUnattributed());

        assertFalse(stillOnDisk(id),
                "time_stamp boş olan satır saklama işinden kaçamamalı.");
    }
}
