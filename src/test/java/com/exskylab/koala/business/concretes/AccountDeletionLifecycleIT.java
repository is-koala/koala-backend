package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.LegalService;
import com.exskylab.koala.business.abstracts.IysService;
import com.exskylab.koala.core.constants.LegalMessages;
import com.exskylab.koala.core.dtos.legal.response.DeletionBlockerDto;
import com.exskylab.koala.entities.DeletionBlockerType;
import com.exskylab.koala.core.utilities.iys.IysConsentRequest;
import com.exskylab.koala.core.utilities.iys.IysPermissionResult;
import com.exskylab.koala.core.utilities.iys.IysProviderClient;
import com.exskylab.koala.entities.NotificationChannel;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Transactional
@TestPropertySource(properties = {
        "iys.enabled=true", "iys.api-key=test", "iys.api-hash=test", "iys.brand-code=test",
        "iys.recipient-encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
})
class AccountDeletionLifecycleIT extends AbstractIntegrationTest {

    @Autowired
    private LegalService legalService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired private IysService iysService;
    @Autowired private EntityManager entityManager;
    @MockitoBean private IysProviderClient iysProviderClient;

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
        UUID commercialConsentId = UUID.randomUUID();
        UUID iysTaskId = UUID.randomUUID();
        UUID iysStateId = UUID.randomUUID();

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
                insert into users
                    (id, first_name, username, email, email_verified, phone_verified,
                     identity_verified, is_employer, created_at, is_deleted)
                values (?, 'İşveren', ?, ?, false, false, false, true, now(), false)
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
                """, commercialConsentId, userId);
        jdbcTemplate.update("""
                insert into iys_consent_tasks
                    (id, user_id, consent_id, channel, recipient, granted, provider_source,
                     status, attempts, next_attempt_at, last_failure_code, created_at, is_deleted)
                values (?, ?, ?, 'EMAIL', ?, false, 'HS_WEB', 'PENDING', 1, now(),
                        'PROVIDER_UNAVAILABLE', now(), false)
                """, iysTaskId, userId, commercialConsentId, "worker-" + userId + "@example.test");
        jdbcTemplate.update("""
                insert into iys_permission_states
                    (id, user_id, channel, recipient, status, provider_recorded_at, verified_at,
                     provider_source, created_at, is_deleted)
                values (?, ?, 'EMAIL', ?, 'GRANTED', now(), now(), 'HS_WEB', now(), false)
                """, iysStateId, userId, "worker-" + userId + "@example.test");
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
        assertEquals("[anonymized]", jdbcTemplate.queryForObject(
                "select recipient from iys_consent_tasks where id = ?", String.class, iysTaskId));
        assertEquals("PENDING", jdbcTemplate.queryForObject(
                "select status from iys_consent_tasks where id = ?", String.class, iysTaskId));
        assertNull(jdbcTemplate.queryForObject(
                "select last_failure_code from iys_consent_tasks where id = ?", String.class, iysTaskId));
        String retainedRecipient = jdbcTemplate.queryForObject(
                "select retained_recipient_ciphertext from iys_consent_tasks where id = ?", String.class, iysTaskId);
        assertTrue(retainedRecipient != null && retainedRecipient.startsWith("v1:"));
        assertFalse(retainedRecipient.contains("worker-"));
        assertTrue(Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select recipient_retain_until > now() from iys_consent_tasks where id = ?",
                Boolean.class, iysTaskId)));
        String originalRecipient = "worker-" + userId + "@example.test";
        when(iysProviderClient.lookupConsent(originalRecipient, NotificationChannel.EMAIL))
                .thenReturn(new IysPermissionResult(originalRecipient, NotificationChannel.EMAIL,
                        false, Instant.now(), "HS_WEB"));
        entityManager.flush();
        entityManager.clear();
        assertTrue(iysService.runTask(iysTaskId));
        verify(iysProviderClient).registerConsent(any(IysConsentRequest.class));
        entityManager.flush();
        assertEquals("COMPLETED", jdbcTemplate.queryForObject(
                "select status from iys_consent_tasks where id = ?", String.class, iysTaskId));
        assertNull(jdbcTemplate.queryForObject(
                "select retained_recipient_ciphertext from iys_consent_tasks where id = ?", String.class, iysTaskId));
        assertEquals("[anonymized]", jdbcTemplate.queryForObject(
                "select recipient from iys_permission_states where id = ?", String.class, iysStateId));
        assertNull(jdbcTemplate.queryForObject(
                "select provider_source from iys_permission_states where id = ?", String.class, iysStateId));
        assertTrue(Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "select is_deleted from iys_permission_states where id = ?", Boolean.class, iysStateId)));
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

        assertEquals(java.util.List.of(new DeletionBlockerDto(DeletionBlockerType.PENDING_PAYMENT, paymentId)),
                legalService.findDeletionBlockers(java.util.List.of(waitingRequest)).get(requestId),
                "Personel engelleyen kaydi tipi ve id'siyle gormeli; tamamlanmis atama etkin is sayilmaz.");

        jdbcTemplate.update("update job_assignments set status = 'IN_PROGRESS' where id = ?", assignmentId);
        assertEquals(java.util.List.of(
                        new DeletionBlockerDto(DeletionBlockerType.PENDING_PAYMENT, paymentId),
                        new DeletionBlockerDto(DeletionBlockerType.ACTIVE_WORK, assignmentId)),
                legalService.findDeletionBlockers(java.util.List.of(waitingRequest)).get(requestId));

        var rejected = legalService.rejectDeletionRequest(null, requestId, "Yasal inceleme sürüyor.");
        assertEquals("Yasal inceleme sürüyor.", rejected.getRejectionReason());
        assertEquals(java.util.Map.of(), legalService.findDeletionBlockers(java.util.List.of(rejected)),
                "Reddedilmis talep icin engel hesaplanmaz.");
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
                insert into users
                    (id, first_name, username, email, email_verified, phone_verified,
                     identity_verified, is_employer, created_at, is_deleted)
                values (?, 'Blokeli', ?, ?, false, false, false, false, now(), false),
                       (?, 'Hazır', ?, ?, false, false, false, false, now(), false)
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

    @Test
    void anonymizationErasesTheAuditDetailAboutThePersonIncludingRowsWrittenAfterTheRequest() {
        UUID userId = UUID.randomUUID();
        UUID someoneElse = UUID.randomUUID();
        UUID requestId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into users
                    (id, first_name, username, email, email_verified, phone_verified,
                     identity_verified, is_employer, created_at, is_deleted)
                values (?, 'Silinecek', ?, ?, false, false, false, false, now(), false),
                       (?, 'Başkası', ?, ?, false, false, false, true, now(), false)
                """, userId, "erased-" + userId, "erased-" + userId + "@example.test",
                someoneElse, "other-" + someoneElse, "other-" + someoneElse + "@example.test");
        jdbcTemplate.update("""
                insert into account_deletion_requests
                    (id, user_id, subject_id, status, requested_at, created_at, is_deleted)
                values (?, ?, ?, 'REQUESTED', now() - interval '31 days', now(), false)
                """, requestId, userId, userId);
        UUID onTheirAccount = auditEntryAbout(userId.toString(), null, null);
        UUID onTheirPayout = auditEntryAbout(UUID.randomUUID().toString(), userId, someoneElse);
        UUID asCounterparty = auditEntryAbout(UUID.randomUUID().toString(), someoneElse, userId);
        UUID aboutSomeoneElse = auditEntryAbout(UUID.randomUUID().toString(), someoneElse, null);

        assertTrue(legalService.processDeletionRequest(requestId));

        assertFalse(hasAuditDetail(onTheirAccount),
                "Talepten sonra yazilan, kisinin kendi kaydi hakkindaki satirin detayi anonimlestirmede silinmeli.");
        assertFalse(hasAuditDetail(onTheirPayout), "Konusu kisi olan kararin detayi silinmeli.");
        assertFalse(hasAuditDetail(asCounterparty), "Karsi tarafi kisi olan kararin detayi silinmeli.");
        assertTrue(hasAuditDetail(aboutSomeoneElse), "Baskasi hakkindaki kararin detayi kalmali.");
        assertEquals(4, jdbcTemplate.queryForObject(
                "select count(*) from admin_audit_entries where id in (?, ?, ?, ?)", Integer.class,
                onTheirAccount, onTheirPayout, asCounterparty, aboutSomeoneElse),
                "KVKK silmesi denetim cekirdegini birakir.");
    }

    @Test
    void aBlockedThenRejectedErasureRequestErasesNoAuditDetail() {
        UUID userId = UUID.randomUUID();
        UUID employerId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into users
                    (id, first_name, username, email, email_verified, phone_verified,
                     identity_verified, is_employer, created_at, is_deleted)
                values (?, 'Çalışan', ?, ?, false, false, false, false, now(), false),
                       (?, 'İşveren', ?, ?, false, false, false, true, now(), false)
                """, userId, "kept-worker-" + userId, "kept-worker-" + userId + "@example.test",
                employerId, "kept-employer-" + employerId, "kept-employer-" + employerId + "@example.test");
        jdbcTemplate.update("insert into jobs (id, creator_id, title, created_at, is_deleted) values (?, ?, 'Açık ödeme', now(), false)",
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
                """, paymentId, assignmentId, "kept-" + paymentId);
        UUID beforeRequest = auditEntryAbout(userId.toString(), userId, null);

        var request = legalService.requestAccountDeletion(entityManager.find(User.class, userId), "kvkk");
        UUID afterRequest = auditEntryAbout(UUID.randomUUID().toString(), employerId, userId);

        assertEquals(LegalMessages.DELETION_BLOCKED_BY_PENDING_PAYMENT, request.getBlockedReason());
        assertTrue(hasAuditDetail(beforeRequest), "Talep ani hicbir sey silmez.");

        assertFalse(legalService.processDeletionRequest(request.getId()));
        assertTrue(hasAuditDetail(beforeRequest), "Acik yukumluluk yuzunden bekleyen talep hicbir sey silmez.");
        assertTrue(hasAuditDetail(afterRequest));

        legalService.rejectDeletionRequest(null, request.getId(), "Yasal inceleme sürüyor.");
        assertTrue(hasAuditDetail(beforeRequest), "Reddedilen talep hicbir sey silmez.");
        assertTrue(hasAuditDetail(afterRequest));
    }

    private UUID auditEntryAbout(String resourceId, UUID subjectId, UUID relatedSubjectId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into admin_audit_entries (id, occurred_at, actor_staff_id, actor_display_name,
                                                 permission, action, resource_type, resource_id,
                                                 allowed, mutating, sensitivity, retain_until,
                                                 subject_type, subject_id,
                                                 related_subject_type, related_subject_id)
                values (?, now(), ?, 'Silme Denemesi', 'payout.release', 'payout.release', 'job-assignments', ?,
                        true, true, 'FINANCIAL', now() + interval '10 years',
                        case when ?::uuid is null then null else 'USER' end, ?,
                        case when ?::uuid is null then null else 'USER' end, ?)
                """, id, UUID.randomUUID(), resourceId,
                subjectId, subjectId, relatedSubjectId, relatedSubjectId);
        jdbcTemplate.update("""
                insert into admin_audit_entry_details (audit_entry_id, request_body, ip, user_agent, purge_after)
                values (?, '{"note":"kisisel"}', '203.0.113.9', 'koala-admin/1.0', now() + interval '6 months')
                """, id);
        return id;
    }

    private boolean hasAuditDetail(UUID entryId) {
        return jdbcTemplate.queryForObject(
                "select count(*) from admin_audit_entry_details where audit_entry_id = ?",
                Integer.class, entryId) > 0;
    }
}
