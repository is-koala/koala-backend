package com.exskylab.koala.core.privacy;

import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountAnonymizationLoginAttemptIT extends AbstractIntegrationTest {

    @Autowired
    private AccountAnonymizer accountAnonymizer;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private UUID seedUser(String email) {
        UUID userId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into users
                    (id, first_name, username, email, email_verified, phone_verified,
                     identity_verified, is_employer, created_at, is_deleted)
                values (?, 'Silinecek', ?, ?, false, false, false, false, now(), false)
                """, userId, "purge-" + userId, email);
        return userId;
    }

    private UUID seedRequest(UUID userId) {
        UUID requestId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into account_deletion_requests
                    (id, user_id, subject_id, status, requested_at, created_at, is_deleted)
                values (?, ?, ?, 'REQUESTED', now(), now(), false)
                """, requestId, userId, userId);
        return requestId;
    }

    private UUID seedAttempt(UUID userId, String emailAttempted, String failureReason) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into login_attempts
                    (id, created_at, user_id, email_attempted, is_success, failure_reason,
                     ip_address, location, device_name, time_stamp, is_deleted)
                values (?, now(), ?, ?, false, ?, '203.0.113.7', 'Istanbul, TR', 'koala-web/1.0', now(), false)
                """, id, userId, emailAttempted, failureReason);
        return id;
    }

    private String emailOn(UUID attemptId) {
        List<String> values = jdbcTemplate.queryForList(
                "select email_attempted from login_attempts where id = ?", String.class, attemptId);
        assertEquals(1, values.size(), "Satır anonimleştirme sırasında yok olmamalıydı.");
        return values.get(0);
    }

    @Test
    void anAttemptThatCarriesTheAddressWithoutAnAccountIsAnonymizedToo() {
        String email = "silinen-" + UUID.randomUUID() + "@example.test";
        UUID userId = seedUser(email);
        UUID requestId = seedRequest(userId);
        UUID attributed = seedAttempt(userId, email, "Invalid email or password");
        UUID unattributed = seedAttempt(null, email, "No account for email");

        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> accountAnonymizer.anonymize(userId, requestId));

        assertNull(emailOn(attributed), "user_id taşıyan satır zaten anonimleştiriliyordu.");
        assertNull(emailOn(unattributed),
                "user_id'si olmayan ama aynı adresi taşıyan satır da anonimleştirilmeliydi.");
    }

    @Test
    void theAddressIsMatchedWithoutRegardToCase() {
        String local = "MixedCase-" + UUID.randomUUID();
        UUID userId = seedUser(local + "@Example.Test");
        UUID requestId = seedRequest(userId);
        UUID unattributed = seedAttempt(null,
                local.toLowerCase(Locale.ROOT) + "@example.test", "No account for email");

        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> accountAnonymizer.anonymize(userId, requestId));

        assertNull(emailOn(unattributed),
                "Giriş formuna farklı harf büyüklüğüyle yazılan aynı adres de eşleşmeliydi.");
    }

    @Test
    void theAddressTypedIntoTheFormInAnotherCaseIsMatchedToo() {
        String local = "FormCase-" + UUID.randomUUID();
        UUID userId = seedUser(local.toLowerCase(Locale.ROOT) + "@example.test");
        UUID requestId = seedRequest(userId);
        UUID unattributed = seedAttempt(null, local + "@Example.Test", "No account for email");

        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> accountAnonymizer.anonymize(userId, requestId));

        assertNull(emailOn(unattributed),
                "Giriş formuna karışık harfle yazılan adres, hesabın küçük harfli adresiyle eşleşmeliydi.");
    }

    @Test
    void anotherPersonsAttemptIsLeftAlone() {
        String email = "sahibi-" + UUID.randomUUID() + "@example.test";
        String other = "baskasi-" + UUID.randomUUID() + "@example.test";
        UUID userId = seedUser(email);
        UUID requestId = seedRequest(userId);
        UUID foreign = seedAttempt(null, other, "No account for email");

        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> accountAnonymizer.anonymize(userId, requestId));

        assertEquals(other, emailOn(foreign),
                "Başka bir adresi taşıyan satır bu silmeden etkilenmemeliydi.");
    }

    @Test
    void theRestOfTheUnattributedRowIsStrippedAsWell() {
        String email = "izli-" + UUID.randomUUID() + "@example.test";
        UUID userId = seedUser(email);
        UUID requestId = seedRequest(userId);
        UUID unattributed = seedAttempt(null, email, "No account for email");

        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> accountAnonymizer.anonymize(userId, requestId));

        List<String> leftovers = jdbcTemplate.queryForList("""
                select coalesce(failure_reason, '') || coalesce(ip_address, '') || coalesce(device_name, '')
                       || coalesce(location, '')
                from login_attempts where id = ?
                """, String.class, unattributed);
        assertTrue(leftovers.get(0).isEmpty(),
                "Gerekçe, IP, konum ve cihaz adı da temizlenmeliydi: " + leftovers.get(0));
    }
}
