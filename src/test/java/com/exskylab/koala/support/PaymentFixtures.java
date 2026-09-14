package com.exskylab.koala.support;

import com.exskylab.koala.core.helpers.CommissionCalculator;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;

public final class PaymentFixtures {

    public static final UUID SEEDED_WORKER = UUID.fromString("00000000-0000-0000-0000-000000000001");

    public static final String PRICING_VERSION = "2026-09-v1";

    private final JdbcTemplate jdbcTemplate;

    public PaymentFixtures(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public static CommissionCalculator.Breakdown breakdown() {
        return new CommissionCalculator.Breakdown(
                new BigDecimal("1000.00"), new BigDecimal("75.00"),
                new BigDecimal("1075.00"), new BigDecimal("0.0750"));
    }

    public void seedAssignment(UUID jobId, UUID assignmentId) {
        seedAssignment(jobId, assignmentId, SEEDED_WORKER, "CONFIRMED", "PENDING_COLLECTION");
    }

    public void seedAssignment(UUID jobId, UUID assignmentId, UUID workerId, String status, String paymentStatus) {
        jdbcTemplate.update("""
                insert into jobs (id, created_at, is_deleted, title, status, employer_type,
                                  experience_required, insurance_required, min_age, max_age,
                                  start_date_time, end_date_time)
                values (?, now(), false, 'Payment fixture', 'OPEN', 'INDIVIDUAL',
                        false, false, 18, 65, now() + interval '1 day', now() + interval '2 days')
                """, jobId);
        jdbcTemplate.update("""
                insert into job_assignments
                    (id, created_at, is_deleted, job_id, worker_id, status, payment_status,
                     pricing_version, currency,
                     worker_payout, commission_rate, commission_amount, total_charged, work_hours)
                values (?, now(), false, ?, ?, ?::varchar, ?::varchar, ?, 'TRY',
                        1000.00, 0.0750, 75.00, 1075.00, 0)
                """, assignmentId, jobId, workerId, status, paymentStatus, PRICING_VERSION);
    }

    public void cleanUp(UUID jobId, UUID assignmentId) {
        jdbcTemplate.update("delete from payouts where job_assignment_id = ?", assignmentId);
        jdbcTemplate.update("delete from payments where job_assignment_id = ?", assignmentId);
        jdbcTemplate.update("delete from job_assignments where id = ?", assignmentId);
        jdbcTemplate.update("delete from jobs where id = ?", jobId);
    }
}
