package com.exskylab.koala.dataAccess;

import com.exskylab.koala.business.abstracts.PaymentService;
import com.exskylab.koala.core.constants.PaymentMessages;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.PaymentAttemptStatus;
import com.exskylab.koala.support.AbstractIntegrationTest;
import com.exskylab.koala.support.PaymentFixtures;
import com.exskylab.koala.support.RunbookSql;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StuckThreeDSecureAttemptRunbookIT extends AbstractIntegrationTest {

    private static final String PLACEHOLDER = "'<payment_id>'";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PaymentService paymentService;

    private PaymentFixtures fixtures;

    @BeforeEach
    void fixtures() {
        fixtures = new PaymentFixtures(jdbcTemplate);
    }

    @Test
    void theRunbookUpdateClosesTheStuckAttemptAndReopensCheckoutWithoutTouchingTheAssignment() {
        UUID jobId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        fixtures.seedAssignment(jobId, assignmentId);
        seedStuckThreeDSecureAttempt(paymentId, assignmentId);

        try {
            ConflictException blocked = assertThrows(ConflictException.class,
                    () -> paymentService.startAttempt(assignment(assignmentId), PaymentFixtures.breakdown()));
            assertEquals(PaymentMessages.ATTEMPT_IN_PROGRESS, blocked.getMessage());

            assertEquals(1, jdbcTemplate.update(closingUpdateFromTheRunbook(), paymentId),
                    "Runbook update'i tam bir satir kapatmali.");

            Map<String, Object> checked = jdbcTemplate.queryForMap(checkQueryFromTheRunbook(), paymentId);
            assertEquals("FAILED", checked.get("status"));
            assertNotNull(checked.get("completed_at"));
            assertEquals("PENDING_COLLECTION", checked.get("payment_status"),
                    "VERIFY_PENDING atamanin payment_status alanina dokunmaz; kapatma da dokunmamali.");
            assertEquals("{payment.3ds.verification.failed}", jdbcTemplate.queryForObject(
                    "select failure_reason from payments where id = ?", String.class, paymentId));
            assertNull(jdbcTemplate.queryForObject(
                    "select next_verification_at from payments where id = ?", Instant.class, paymentId));

            assertEquals(0, jdbcTemplate.update(closingUpdateFromTheRunbook(), paymentId),
                    "Kapatilmis satirda ikinci kosum kor tekrar olmamali.");

            var retry = paymentService.startAttempt(assignment(assignmentId), PaymentFixtures.breakdown());
            assertEquals(PaymentAttemptStatus.INITIATED, retry.getStatus());
            assertEquals(2, jdbcTemplate.queryForObject(
                    "select count(*) from payments where job_assignment_id = ?", Integer.class, assignmentId));
        } finally {
            fixtures.cleanUp(jobId, assignmentId);
        }
    }

    @Test
    void theRunbookUpdateNeverTouchesAnAttemptThatIsNotAThreeDSecureHold() {
        UUID jobId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        UUID succeeded = UUID.randomUUID();
        UUID uncertain = UUID.randomUUID();
        fixtures.seedAssignment(jobId, assignmentId);
        jdbcTemplate.update("""
                insert into payments (id, created_at, is_deleted, job_assignment_id, conversation_id,
                                      provider_token, status, currency, verification_attempts, completed_at)
                values (?, now(), false, ?, ?, ?, 'SUCCEEDED', 'TRY', 1, now())
                """, succeeded, assignmentId, UUID.randomUUID().toString(), "tok-" + succeeded);
        jdbcTemplate.update("""
                insert into payments (id, created_at, is_deleted, job_assignment_id, conversation_id,
                                      provider_token, status, failure_reason, currency, verification_attempts)
                values (?, now(), false, ?, ?, ?, 'VERIFY_PENDING', '{payment.verification.pending}', 'TRY', 8)
                """, uncertain, assignmentId, UUID.randomUUID().toString(), "tok-" + uncertain);

        try {
            assertEquals(0, jdbcTemplate.update(closingUpdateFromTheRunbook(), succeeded),
                    "Basarili odeme runbook update'iyle FAILED yapilamamali.");
            assertEquals(0, jdbcTemplate.update(closingUpdateFromTheRunbook(), uncertain),
                    "Sonucu bilinmeyen deneme 3DS adimiyla kapatilamamali; onun kendi adimi var.");
            assertEquals("SUCCEEDED", jdbcTemplate.queryForObject(
                    "select status from payments where id = ?", String.class, succeeded));
            assertEquals("VERIFY_PENDING", jdbcTemplate.queryForObject(
                    "select status from payments where id = ?", String.class, uncertain));
        } finally {
            fixtures.cleanUp(jobId, assignmentId);
        }
    }

    private static String closingUpdateFromTheRunbook() {
        String block = RunbookSql.theOnlyBlock(RunbookSql.PAYMENTS, candidate -> candidate.startsWith("update payments")
                && candidate.contains("'{payment.3ds.verification.failed}'")
                && candidate.contains("status = 'VERIFY_PENDING'"), "sikisan 3DS denemesini kapatan update'i");
        assertTrue(block.contains("set status = 'FAILED'"), block);
        assertTrue(block.contains("completed_at = now()"), block);
        return withPaymentParameter(block);
    }

    private static String checkQueryFromTheRunbook() {
        String block = RunbookSql.theOnlyBlock(RunbookSql.PAYMENTS, candidate -> candidate.startsWith("select p.status")
                && candidate.contains("join job_assignments a"), "kapatma sonrasi atama kontrolunu");
        return withPaymentParameter(block);
    }

    private static String withPaymentParameter(String block) {
        assertTrue(block.contains(PLACEHOLDER), "Runbook SQL'i " + PLACEHOLDER + " yer tutucusunu tasimali");
        return block.replace(PLACEHOLDER, "?").replace(";", "");
    }

    private static JobAssignment assignment(UUID assignmentId) {
        JobAssignment assignment = new JobAssignment();
        assignment.setId(assignmentId);
        assignment.setPricingVersion(PaymentFixtures.PRICING_VERSION);
        assignment.setCurrency("TRY");
        return assignment;
    }

    private void seedStuckThreeDSecureAttempt(UUID paymentId, UUID assignmentId) {
        jdbcTemplate.update("""
                insert into payments (id, created_at, is_deleted, job_assignment_id, conversation_id,
                                      provider_token, provider_submerchant_key, status, failure_reason,
                                      currency, pricing_version, worker_payout, commission_rate,
                                      commission_amount, total_charged, verification_attempts,
                                      next_verification_at, completed_at)
                values (?, now() - interval '1 day', false, ?, ?, ?, 'submerchant-key', 'VERIFY_PENDING',
                        '{payment.3ds.verification.failed}', 'TRY', '2026-09-v1',
                        1000.00, 0.0750, 75.00, 1075.00, 8, null, null)
                """, paymentId, assignmentId, UUID.randomUUID().toString(), "tok-" + paymentId);
    }
}
