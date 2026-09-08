package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.support.AbstractStaffIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PayoutReconciliationIT extends AbstractStaffIntegrationTest {

    private static final String PROVIDER_REFERENCE = "iyz-transaction-4711";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UUID worker = UUID.randomUUID();
    private final UUID otherWorker = UUID.randomUUID();
    private UUID jobId;
    private UUID assignmentId;
    private UUID payoutId;

    @BeforeEach
    void setUp() {
        seedUser(worker);
        seedUser(otherWorker);

        jobId = UUID.randomUUID();
        assignmentId = UUID.randomUUID();
        payoutId = UUID.randomUUID();

        jdbcTemplate.update("""
                insert into jobs (id, created_at, is_deleted, title, status, employer_type,
                                  experience_required, insurance_required, min_age, max_age,
                                  start_date_time, end_date_time)
                values (?, now(), false, 'Payout reconciliation', 'OPEN', 'INDIVIDUAL',
                        false, false, 18, 65, now() + interval '1 day', now() + interval '2 days')
                """, jobId);
        jdbcTemplate.update("""
                insert into job_assignments
                    (id, created_at, is_deleted, job_id, worker_id, status, payment_status,
                     pricing_version, currency,
                     worker_payout, commission_rate, commission_amount, total_charged, work_hours)
                values (?, now(), false, ?, ?, 'COMPLETED', 'RELEASED_TO_WORKER', '2026-09-v1', 'TRY',
                        1000.00, 0.0750, 75.00, 1075.00, 0)
                """, assignmentId, jobId, worker);
        jdbcTemplate.update("""
                insert into payouts (id, created_at, is_deleted, job_assignment_id, worker_id,
                                     amount, currency, status, provider_reference, processing_at)
                values (?, now() - interval '2 days', false, ?, ?, 1000.00, 'TRY', 'PROCESSING', ?,
                        now() - interval '2 days')
                """, payoutId, assignmentId, worker, PROVIDER_REFERENCE);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("delete from admin_audit_entries");
        jdbcTemplate.update("delete from staff_members where email like 'payout-reconcile-%@iskoala.com'");
        jdbcTemplate.update("delete from payouts where job_assignment_id = ?", assignmentId);
        jdbcTemplate.update("delete from job_assignments where id = ?", assignmentId);
        jdbcTemplate.update("delete from jobs where id = ?", jobId);
        jdbcTemplate.update("delete from authorities where user_id in (?, ?)", worker, otherWorker);
        jdbcTemplate.update("delete from users where id in (?, ?)", worker, otherWorker);
    }

    private void seedUser(UUID id) {
        jdbcTemplate.update("insert into users (id, created_at, is_deleted, email, password, is_employer, "
                        + "email_verified, phone_verified, identity_verified) "
                        + "values (?, now(), false, ?, 'test', false, true, false, true)",
                id, id + "@example.com");
    }

    private UserPrincipal principal(UUID id) {
        return new UserPrincipal(id, id + "@example.com", Set.of(Role.ROLE_USER), null);
    }

    private String staffToken(String... roles) {
        return staffTokenFor("payout-reconcile-" + UUID.randomUUID() + "@iskoala.com", "Finans", roles);
    }

    private String evidence(String statementLine, Instant settledAt) {
        return "{\"settlementReference\":\"" + statementLine + "\","
                + "\"providerReference\":\"" + PROVIDER_REFERENCE + "\","
                + "\"amount\":1000.00,\"currency\":\"TRY\","
                + "\"settledAt\":\"" + settledAt + "\"}";
    }

    @Test
    void theWorkerSeesTheirPayoutAsCompletedOnceTheStatementLineIsReported() throws Exception {
        Instant settledAt = Instant.now().minus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);

        mockMvc.perform(get("/users/me/payouts").with(user(principal(worker))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].status").value("PROCESSING"))
                .andExpect(jsonPath("$.data[0].completedAt").doesNotExist());

        mockMvc.perform(post("/admin/payouts/{payoutId}/reconcile", payoutId)
                        .header("Authorization", "Bearer " + staffToken("FINANCE_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evidence("TRF-HTTP-1", settledAt)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.amount").value("1000.00"));

        mockMvc.perform(get("/users/me/payouts").with(user(principal(worker))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data[0].completedAt").value(settledAt.toString()));
    }

    @Test
    void reportingTheSameStatementLineTwiceStillLeavesOneCompletedPayout() throws Exception {
        Instant settledAt = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        String token = staffToken("FINANCE_OFFICER");

        mockMvc.perform(post("/admin/payouts/{payoutId}/reconcile", payoutId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evidence("TRF-HTTP-2", settledAt)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/admin/payouts/{payoutId}/reconcile", payoutId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evidence("TRF-HTTP-2", Instant.now().truncatedTo(ChronoUnit.SECONDS))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.completedAt").value(settledAt.toString()));

        assertEquals(1, jdbcTemplate.queryForObject(
                "select count(*) from payouts where status = 'COMPLETED' and job_assignment_id = ?",
                Integer.class, assignmentId));
    }

    @Test
    void aSecondStatementLineOnACompletedPayoutIsRefused() throws Exception {
        String token = staffToken("FINANCE_OFFICER");
        mockMvc.perform(post("/admin/payouts/{payoutId}/reconcile", payoutId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evidence("TRF-HTTP-3", Instant.now().minus(1, ChronoUnit.HOURS))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/admin/payouts/{payoutId}/reconcile", payoutId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evidence("TRF-HTTP-4", Instant.now())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PAYOUT_SETTLEMENT_REFERENCE_CONFLICT"));
    }

    @Test
    void evidenceThatDoesNotMatchTheSnapshotLeavesThePayoutProcessing() throws Exception {
        String body = "{\"settlementReference\":\"TRF-HTTP-5\","
                + "\"providerReference\":\"iyz-transaction-9999\","
                + "\"amount\":1000.00,\"currency\":\"TRY\","
                + "\"settledAt\":\"" + Instant.now().minus(1, ChronoUnit.HOURS) + "\"}";

        mockMvc.perform(post("/admin/payouts/{payoutId}/reconcile", payoutId)
                        .header("Authorization", "Bearer " + staffToken("FINANCE_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PAYOUT_SETTLEMENT_EVIDENCE_MISMATCH"));

        assertEquals("PROCESSING", jdbcTemplate.queryForObject(
                "select status from payouts where id = ?", String.class, payoutId));
    }

    @Test
    void staffWithoutTheReconcilePermissionCannotCompleteAPayout() throws Exception {
        mockMvc.perform(post("/admin/payouts/{payoutId}/reconcile", payoutId)
                        .header("Authorization", "Bearer " + staffToken("DISPUTE_HANDLER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evidence("TRF-HTTP-6", Instant.now().minus(1, ChronoUnit.HOURS))))
                .andExpect(status().isForbidden());

        assertEquals("PROCESSING", jdbcTemplate.queryForObject(
                "select status from payouts where id = ?", String.class, payoutId));
    }

    @Test
    void aMarketplaceUserCannotReachTheReconciliationEndpoint() throws Exception {
        mockMvc.perform(post("/admin/payouts/{payoutId}/reconcile", payoutId)
                        .with(user(principal(worker)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evidence("TRF-HTTP-7", Instant.now().minus(1, ChronoUnit.HOURS))))
                .andExpect(status().isForbidden());

        assertEquals("PROCESSING", jdbcTemplate.queryForObject(
                "select status from payouts where id = ?", String.class, payoutId));
    }

    @Test
    void aWorkerNeverSeesAnotherWorkersPayoutResult() throws Exception {
        mockMvc.perform(post("/admin/payouts/{payoutId}/reconcile", payoutId)
                        .header("Authorization", "Bearer " + staffToken("FINANCE_OFFICER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(evidence("TRF-HTTP-8", Instant.now().minus(1, ChronoUnit.HOURS))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/users/me/payouts").with(user(principal(otherWorker))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }
}
