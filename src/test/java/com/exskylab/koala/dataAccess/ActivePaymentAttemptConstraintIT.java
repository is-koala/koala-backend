package com.exskylab.koala.dataAccess;

import com.exskylab.koala.support.AbstractIntegrationTest;
import com.exskylab.koala.business.abstracts.PaymentService;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.helpers.CommissionCalculator;
import com.exskylab.koala.entities.JobAssignment;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ActivePaymentAttemptConstraintIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PaymentService paymentService;

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
        jdbcTemplate.update("insert into jobs (id, created_at, is_deleted) values (?, now(), false)", jobId);
        jdbcTemplate.update("""
                insert into job_assignments
                    (id, created_at, is_deleted, job_id, worker_id, pricing_version, currency,
                     worker_payout, commission_rate, commission_amount, total_charged)
                values (?, now(), false, ?, '00000000-0000-0000-0000-000000000001',
                        '2026-09-v1', 'TRY', 1000.00, 0.0750, 75.00, 1075.00)
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
}
