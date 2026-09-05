package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.LegalService;
import com.exskylab.koala.core.constants.LegalMessages;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Transactional
class AccountDeletionLifecycleIT extends AbstractIntegrationTest {

    @Autowired
    private LegalService legalService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void aCleanAccountIsAnonymizedIdempotentlyWhileFinancialAndConsentRecordsRemain() {
        UUID userId = UUID.randomUUID();
        UUID employerId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();

        UUID countryId = jdbcTemplate.queryForObject("select id from countries limit 1", UUID.class);
        UUID cityId = jdbcTemplate.queryForObject("select id from cities where country_id = ? limit 1",
                UUID.class, countryId);
        UUID districtId = jdbcTemplate.queryForObject("select id from districts where city_id = ? limit 1",
                UUID.class, cityId);
        UUID agreementId = jdbcTemplate.queryForObject("select id from agreement_versions limit 1", UUID.class);

        jdbcTemplate.update("""
                insert into addresses
                    (id, country_id, city_id, district_id, neighborhood, thoroughfare,
                     building_number, latitude, longitude, canonical_version, needs_review,
                     created_at, is_deleted)
                values (?, ?, ?, ?, 'Deneme Mahallesi', 'Örnek Sokak', '12', 41.012345,
                        29.012345, 1, false, now(), false)
                """, addressId, countryId, cityId, districtId);
        jdbcTemplate.update("""
                insert into media
                    (id, media_type, file_name, file_type, file_url, file_size, created_at, is_deleted)
                values (?, 'FILE', 'ozgecmis.pdf', 'application/pdf', 'files/missing-test.pdf', 20, now(), false)
                """, mediaId);
        jdbcTemplate.update("""
                insert into users
                    (id, first_name, last_name, phone_number, username, email, password,
                     tc_identity_number, tc_document_number, address_id, cv_id,
                     email_verified, phone_verified, identity_verified, is_employer,
                     created_at, is_deleted)
                values (?, 'Ayşe', 'Deneme', '05555555555', ?, ?, 'hash', '11111111110',
                        'DOC123', ?, ?, true, true, true, false, now(), false)
                """, userId, "worker-" + userId, "worker-" + userId + "@example.test", addressId, mediaId);
        jdbcTemplate.update("""
                insert into users (id, first_name, username, email, is_employer, created_at, is_deleted)
                values (?, 'İşveren', ?, ?, true, now(), false)
                """, employerId, "employer-" + employerId, "employer-" + employerId + "@example.test");
        jdbcTemplate.update("update media set created_by_id = ? where id = ?", userId, mediaId);
        jdbcTemplate.update("""
                insert into consent_records
                    (id, user_id, agreement_version_id, granted, recorded_at, ip_address,
                     user_agent, created_at, is_deleted)
                values (?, ?, ?, true, now(), '198.51.100.4', 'test-agent', now(), false)
                """, UUID.randomUUID(), userId, agreementId);
        jdbcTemplate.update("""
                insert into bank_accounts
                    (id, user_id, iban, account_holder_name, is_primary, is_active, created_at, is_deleted)
                values (?, ?, 'TR000000000000000000000001', 'Ayşe Deneme', true, true, now(), false)
                """, UUID.randomUUID(), userId);
        jdbcTemplate.update("insert into jobs (id, creator_id, title, created_at, is_deleted) values (?, ?, 'Deneme işi', now(), false)",
                jobId, employerId);
        jdbcTemplate.update("""
                insert into job_assignments
                    (id, job_id, worker_id, status, payment_status, created_at, is_deleted)
                values (?, ?, ?, 'COMPLETED', 'RELEASED_TO_WORKER', now(), false)
                """, assignmentId, jobId, userId);
        jdbcTemplate.update("""
                insert into payments
                    (id, job_assignment_id, conversation_id, status, currency, created_at, is_deleted)
                values (?, ?, ?, 'SUCCEEDED', 'TRY', now(), false)
                """, paymentId, assignmentId, "payment-" + paymentId);
        jdbcTemplate.update("""
                insert into conversations
                    (id, job_id, employer_id, worker_id, created_at, is_deleted)
                values (?, ?, ?, ?, now(), false)
                """, conversationId, jobId, employerId, userId);
        jdbcTemplate.update("""
                insert into messages
                    (id, conversation_id, sender_id, body, sent_at, created_at, is_deleted)
                values (?, ?, ?, 'Telefonum 05555555555', now(), now(), false)
                """, UUID.randomUUID(), conversationId, userId);
        jdbcTemplate.update("""
                insert into account_deletion_requests
                    (id, user_id, subject_id, status, reason, requested_at, created_at, is_deleted)
                values (?, ?, ?, 'REQUESTED', 'Artık kullanmıyorum', now() - interval '31 days', now(), false)
                """, requestId, userId, userId);

        assertTrue(legalService.processDeletionRequest(requestId));
        assertFalse(legalService.processDeletionRequest(requestId));

        assertTrue(Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select is_deleted from users where id = ?", Boolean.class, userId)));
        assertNull(jdbcTemplate.queryForObject(
                "select tc_identity_number from users where id = ?", String.class, userId));
        assertEquals("[anonymized]", jdbcTemplate.queryForObject(
                "select body from messages where sender_id = ?", String.class, userId));
        assertNull(jdbcTemplate.queryForObject(
                "select file_url from media where id = ?", String.class, mediaId));
        assertEquals("[anonymized]", jdbcTemplate.queryForObject(
                "select iban from bank_accounts where user_id = ?", String.class, userId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from payments where id = ?", Integer.class, paymentId));
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from consent_records where user_id = ?", Integer.class, userId));
        assertNull(jdbcTemplate.queryForObject(
                "select ip_address from consent_records where user_id = ?", String.class, userId));
        assertNull(jdbcTemplate.queryForObject(
                "select user_agent from consent_records where user_id = ?", String.class, userId));
        assertEquals("ANONYMIZED", jdbcTemplate.queryForObject(
                "select status from account_deletion_requests where id = ?", String.class, requestId));
        assertNull(jdbcTemplate.queryForObject(
                "select user_id from account_deletion_requests where id = ?", UUID.class, requestId));
        assertEquals(6, jdbcTemplate.queryForObject(
                "select count(*) from account_anonymization_audit where request_id = ?", Integer.class, requestId));
    }

    @Test
    void pendingPaymentKeepsTheRequestWaitingWithAVisibleReason() {
        UUID userId = UUID.randomUUID();
        UUID employerId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();

        jdbcTemplate.update("""
                insert into users
                    (id, first_name, username, email, email_verified, phone_verified,
                     identity_verified, is_employer, created_at, is_deleted)
                values (?, 'Çalışan', ?, ?, false, false, false, false, now(), false),
                       (?, 'İşveren', ?, ?, false, false, false, true, now(), false)
                """, userId, "pending-worker-" + userId, "pending-worker-" + userId + "@example.test",
                employerId, "pending-employer-" + employerId, "pending-employer-" + employerId + "@example.test");
        jdbcTemplate.update("insert into jobs (id, creator_id, title, created_at, is_deleted) values (?, ?, 'Bekleyen ödeme', now(), false)",
                jobId, employerId);
        jdbcTemplate.update("""
                insert into job_assignments
                    (id, job_id, worker_id, status, payment_status, created_at, is_deleted)
                values (?, ?, ?, 'COMPLETED', 'PENDING_COLLECTION', now(), false)
                """, assignmentId, jobId, userId);
        jdbcTemplate.update("""
                insert into payments
                    (id, job_assignment_id, conversation_id, status, currency, created_at, is_deleted)
                values (?, ?, ?, 'INITIATED', 'TRY', now(), false)
                """, paymentId, assignmentId, "pending-" + paymentId);
        jdbcTemplate.update("""
                insert into account_deletion_requests
                    (id, user_id, subject_id, status, reason, requested_at, created_at, is_deleted)
                values (?, ?, ?, 'REQUESTED', null, now() - interval '31 days', now(), false)
                """, requestId, userId, userId);

        assertFalse(legalService.processDeletionRequest(requestId));

        var waitingRequest = legalService.getCurrentDeletionRequest(userId);
        assertEquals("REQUESTED", waitingRequest.getStatus().name());
        assertEquals(LegalMessages.DELETION_BLOCKED_BY_PENDING_PAYMENT, waitingRequest.getBlockedReason());
        assertFalse(Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select is_deleted from users where id = ?", Boolean.class, userId)));
        assertEquals(0, jdbcTemplate.queryForObject(
                "select count(*) from account_anonymization_audit where request_id = ?", Integer.class, requestId));
    }
}
