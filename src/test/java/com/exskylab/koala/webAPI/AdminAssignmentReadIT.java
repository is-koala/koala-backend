package com.exskylab.koala.webAPI;

import com.exskylab.koala.support.AbstractStaffIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminAssignmentReadIT extends AbstractStaffIntegrationTest {

    private static final String WORKER_EMAIL = "assignment-worker-%s@example.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final UUID employerId = UUID.randomUUID();
    private final UUID workerId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();

    @BeforeEach
    void seedAssignment() {
        String workerEmail = WORKER_EMAIL.formatted(workerId);
        jdbcTemplate.update("""
                insert into users (id, created_at, is_deleted, email, password, is_employer,
                                   email_verified, phone_verified, identity_verified, first_name, last_name)
                values (?, now(), false, ?, 'test', true, true, false, true, 'Deneme', 'İşveren')
                """, employerId, employerId + "@example.com");
        jdbcTemplate.update("""
                insert into users (id, created_at, is_deleted, email, password, is_employer,
                                   email_verified, phone_verified, identity_verified, first_name, last_name)
                values (?, now(), false, ?, 'test', false, true, false, true, 'Deneme', 'İşçi')
                """, workerId, workerEmail);
        jdbcTemplate.update("""
                insert into jobs (id, created_at, is_deleted, creator_id, title, pricing_version, currency,
                                  worker_payout, commission_rate, commission_amount, total_charged,
                                  insurance_required, address_public, experience_required, min_age, max_age)
                values (?, now(), false, ?, 'Depo görevlisi', '2026-09-v1', 'TRY', 1000.00, 0.0750, 75.00, 1075.00,
                        false, false, false, 18, 65)
                """, jobId, employerId);
        jdbcTemplate.update("""
                insert into job_assignments (id, created_at, is_deleted, job_id, worker_id, status, payment_status,
                                             assignment_date_time, work_hours, pricing_version, currency,
                                             worker_payout, commission_rate, commission_amount, total_charged)
                values (?, now(), false, ?, ?, 'COMPLETED', 'HELD_IN_ESCROW', now(), 8, '2026-09-v1', 'TRY',
                        1000.00, 0.0750, 75.00, 1075.00)
                """, assignmentId, jobId, workerId);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from admin_audit_entries");
        jdbcTemplate.update("delete from staff_members where email like 'assignment-read-%@iskoala.com'");
        jdbcTemplate.update("delete from job_assignments where id = ?", assignmentId);
        jdbcTemplate.update("delete from jobs where id = ?", jobId);
        jdbcTemplate.update("delete from users where id in (?, ?)", employerId, workerId);
    }

    private String tokenFor(String... roles) {
        return staffTokenFor("assignment-read-" + UUID.randomUUID() + "@iskoala.com", "Okuyucu", roles);
    }

    @Test
    void aFinanceAnalystReachesTheAssignmentReadEndpoint() throws Exception {
        mockMvc.perform(get("/admin/job-assignments/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenFor("FINANCE_ANALYST")))
                .andExpect(status().isNotFound());
    }

    @Test
    void aDisputeHandlerCannotReadTheEscrowBehindAnAssignment() throws Exception {
        mockMvc.perform(get("/admin/job-assignments/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + tokenFor("DISPUTE_HANDLER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffCanPageAssignmentsWithTheSameSafeSummaryAsTheDetail() throws Exception {
        String token = tokenFor("FINANCE_ANALYST");

        mockMvc.perform(get("/admin/job-assignments")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].id", hasItem(assignmentId.toString())))
                .andExpect(jsonPath("$.data.content[*].jobTitle", hasItem("Depo görevlisi")))
                .andExpect(jsonPath("$.data.content[*].worker.firstName", hasItem("Deneme")))
                .andExpect(jsonPath("$.data.content[*].pricing.employerTotal", hasItem("1075.00")))
                .andExpect(jsonPath("$.data.content[0].email").doesNotExist())
                .andExpect(content().string(not(WORKER_EMAIL.formatted(workerId))));

        mockMvc.perform(get("/admin/job-assignments/" + assignmentId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(assignmentId.toString()))
                .andExpect(jsonPath("$.data.jobTitle").value("Depo görevlisi"))
                .andExpect(jsonPath("$.data.pricing.employerTotal").value("1075.00"));
    }

    @Test
    void aDisputeHandlerCannotPageAssignments() throws Exception {
        mockMvc.perform(get("/admin/job-assignments")
                        .header("Authorization", "Bearer " + tokenFor("DISPUTE_HANDLER")))
                .andExpect(status().isForbidden());
    }
}
