package com.exskylab.koala.dataAccess;

import com.exskylab.koala.support.AbstractIntegrationTest;
import com.exskylab.koala.business.abstracts.PaymentService;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.helpers.CommissionCalculator;
import com.exskylab.koala.entities.JobAssignment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class ActivePaymentAttemptConstraintIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private JobAssignmentDao jobAssignmentDao;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void databaseAllowsOnlyOneActiveAttemptPerAssignment() {
        String definition = jdbcTemplate.queryForObject(
                "select indexdef from pg_indexes where indexname = 'uk_payments_one_active_attempt'",
                String.class);

        assertTrue(definition.contains("UNIQUE INDEX"));
        assertTrue(definition.contains("job_assignment_id"));
        assertTrue(definition.contains("INITIATED"));
        assertTrue(definition.contains("VERIFY_PENDING"));
        assertTrue(definition.contains("IN_REVIEW"));

        String tokenDefinition = jdbcTemplate.queryForObject(
                "select indexdef from pg_indexes where indexname = 'uk_payments_provider_token'",
                String.class);
        assertTrue(tokenDefinition.contains("UNIQUE INDEX"));
        assertTrue(tokenDefinition.contains("provider_token"));

        assertEquals("YES", jdbcTemplate.queryForObject("""
                select is_nullable from information_schema.columns
                where table_schema = 'public' and table_name = 'payments'
                  and column_name = 'provider_submerchant_key'
                """, String.class));
    }

    @Test
    void twoConcurrentReservationsProduceOneAttemptAndOneConflict() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into jobs (id, created_at, is_deleted, title, status, employer_type,
                                  experience_required, insurance_required, min_age, max_age,
                                  start_date_time, end_date_time)
                values (?, now(), false, 'Payment lock', 'OPEN', 'INDIVIDUAL',
                        false, false, 18, 65, now() + interval '1 day', now() + interval '2 days')
                """, jobId);
        jdbcTemplate.update("""
                insert into job_assignments
                    (id, created_at, is_deleted, job_id, worker_id, status, payment_status,
                     pricing_version, currency,
                     worker_payout, commission_rate, commission_amount, total_charged, work_hours)
                values (?, now(), false, ?, '00000000-0000-0000-0000-000000000001',
                        'CONFIRMED', 'PENDING_COLLECTION', '2026-09-v1', 'TRY',
                        1000.00, 0.0750, 75.00, 1075.00, 0)
                """, assignmentId, jobId);
        jdbcTemplate.execute("""
                create or replace function delay_payment_attempt_insert() returns trigger as $$
                begin
                    perform pg_sleep(0.5);
                    return new;
                end;
                $$ language plpgsql
                """);
        jdbcTemplate.execute("""
                create trigger delay_payment_attempt_insert
                before insert on payments for each row execute function delay_payment_attempt_insert()
                """);

        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            CommissionCalculator.Breakdown breakdown = new CommissionCalculator.Breakdown(
                    new BigDecimal("1000.00"), new BigDecimal("75.00"),
                    new BigDecimal("1075.00"), new BigDecimal("0.0750"));
            java.util.concurrent.Callable<String> request = () -> {
                start.await();
                JobAssignment assignment = new JobAssignment();
                assignment.setId(assignmentId);
                assignment.setPricingVersion("2026-09-v1");
                assignment.setCurrency("TRY");
                try {
                    paymentService.startAttempt(assignment, breakdown);
                    return "SUCCESS";
                } catch (ConflictException conflict) {
                    assertEquals("PAYMENT_ATTEMPT_IN_PROGRESS", conflict.getErrorCode());
                    return "CONFLICT";
                }
            };

            var first = executor.submit(request);
            var second = executor.submit(request);
            start.countDown();

            assertEquals(List.of("CONFLICT", "SUCCESS"),
                    java.util.stream.Stream.of(first.get(10, TimeUnit.SECONDS),
                                    second.get(10, TimeUnit.SECONDS)).sorted().toList());
            assertEquals(1, jdbcTemplate.queryForObject("""
                    select count(*) from payments
                    where job_assignment_id = ? and status in ('INITIATED', 'VERIFY_PENDING', 'IN_REVIEW')
                    """, Integer.class, assignmentId));
        } finally {
            jdbcTemplate.execute("drop trigger if exists delay_payment_attempt_insert on payments");
            jdbcTemplate.execute("drop function if exists delay_payment_attempt_insert()");
            jdbcTemplate.update("delete from payments where job_assignment_id = ?", assignmentId);
            jdbcTemplate.update("delete from job_assignments where id = ?", assignmentId);
            jdbcTemplate.update("delete from jobs where id = ?", jobId);
        }
    }

    @Test
    void reservationWaitsForTheFinancialLockAndRechecksTheCommittedAssignmentState() throws Exception {
        UUID jobId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into jobs (id, created_at, is_deleted, title, status, employer_type,
                                  experience_required, insurance_required, min_age, max_age,
                                  start_date_time, end_date_time)
                values (?, now(), false, 'Payment lock', 'OPEN', 'INDIVIDUAL',
                        false, false, 18, 65, now() + interval '1 day', now() + interval '2 days')
                """, jobId);
        jdbcTemplate.update("""
                insert into job_assignments
                    (id, created_at, is_deleted, job_id, worker_id, status, payment_status,
                     pricing_version, currency, worker_payout, commission_rate,
                     commission_amount, total_charged, work_hours)
                values (?, now(), false, ?, '00000000-0000-0000-0000-000000000001',
                        'CONFIRMED', 'PENDING_COLLECTION', '2026-09-v1', 'TRY',
                        1000.00, 0.0750, 75.00, 1075.00, 0)
                """, assignmentId, jobId);

        CountDownLatch firstHasLock = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        CommissionCalculator.Breakdown breakdown = new CommissionCalculator.Breakdown(
                new BigDecimal("1000.00"), new BigDecimal("75.00"),
                new BigDecimal("1075.00"), new BigDecimal("0.0750"));

        try (var executor = Executors.newFixedThreadPool(2)) {
            var financialDecision = executor.submit(() -> transactions.executeWithoutResult(status -> {
                JobAssignment assignment = jobAssignmentDao.findByIdForUpdate(assignmentId).orElseThrow();
                assignment.setPaymentStatus(com.exskylab.koala.entities.PaymentStatus.HELD_IN_ESCROW);
                jobAssignmentDao.saveAndFlush(assignment);
                firstHasLock.countDown();
                await(releaseFirst);
            }));
            assertTrue(firstHasLock.await(10, TimeUnit.SECONDS));

            var reservation = executor.submit(() -> {
                JobAssignment stale = new JobAssignment();
                stale.setId(assignmentId);
                stale.setStatus(com.exskylab.koala.entities.AssignmentStatus.CONFIRMED);
                stale.setPaymentStatus(com.exskylab.koala.entities.PaymentStatus.PENDING_COLLECTION);
                try {
                    paymentService.startAttempt(stale, breakdown);
                    return "SUCCESS";
                } catch (ConflictException conflict) {
                    return conflict.getErrorCode();
                }
            });

            assertThrows(TimeoutException.class, () -> reservation.get(250, TimeUnit.MILLISECONDS));
            releaseFirst.countDown();
            financialDecision.get(10, TimeUnit.SECONDS);
            assertEquals("JOB_ASSIGNMENT_NOT_READY_FOR_PAYMENT", reservation.get(10, TimeUnit.SECONDS));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "select count(*) from payments where job_assignment_id = ?", Integer.class, assignmentId));
        } finally {
            releaseFirst.countDown();
            jdbcTemplate.update("delete from payments where job_assignment_id = ?", assignmentId);
            jdbcTemplate.update("delete from job_assignments where id = ?", assignmentId);
            jdbcTemplate.update("delete from jobs where id = ?", jobId);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("payment reservation lock test timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("payment reservation lock test interrupted", exception);
        }
    }
}
