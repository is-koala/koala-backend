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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class DisputeQueueIT extends AbstractStaffIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final UUID employerId = UUID.randomUUID();
    private final UUID workerId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final UUID openAssignmentId = UUID.randomUUID();
    private final UUID reviewAssignmentId = UUID.randomUUID();
    private final UUID resolvedAssignmentId = UUID.randomUUID();
    private final UUID openDisputeId = UUID.randomUUID();
    private final UUID reviewDisputeId = UUID.randomUUID();
    private final UUID resolvedDisputeId = UUID.randomUUID();

    private String handlerToken;

    @BeforeEach
    void setUp() {
        insertUser(employerId);
        insertUser(workerId);
        jdbc.update("insert into companies (id, created_at, is_deleted, name, tax_number, email, approval_status) "
                        + "values (?, now(), false, ?, ?, ?, 'APPROVED')",
                companyId, "Kuyruk Şirketi " + companyId, String.valueOf(Math.abs(companyId.hashCode())),
                "queue-company-" + companyId + "@example.com");
        jdbc.update("insert into job_categories (id, name, code, requires_insurance) values (?, ?, ?, false)",
                categoryId, "Kuyruk", "QUEUE_" + categoryId);
        jdbc.update("insert into jobs (id, created_at, is_deleted, creator_id, job_category_id, employer_type, "
                        + "title, description, position, address_public, type, salary_type, salary, "
                        + "min_age, max_age, experience_required, insurance_required, status, "
                        + "headcount, start_date_time, end_date_time, pricing_version, currency, worker_payout, "
                        + "commission_rate, commission_amount, total_charged) "
                        + "values (?, now(), false, ?, ?, 'INDIVIDUAL', ?, 'Açıklama', 'Pozisyon', false, "
                        + "'ONE_TIME', 'HOURLY', 100, 18, 65, false, false, 'OPEN', 1, now() - interval '2 hours', "
                        + "now() + interval '1 day', 'v1', 'TRY', 100.00, 0.1000, 10.00, 110.00)",
                jobId, employerId, categoryId, "Kuyruk testi " + jobId);

        insertAssignment(openAssignmentId);
        insertAssignment(reviewAssignmentId);
        insertAssignment(resolvedAssignmentId);

        jdbc.update("insert into disputes (id, created_at, is_deleted, job_assignment_id, opened_by_id, "
                        + "status, reason) values (?, now(), false, ?, ?, 'OPEN', ?)",
                openDisputeId, openAssignmentId, workerId, "Ücret eksik");
        jdbc.update("insert into disputes (id, created_at, is_deleted, job_assignment_id, opened_by_id, "
                        + "status, reason) values (?, now(), false, ?, ?, 'UNDER_REVIEW', ?)",
                reviewDisputeId, reviewAssignmentId, workerId, "Saatler uyuşmuyor");
        jdbc.update("insert into disputes (id, created_at, is_deleted, job_assignment_id, opened_by_id, "
                        + "status, reason, resolution, resolution_note, resolved_at) "
                        + "values (?, now(), false, ?, ?, 'RESOLVED', ?, 'DISMISSED', 'Dayanaksız', now())",
                resolvedDisputeId, resolvedAssignmentId, workerId, "Kapandı");

        handlerToken = staffTokenFor("dispute-queue-" + UUID.randomUUID() + "@iskoala.com",
                "Uyuşmazlık Uzmanı", "DISPUTE_HANDLER");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from disputes where id in (?, ?, ?)",
                openDisputeId, reviewDisputeId, resolvedDisputeId);
        jdbc.update("delete from job_assignments where id in (?, ?, ?)",
                openAssignmentId, reviewAssignmentId, resolvedAssignmentId);
        jdbc.update("delete from jobs where id = ?", jobId);
        jdbc.update("delete from job_categories where id = ?", categoryId);
        jdbc.update("delete from companies where id = ?", companyId);
        jdbc.update("delete from staff_member_roles where staff_member_id in "
                + "(select id from staff_members where email like 'dispute-queue-%@iskoala.com')");
        jdbc.update("delete from staff_members where email like 'dispute-queue-%@iskoala.com'");
        jdbc.update("delete from users where id in (?, ?)", employerId, workerId);
    }

    @Test
    void omittingStatusKeepsThePendingQueueAndDoesNotLeakClosedRows() throws Exception {
        long pending = countStatuses("OPEN", "UNDER_REVIEW");

        mockMvc.perform(get("/admin/disputes")
                        .param("size", "1")
                        .header("Authorization", "Bearer " + handlerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(pending))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].status").value(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is("OPEN"), org.hamcrest.Matchers.is("UNDER_REVIEW"))))
                .andExpect(jsonPath("$.data.content[0].id").value(not(resolvedDisputeId.toString())))
                .andExpect(jsonPath("$.data.content[0].amount").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].employerTotal").doesNotExist());

        mockMvc.perform(get("/admin/disputes")
                        .param("size", "100")
                        .header("Authorization", "Bearer " + handlerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].id", hasItem(openDisputeId.toString())))
                .andExpect(jsonPath("$.data.content[*].id", hasItem(reviewDisputeId.toString())))
                .andExpect(jsonPath("$.data.content[*].id", not(hasItem(resolvedDisputeId.toString()))));
    }

    @Test
    void statusResolvedPagesClosedDisputesHonestlyIncludingDismissedResolutions() throws Exception {
        long resolved = countStatuses("RESOLVED");

        mockMvc.perform(get("/admin/disputes")
                        .param("status", "RESOLVED")
                        .param("size", "1")
                        .header("Authorization", "Bearer " + handlerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(resolved))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].status").value("RESOLVED"));

        mockMvc.perform(get("/admin/disputes")
                        .param("status", "RESOLVED")
                        .param("size", "100")
                        .header("Authorization", "Bearer " + handlerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].id", hasItem(resolvedDisputeId.toString())))
                .andExpect(jsonPath("$.data.content[*].id", not(hasItem(openDisputeId.toString()))));
    }

    @Test
    void statusOpenCountsOnlyOpenRows() throws Exception {
        long open = countStatuses("OPEN");

        mockMvc.perform(get("/admin/disputes")
                        .param("status", "OPEN")
                        .header("Authorization", "Bearer " + handlerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(open))
                .andExpect(jsonPath("$.data.content[*].id", hasItem(openDisputeId.toString())))
                .andExpect(jsonPath("$.data.content[*].id", not(hasItem(reviewDisputeId.toString()))))
                .andExpect(jsonPath("$.data.content[*].id", not(hasItem(resolvedDisputeId.toString()))));
    }

    @Test
    void dismissedIsAResolutionNotAListStatusAndFinanceCannotReadTheQueue() throws Exception {
        mockMvc.perform(get("/admin/disputes")
                        .param("status", "DISMISSED")
                        .header("Authorization", "Bearer " + handlerToken))
                .andExpect(status().isBadRequest());

        String financeToken = staffTokenFor("dispute-queue-finance-" + UUID.randomUUID() + "@iskoala.com",
                "Finans", "FINANCE_ANALYST");
        mockMvc.perform(get("/admin/disputes")
                        .header("Authorization", "Bearer " + financeToken))
                .andExpect(status().isForbidden());
    }

    private long countStatuses(String... statuses) {
        String placeholders = String.join(",", java.util.Collections.nCopies(statuses.length, "?"));
        Long count = jdbc.queryForObject(
                "select count(*) from disputes where is_deleted = false and status in (" + placeholders + ")",
                Long.class, (Object[]) statuses);
        return count == null ? 0 : count;
    }

    private void insertAssignment(UUID assignmentId) {
        jdbc.update("insert into job_assignments (id, created_at, is_deleted, job_id, worker_id, status, "
                        + "payment_status, work_hours, total_charged) "
                        + "values (?, now(), false, ?, ?, 'IN_PROGRESS', 'HELD_IN_ESCROW', 0, 110.00)",
                assignmentId, jobId, workerId);
    }

    private void insertUser(UUID id) {
        jdbc.update("insert into users (id, created_at, is_deleted, email, password, is_employer, "
                        + "email_verified, phone_verified, identity_verified) "
                        + "values (?, now(), false, ?, 'test', false, true, false, false)",
                id, id + "@example.com");
    }
}
