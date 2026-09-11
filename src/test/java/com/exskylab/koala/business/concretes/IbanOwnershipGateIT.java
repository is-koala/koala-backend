package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.ActivityNotificationService;
import com.exskylab.koala.business.abstracts.BankAccountService;
import com.exskylab.koala.business.abstracts.PayoutService;
import com.exskylab.koala.business.abstracts.SettlementService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.entities.Payout;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IbanOwnershipGateIT extends AbstractIntegrationTest {

    private static final String CONVERSATION_ID = "iban-ownership-gate-it";

    private final UUID workerId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();
    private final UUID paymentId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();

    @Autowired
    private PayoutService payoutService;

    @Autowired
    private BankAccountService bankAccountService;

    @Autowired
    private UserService userService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private SettlementService settlementService;

    @MockitoBean
    private ActivityNotificationService activityNotificationService;

    @BeforeEach
    void escrowIsFundedAndWorkIsDone() {
        org.mockito.Mockito.when(settlementService.enqueue(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(java.util.Optional.of(new com.exskylab.koala.entities.SettlementTask()));

        jdbcTemplate.update("""
                insert into users (id, created_at, is_deleted, email, password, is_employer,
                                   email_verified, phone_verified, identity_verified)
                values (?, now(), false, ?, 'test', false, true, false, true)
                """, workerId, workerId + "@example.com");

        jdbcTemplate.update("""
                insert into jobs (id, created_at, is_deleted, creator_id, title, pricing_version, currency,
                                  worker_payout, commission_rate, commission_amount, total_charged,
                                  insurance_required, address_public, experience_required, min_age, max_age)
                values (?, now(), false, ?, 'IBAN kapisi', '2026-09-v1', 'TRY', 1000.00, 0.0750, 75.00, 1075.00,
                        false, false, false, 18, 65)
                """, jobId, workerId);

        jdbcTemplate.update("""
                insert into job_assignments (id, created_at, is_deleted, job_id, worker_id, status, payment_status,
                                             work_hours, pricing_version, currency, worker_payout, commission_rate,
                                             commission_amount, total_charged)
                values (?, now(), false, ?, ?, 'COMPLETED', 'HELD_IN_ESCROW', 8, '2026-09-v1', 'TRY',
                        1000.00, 0.0750, 75.00, 1075.00)
                """, assignmentId, jobId, workerId);

        jdbcTemplate.update("""
                insert into payments (id, created_at, is_deleted, job_assignment_id, conversation_id, status,
                                      currency, pricing_version, worker_payout, commission_rate,
                                      commission_amount, total_charged, provider_transaction_id)
                values (?, now(), false, ?, ?, 'SUCCEEDED', 'TRY', '2026-09-v1', 1000.00, 0.0750, 75.00,
                        1075.00, 'txn-iban-gate')
                """, paymentId, assignmentId, CONVERSATION_ID);

        jdbcTemplate.update("""
                insert into bank_accounts (id, created_at, is_deleted, user_id, iban, is_primary, is_active,
                                           verification_status, verification_attempts,
                                           account_holder_name_verified)
                values (?, now(), false, ?, 'TR180006200119000006672315', true, true, 'UNVERIFIED', 0, false)
                """, accountId, workerId);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from payouts where job_assignment_id = ?", assignmentId);
        jdbcTemplate.update("delete from payments where conversation_id = ?", CONVERSATION_ID);
        jdbcTemplate.update("delete from job_assignments where id = ?", assignmentId);
        jdbcTemplate.update("delete from jobs where id = ?", jobId);
        jdbcTemplate.update("delete from bank_accounts where user_id = ?", workerId);
        jdbcTemplate.update("delete from authorities where user_id = ?", workerId);
        jdbcTemplate.update("delete from users where id = ?", workerId);
    }

    private String paymentStatusOfTheAssignment() {
        return jdbcTemplate.queryForObject(
                "select payment_status from job_assignments where id = ?", String.class, assignmentId);
    }

    @Test
    void escrowIsReleasedToAnUnverifiedPrimaryIban() {
        Payout payout = payoutService.releaseToWorker(assignmentId);

        assertNotNull(payout.getId());
        assertEquals("RELEASED_TO_WORKER", paymentStatusOfTheAssignment());
    }

    @Test
    void escrowIsReleasedOnceTheIbanOwnershipIsProven() {
        jdbcTemplate.update("""
                update bank_accounts set verification_status = 'VERIFIED', verified_at = now() where id = ?
                """, accountId);

        Payout payout = payoutService.releaseToWorker(assignmentId);

        assertNotNull(payout.getId());
        assertEquals("RELEASED_TO_WORKER", paymentStatusOfTheAssignment());
    }

    @Test
    void replacingTheIbanDoesNotBlockRelease() {
        jdbcTemplate.update("""
                update bank_accounts set verification_status = 'VERIFIED', verified_at = now(),
                                         verification_reference = 'KOALA-ABCDEFGH',
                                         verification_amount = 1.42,
                                         account_holder_name = 'Ada Yilmaz', account_holder_name_verified = true
                where id = ?
                """, accountId);

        bankAccountService.savePrimaryAccount(userService.getById(workerId), "TR960001000200300040005006");

        assertEquals("UNVERIFIED", jdbcTemplate.queryForObject(
                "select verification_status from bank_accounts where id = ?", String.class, accountId));
        assertNull(jdbcTemplate.queryForObject(
                "select verified_at from bank_accounts where id = ?", java.sql.Timestamp.class, accountId));
        assertNull(jdbcTemplate.queryForObject(
                "select account_holder_name from bank_accounts where id = ?", String.class, accountId));
        assertNull(jdbcTemplate.queryForObject(
                "select verification_reference from bank_accounts where id = ?", String.class, accountId));

        Payout payout = payoutService.releaseToWorker(assignmentId);

        assertNotNull(payout.getId());
        assertEquals("RELEASED_TO_WORKER", paymentStatusOfTheAssignment());
    }

    @Test
    void escrowIsNotReleasedWhenTheWorkerHasNoPrimaryIban() {
        jdbcTemplate.update("delete from bank_accounts where user_id = ?", workerId);

        assertThrows(ConflictException.class, () -> payoutService.releaseToWorker(assignmentId));

        assertEquals(0, (int) jdbcTemplate.queryForObject(
                "select count(*) from payouts where job_assignment_id = ?", Integer.class, assignmentId));
        assertEquals("HELD_IN_ESCROW", paymentStatusOfTheAssignment());
    }
}
