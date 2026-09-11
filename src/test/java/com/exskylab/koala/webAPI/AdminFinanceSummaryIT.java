package com.exskylab.koala.webAPI;

import com.exskylab.koala.support.AbstractStaffIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminFinanceSummaryIT extends AbstractStaffIntegrationTest {

    private static final Instant IN_PERIOD = Instant.parse("2026-03-15T12:00:00Z");
    private static final Instant OUT_OF_PERIOD = Instant.parse("2026-01-01T12:00:00Z");
    private static final Instant FROM = Instant.parse("2026-03-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-04-01T00:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UUID workerId = UUID.randomUUID();
    private final UUID inPeriodJob = UUID.randomUUID();
    private final UUID outPeriodJob = UUID.randomUUID();
    private final UUID awaitingJob = UUID.randomUUID();
    private final UUID inPeriodAssignment = UUID.randomUUID();
    private final UUID outPeriodAssignment = UUID.randomUUID();
    private final UUID awaitingAssignment = UUID.randomUUID();

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from admin_audit_entries");
        jdbcTemplate.update("delete from staff_members where email like 'finance-summary-%@iskoala.com'");
        jdbcTemplate.update("delete from payouts where worker_id = ?", workerId);
        jdbcTemplate.update("delete from payments where job_assignment_id in (?, ?, ?)",
                inPeriodAssignment, outPeriodAssignment, awaitingAssignment);
        jdbcTemplate.update("delete from job_assignments where id in (?, ?, ?)",
                inPeriodAssignment, outPeriodAssignment, awaitingAssignment);
        jdbcTemplate.update("delete from jobs where id in (?, ?, ?)", inPeriodJob, outPeriodJob, awaitingJob);
        jdbcTemplate.update("delete from authorities where user_id = ?", workerId);
        jdbcTemplate.update("delete from users where id = ?", workerId);
    }

    private String tokenFor(String... roles) {
        return staffTokenFor("finance-summary-" + UUID.randomUUID() + "@iskoala.com", "Finans", roles);
    }

    @Test
    void aDisputeHandlerCannotReadTheFinanceSummary() throws Exception {
        mockMvc.perform(get("/admin/finance/summary")
                        .header("Authorization", "Bearer " + tokenFor("DISPUTE_HANDLER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aFinanceAnalystReadsAnEmptySummaryWhenNothingSettled() throws Exception {
        mockMvc.perform(get("/admin/finance/summary")
                        .header("Authorization", "Bearer " + tokenFor("FINANCE_ANALYST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currency").value("TRY"))
                .andExpect(jsonPath("$.data.employerVolume").value("0.00"))
                .andExpect(jsonPath("$.data.koalaFee").value("0.00"))
                .andExpect(jsonPath("$.data.workerPayout").value("0.00"))
                .andExpect(jsonPath("$.data.succeededPaymentCount").value(0))
                .andExpect(jsonPath("$.data.completedPayoutAmount").value("0.00"))
                .andExpect(jsonPath("$.data.completedPayoutCount").value(0))
                .andExpect(jsonPath("$.data.awaitingPayoutAmount").value("0.00"))
                .andExpect(jsonPath("$.data.awaitingPayoutCount").value(0));
    }

    @Test
    void theSummaryCountsOnlySucceededPaymentsAndPayoutsInsideThePeriod() throws Exception {
        seedLedger();

        mockMvc.perform(get("/admin/finance/summary")
                        .param("from", FROM.toString())
                        .param("to", TO.toString())
                        .header("Authorization", "Bearer " + tokenFor("FINANCE_ANALYST")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.from").value(FROM.toString()))
                .andExpect(jsonPath("$.data.to").value(TO.toString()))
                .andExpect(jsonPath("$.data.employerVolume").value("1075.00"))
                .andExpect(jsonPath("$.data.koalaFee").value("75.00"))
                .andExpect(jsonPath("$.data.workerPayout").value("1000.00"))
                .andExpect(jsonPath("$.data.succeededPaymentCount").value(1))
                .andExpect(jsonPath("$.data.completedPayoutAmount").value("1000.00"))
                .andExpect(jsonPath("$.data.completedPayoutCount").value(1))
                .andExpect(jsonPath("$.data.awaitingPayoutAmount").value("400.00"))
                .andExpect(jsonPath("$.data.awaitingPayoutCount").value(1));
    }

    @Test
    void aPartialPeriodIsRejected() throws Exception {
        mockMvc.perform(get("/admin/finance/summary")
                        .param("from", FROM.toString())
                        .header("Authorization", "Bearer " + tokenFor("FINANCE_ANALYST")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ADMIN_FINANCE_PERIOD_INVALID"));
    }

    @Test
    void aSuccessfulReadIsKeptAsATenYearFinancialAuditRow() throws Exception {
        mockMvc.perform(get("/admin/finance/summary")
                        .header("Authorization", "Bearer " + tokenFor("FINANCE_ANALYST")))
                .andExpect(status().isOk());

        Integer years = jdbcTemplate.queryForObject(
                "select extract(year from age(retain_until, occurred_at))::int from admin_audit_entries "
                        + "where action = 'finance.read' or permission = 'finance.read'",
                Integer.class);
        assertEquals(10, years);
    }

    private void seedLedger() {
        jdbcTemplate.update("""
                insert into users (id, created_at, is_deleted, email, password, is_employer,
                                   email_verified, phone_verified, identity_verified)
                values (?, now(), false, ?, 'test', false, true, false, true)
                """, workerId, workerId + "@example.com");
        seedAssignment(inPeriodJob, inPeriodAssignment, "in");
        seedAssignment(outPeriodJob, outPeriodAssignment, "out");
        seedAssignment(awaitingJob, awaitingAssignment, "wait");
        seedPayment(inPeriodAssignment, "fin-in", IN_PERIOD, 1000, 75, 1075);
        seedPayment(outPeriodAssignment, "fin-out", OUT_OF_PERIOD, 2000, 150, 2150);
        seedPayout(inPeriodAssignment, "COMPLETED", IN_PERIOD, 1000);
        seedPayout(awaitingAssignment, "PENDING", null, 400);
    }

    private void seedAssignment(UUID jobId, UUID assignmentId, String title) {
        jdbcTemplate.update("""
                insert into jobs (id, created_at, is_deleted, creator_id, title, pricing_version, currency,
                                  worker_payout, commission_rate, commission_amount, total_charged,
                                  insurance_required, address_public, experience_required, min_age, max_age)
                values (?, now(), false, ?, ?, '2026-09-v1', 'TRY', 1000.00, 0.0750, 75.00, 1075.00,
                        false, false, false, 18, 65)
                """, jobId, workerId, "Finans " + title);
        jdbcTemplate.update("""
                insert into job_assignments (id, created_at, is_deleted, job_id, worker_id, status, payment_status,
                                             work_hours, pricing_version, currency, worker_payout, commission_rate,
                                             commission_amount, total_charged)
                values (?, now(), false, ?, ?, 'COMPLETED', 'HELD_IN_ESCROW', 8, '2026-09-v1', 'TRY',
                        1000.00, 0.0750, 75.00, 1075.00)
                """, assignmentId, jobId, workerId);
    }

    private void seedPayment(UUID assignmentId, String conversationId, Instant completedAt,
                             int workerPayout, int fee, int total) {
        jdbcTemplate.update("""
                insert into payments (id, created_at, is_deleted, job_assignment_id, conversation_id, status,
                                      currency, pricing_version, worker_payout, commission_rate,
                                      commission_amount, total_charged, completed_at)
                values (?, now(), false, ?, ?, 'SUCCEEDED', 'TRY', '2026-09-v1', ?, 0.0750, ?, ?, ?)
                """, UUID.randomUUID(), assignmentId, conversationId, workerPayout, fee, total, java.sql.Timestamp.from(completedAt));
    }

    private void seedPayout(UUID assignmentId, String status, Instant completedAt, int amount) {
        jdbcTemplate.update("""
                insert into payouts (id, created_at, is_deleted, job_assignment_id, worker_id,
                                     amount, currency, status, completed_at, settlement_reference,
                                     provider_reference)
                values (?, now(), false, ?, ?, ?, 'TRY', ?, ?, ?, ?)
                """, UUID.randomUUID(), assignmentId, workerId, amount, status,
                completedAt == null ? null : java.sql.Timestamp.from(completedAt),
                "COMPLETED".equals(status) ? "STMT-" + assignmentId : null,
                "COMPLETED".equals(status) ? "PROV-" + assignmentId : null);
    }
}
