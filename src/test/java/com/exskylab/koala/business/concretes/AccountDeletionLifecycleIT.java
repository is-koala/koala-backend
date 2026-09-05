package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.LegalService;
import com.exskylab.koala.core.constants.LegalMessages;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
        UUID retainedJobAddressId = UUID.randomUUID();
        UUID mediaId = UUID.randomUUID();
        UUID sharedMediaId = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID retainedIndividualJobId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID reviewId = UUID.randomUUID();
        UUID blockId = UUID.randomUUID();
        UUID cancellationId = UUID.randomUUID();

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
                insert into addresses
                    (id, country_id, city_id, district_id, neighborhood, thoroughfare,
                     building_number, unit_number, postal_code, additional_directions,
                     latitude, longitude, canonical_version, needs_review, created_at, is_deleted)
                values (?, ?, ?, ?, 'Ev Mahallesi', 'Gizli Sokak', '99', '8', '34000',
                        'Mavi kapı', 41.123456, 29.654321, 1, false, now(), false)
                """, retainedJobAddressId, countryId, cityId, districtId);
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
                insert into media
                    (id, media_type, file_name, file_type, file_url, file_size,
                     created_by_id, created_at, is_deleted)
                values (?, 'IMAGE', 'ilan.jpg', 'image/jpeg', 'jobs/shared-job.jpg', 30,
                        ?, now(), false)
                """, sharedMediaId, userId);
        jdbcTemplate.update("""
                insert into consent_records
                    (id, user_id, agreement_version_id, granted, recorded_at, ip_address,
                     user_agent, created_at, is_deleted)
                values (?, ?, ?, true, now(), '198.51.100.4', 'test-agent', now(), false)
                """, UUID.randomUUID(), userId, agreementId);
        jdbcTemplate.update("""
                insert into notification_preferences
                    (id, user_id, category, channel, enabled, created_at, is_deleted)
                values (?, ?, 'NEWSLETTER', 'EMAIL', true, now(), false)
                """, UUID.randomUUID(), userId);
        jdbcTemplate.update("""
                insert into commercial_message_consents
                    (id, user_id, category, channel, granted, recorded_at, ip_address,
                     user_agent, source, created_at, is_deleted)
                values (?, ?, 'NEWSLETTER', 'EMAIL', true, now(), '198.51.100.5',
                        'commercial-test-agent', 'PREFERENCE_CENTRE', now(), false)
                """, UUID.randomUUID(), userId);
        jdbcTemplate.update("""
                insert into unsubscribe_tokens
                    (id, user_id, category, channel, token_digest, expires_at, created_at, is_deleted)
                values (?, ?, 'NEWSLETTER', 'EMAIL', ?, now() + interval '1 day', now(), false)
                """, UUID.randomUUID(), userId, "b".repeat(64));
        jdbcTemplate.update("""
                insert into bank_accounts
                    (id, user_id, iban, account_holder_name, is_primary, is_active, created_at, is_deleted)
                values (?, ?, 'TR000000000000000000000001', 'Ayşe Deneme', true, true, now(), false)
                """, UUID.randomUUID(), userId);
        jdbcTemplate.update("insert into jobs (id, creator_id, title, created_at, is_deleted) values (?, ?, 'Deneme işi', now(), false)",
                jobId, employerId);
        jdbcTemplate.update("""
                insert into jobs
                    (id, creator_id, employer_type, title, address_id, address_public,
                     status, created_at, is_deleted)
                values (?, ?, 'INDIVIDUAL', 'Geçmiş bireysel iş', ?, true,
                        'COMPLETED', now(), false)
                """, retainedIndividualJobId, userId, retainedJobAddressId);
        jdbcTemplate.update("""
                insert into job_images
                    (id, job_id, image_id, sort_order, is_cover, created_at, is_deleted)
                values (?, ?, ?, 0, true, now(), false)
                """, UUID.randomUUID(), retainedIndividualJobId, sharedMediaId);
        jdbcTemplate.update("""
                insert into job_assignments
                    (id, job_id, worker_id, status, payment_status, created_at, is_deleted)
                values (?, ?, ?, 'COMPLETED', 'RELEASED_TO_WORKER', now(), false)
                """, assignmentId, jobId, userId);
        jdbcTemplate.update("""
                insert into reviews
                    (id, job_assignment_id, author_id, subject_id, rating, comment,
                     anonymous, created_at, is_deleted)
                values (?, ?, ?, ?, 5, 'Ayşe 05555555555 numarasından aranabilir',
                        false, now(), false)
                """, reviewId, assignmentId, employerId, userId);
        jdbcTemplate.update("""
                insert into user_blocks
                    (id, blocker_id, blocked_id, reason, created_at, is_deleted)
                values (?, ?, ?, 'Ayşe ev adresini paylaştı', now(), false)
                """, blockId, employerId, userId);
        jdbcTemplate.update("""
                insert into cancellations
                    (id, job_assignment_id, cancelled_by_id, cancelled_by_party, reason,
                     notice_minutes, fee_rate, fee_amount, refund_amount, cancelled_at,
                     created_at, is_deleted)
                values (?, ?, ?, 'EMPLOYER', 'Ayşe özel telefonunu paylaşmadı',
                        0, 0, 0, 0, now(), now(), false)
                """, cancellationId, assignmentId, employerId);
        jdbcTemplate.update("""
                insert into payments
                    (id, job_assignment_id, conversation_id, status, currency, created_at, is_deleted)
                values (?, ?, ?, 'SUCCEEDED', 'TRY', now(), false)
                """, paymentId, assignmentId, "payment-" + paymentId);
        jdbcTemplate.update("""
                insert into conversations
                    (id, job_id, employer_id, worker_id, last_message_body, created_at, is_deleted)
                values (?, ?, ?, ?, 'Telefonum 05555555555', now(), false)
                """, conversationId, jobId, employerId, userId);
        jdbcTemplate.update("""
                insert into messages
                    (id, conversation_id, sender_id, body, sent_at, created_at, is_deleted)
                values (?, ?, ?, 'Telefonum 05555555555', now(), now(), false)
                """, UUID.randomUUID(), conversationId, userId);
        jdbcTemplate.update("""
                insert into password_reset_tokens
                    (id, user_id, token_digest, expires_at, created_at, is_deleted)
                values (?, ?, ?, now() + interval '1 day', now(), false)
                """, UUID.randomUUID(), userId, "a".repeat(64));
        jdbcTemplate.update("""
                insert into talent_pool_entries
                    (id, employer_id, worker_id, note, created_at, is_deleted)
                values (?, ?, ?, 'Telefonla 05555555555 üzerinden ara', now(), false)
                """, UUID.randomUUID(), employerId, userId);
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
        assertEquals("jobs/shared-job.jpg", jdbcTemplate.queryForObject(
                "select file_url from media where id = ?", String.class, sharedMediaId));
        assertNull(jdbcTemplate.queryForObject(
                "select thoroughfare from addresses where id = ?", String.class, retainedJobAddressId));
        assertNull(jdbcTemplate.queryForObject(
                "select building_number from addresses where id = ?", String.class, retainedJobAddressId));
        assertNull(jdbcTemplate.queryForObject(
                "select latitude from addresses where id = ?", java.math.BigDecimal.class, retainedJobAddressId));
        assertNull(jdbcTemplate.queryForObject(
                "select neighborhood from addresses where id = ?", String.class, retainedJobAddressId));
        assertFalse(Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select address_public from jobs where id = ?", Boolean.class, retainedIndividualJobId)));
        assertNull(jdbcTemplate.queryForObject(
                "select last_message_body from conversations where id = ?", String.class, conversationId));
        assertTrue(jdbcTemplate.queryForObject(
                "select revoked_at is not null from password_reset_tokens where user_id = ?",
                Boolean.class, userId));
        assertNull(jdbcTemplate.queryForObject(
                "select note from talent_pool_entries where worker_id = ?", String.class, userId));
        assertNull(jdbcTemplate.queryForObject(
                "select comment from reviews where id = ?", String.class, reviewId));
        assertTrue(Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select anonymous from reviews where id = ?", Boolean.class, reviewId)));
        assertNull(jdbcTemplate.queryForObject(
                "select reason from user_blocks where id = ?", String.class, blockId));
        assertNull(jdbcTemplate.queryForObject(
                "select reason from cancellations where id = ?", String.class, cancellationId));
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
        assertFalse(Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select enabled from notification_preferences where user_id = ?", Boolean.class, userId)));
        assertTrue(Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select is_deleted from notification_preferences where user_id = ?", Boolean.class, userId)));
        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from commercial_message_consents where user_id = ?", Integer.class, userId));
        assertNull(jdbcTemplate.queryForObject(
                "select ip_address from commercial_message_consents where user_id = ?", String.class, userId));
        assertNull(jdbcTemplate.queryForObject(
                "select user_agent from commercial_message_consents where user_id = ?", String.class, userId));
        assertTrue(Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select is_deleted from unsubscribe_tokens where user_id = ?", Boolean.class, userId)));
        assertTrue(Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select deleted_at is not null from unsubscribe_tokens where user_id = ?", Boolean.class, userId)));
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

    @Test
    void aDeferredBlockedRequestCannotStarveANeverAttemptedRequest() {
        UUID blockedUserId = UUID.randomUUID();
        UUID readyUserId = UUID.randomUUID();
        UUID blockedRequestId = UUID.randomUUID();
        UUID readyRequestId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into users (id, first_name, username, email, is_employer, created_at, is_deleted)
                values (?, 'Blokeli', ?, ?, false, now(), false),
                       (?, 'Hazır', ?, ?, false, now(), false)
                """, blockedUserId, "blocked-" + blockedUserId,
                "blocked-" + blockedUserId + "@example.test",
                readyUserId, "ready-" + readyUserId, "ready-" + readyUserId + "@example.test");
        jdbcTemplate.update("""
                insert into account_deletion_requests
                    (id, user_id, subject_id, status, requested_at, next_attempt_at, created_at, is_deleted)
                values (?, ?, ?, 'REQUESTED', now() - interval '60 days', now() + interval '1 day', now(), false),
                       (?, ?, ?, 'REQUESTED', now() - interval '31 days', null, now(), false)
                """, blockedRequestId, blockedUserId, blockedUserId,
                readyRequestId, readyUserId, readyUserId);

        var due = legalService.findDueDeletionRequestIds(
                Instant.now().minus(java.time.Duration.ofDays(30)), Instant.now(), PageRequest.of(0, 1));

        assertEquals(java.util.List.of(readyRequestId), due);
    }
}
