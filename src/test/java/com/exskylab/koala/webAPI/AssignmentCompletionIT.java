package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AssignmentCompletionIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final UUID employerId = UUID.randomUUID();
    private final UUID workerId = UUID.randomUUID();
    private final UUID companyAdminId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();
    private final UUID paymentId = UUID.randomUUID();
    private final UUID bankAccountId = UUID.randomUUID();
    private final UUID disputeId = UUID.randomUUID();

    private static final List<String> WORK_COMPLETED_BODIES =
            List.of("İş tamamlandı olarak işaretlendi.", "The work was marked as completed.");
    private static final List<String> PAYOUT_RELEASED_BODIES =
            List.of("Ödemeniz gönderime alındı.", "Your payment is on its way.");

    @BeforeEach
    void setUp() {
        insertUser(employerId, true);
        insertUser(workerId, false);
        insertUser(companyAdminId, true);
        jdbc.update("insert into companies (id, created_at, is_deleted, name, tax_number, email, approval_status) "
                        + "values (?, now(), false, ?, ?, ?, 'APPROVED')",
                companyId, "Tamamlama Şirketi " + companyId,
                String.valueOf(Math.abs(companyId.hashCode())), companyId + "@example.com");
        jdbc.update("insert into company_contacts (id, created_at, is_deleted, company_id, user_id, role) "
                        + "values (?, now(), false, ?, ?, 'ADMIN')",
                UUID.randomUUID(), companyId, companyAdminId);
        jdbc.update("insert into jobs (id, created_at, is_deleted, creator_id, company_id, employer_type, title, "
                        + "description, position, address_public, type, salary_type, salary, min_age, max_age, "
                        + "experience_required, insurance_required, status, headcount, start_date_time, "
                        + "end_date_time, pricing_version, currency, worker_payout, commission_rate, "
                        + "commission_amount, total_charged) "
                        + "values (?, now(), false, ?, ?, 'COMPANY', ?, 'Açıklama', 'Pozisyon', false, 'ONE_TIME', "
                        + "'HOURLY', 100, 18, 65, false, false, 'OPEN', 1, now() - interval '2 days', "
                        + "now() - interval '1 day', 'v1', 'TRY', 1000.00, 0.0750, 75.00, 1075.00)",
                jobId, employerId, companyId, "Tamamlama " + jobId);
        jdbc.update("insert into job_assignments (id, created_at, is_deleted, job_id, worker_id, status, "
                        + "payment_status, assignment_date_time, work_hours, worker_payout, commission_rate, "
                        + "commission_amount, total_charged, pricing_version, currency) "
                        + "values (?, now(), false, ?, ?, 'IN_PROGRESS', 'HELD_IN_ESCROW', now(), 8, 1000.00, "
                        + "0.0750, 75.00, 1075.00, 'v1', 'TRY')",
                assignmentId, jobId, workerId);
        jdbc.update("insert into payments (id, created_at, is_deleted, job_assignment_id, conversation_id, status, "
                        + "currency, pricing_version, worker_payout, commission_rate, commission_amount, "
                        + "total_charged, provider_transaction_id, verification_attempts, completed_at) "
                        + "values (?, now() - interval '1 hour', false, ?, ?, 'SUCCEEDED', 'TRY', 'v1', 1000.00, "
                        + "0.0750, 75.00, 1075.00, ?, 1, now() - interval '1 hour')",
                paymentId, assignmentId, "conv-" + paymentId, "txn-" + paymentId);
        jdbc.update("insert into bank_accounts (id, created_at, is_deleted, user_id, iban, is_primary, is_active, "
                        + "verification_status, verification_attempts, account_holder_name_verified) "
                        + "values (?, now(), false, ?, 'TR180006200119000006672315', true, true, 'VERIFIED', 0, true)",
                bankAccountId, workerId);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from notifications where recipient_id in (?, ?, ?) or sender_id in (?, ?, ?) "
                        + "or created_by_id in (?, ?, ?)",
                employerId, workerId, companyAdminId, employerId, workerId, companyAdminId,
                employerId, workerId, companyAdminId);
        jdbc.update("delete from settlement_tasks where job_assignment_id = ?", assignmentId);
        jdbc.update("delete from payouts where job_assignment_id = ?", assignmentId);
        jdbc.update("delete from disputes where id = ?", disputeId);
        jdbc.update("delete from payments where id = ?", paymentId);
        jdbc.update("delete from user_badges where user_id = ?", workerId);
        jdbc.update("delete from job_assignments where id = ?", assignmentId);
        jdbc.update("delete from jobs where id = ?", jobId);
        jdbc.update("delete from bank_accounts where id = ?", bankAccountId);
        jdbc.update("delete from company_contacts where company_id = ?", companyId);
        jdbc.update("delete from companies where id = ?", companyId);
        jdbc.update("delete from authorities where user_id in (?, ?, ?)", employerId, workerId, companyAdminId);
        jdbc.update("delete from users where id in (?, ?, ?)", employerId, workerId, companyAdminId);
    }

    @Test
    void theEmployerCompletingTheWorkReleasesTheEscrowToTheWorker() throws Exception {
        completeAs(employerId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.paymentStatus").value("RELEASED_TO_WORKER"))
                .andExpect(jsonPath("$.data.actualCompletionDateTime").isNotEmpty())
                .andExpect(jsonPath("$.data.payout.status").value("PENDING"))
                .andExpect(jsonPath("$.data.payout.completedAt").value(nullValue()));

        assertEscrowReleased();
        assertTheWorkerWasToldOnce(WORK_COMPLETED_BODIES);
        assertTheWorkerWasToldOnce(PAYOUT_RELEASED_BODIES);
    }

    @Test
    void aWorkerWithoutAPrimaryIbanBlocksTheCompletionLoudly() throws Exception {
        jdbc.update("delete from bank_accounts where id = ?", bankAccountId);

        completeAs(employerId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PAYOUT_WORKER_IBAN_NOT_VERIFIED"));

        assertEquals("IN_PROGRESS", assignmentColumn("status"));
        assertEquals("HELD_IN_ESCROW", assignmentColumn("payment_status"));
        assertEquals(0, countRows("payouts"));
        assertEquals(0, countRows("settlement_tasks"));
        assertEquals(0, inAppNotificationsForTheWorker(WORK_COMPLETED_BODIES));
    }

    @Test
    void aCompanyAdminCompletingTheWorkReleasesTheEscrowToo() throws Exception {
        completeAs(companyAdminId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentStatus").value("RELEASED_TO_WORKER"))
                .andExpect(jsonPath("$.data.payout.status").value("PENDING"));

        assertEscrowReleased();
    }

    @Test
    void anOpenDisputeLeavesTheMoneyInEscrowButStillCompletesTheWork() throws Exception {
        jdbc.update("insert into disputes (id, created_at, is_deleted, job_assignment_id, opened_by_id, "
                        + "status, reason) values (?, now(), false, ?, ?, 'OPEN', ?)",
                disputeId, assignmentId, workerId, "Saatler eksik yazıldı");

        completeAs(employerId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.paymentStatus").value("HELD_IN_ESCROW"))
                .andExpect(jsonPath("$.data.payout").value(nullValue()));

        assertEquals("COMPLETED", assignmentColumn("status"));
        assertEquals("HELD_IN_ESCROW", assignmentColumn("payment_status"));
        assertEquals(0, countRows("payouts"));
        assertEquals(0, countRows("settlement_tasks"));
        assertTheWorkerWasToldOnce(WORK_COMPLETED_BODIES);
        assertEquals(0, inAppNotificationsForTheWorker(PAYOUT_RELEASED_BODIES));
    }

    @Test
    void aSecondCompletionIsRefusedAndDoesNotTouchThePayout() throws Exception {
        completeAs(employerId).andExpect(status().isOk());

        completeAs(employerId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("JOB_ASSIGNMENT_NOT_COMPLETABLE"));

        assertEscrowReleased();
    }

    @Test
    void theWorkerCannotCompleteTheirOwnAssignment() throws Exception {
        completeAs(workerId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_ASSIGNMENT_NOT_FOUND"));

        assertEquals("IN_PROGRESS", assignmentColumn("status"));
        assertEquals("HELD_IN_ESCROW", assignmentColumn("payment_status"));
        assertEquals(0, countRows("payouts"));
    }

    private void assertEscrowReleased() {
        assertEquals("COMPLETED", assignmentColumn("status"));
        assertEquals("RELEASED_TO_WORKER", assignmentColumn("payment_status"));
        assertEquals(1, countRows("payouts"));
        assertEquals("PENDING", jdbc.queryForObject(
                "select status from payouts where job_assignment_id = ?", String.class, assignmentId));
        assertEquals("EMPLOYER_COMPLETION", jdbc.queryForObject(
                "select release_origin from payouts where job_assignment_id = ?", String.class, assignmentId));
        assertEquals(1, countRows("settlement_tasks"));
        assertEquals("APPROVE", jdbc.queryForObject(
                "select operation from settlement_tasks where job_assignment_id = ?", String.class, assignmentId));
        assertEquals("PENDING", jdbc.queryForObject(
                "select status from settlement_tasks where job_assignment_id = ?", String.class, assignmentId));
    }

    private void assertTheWorkerWasToldOnce(List<String> bodies) {
        assertEquals(1, inAppNotificationsForTheWorker(bodies));
    }

    private int inAppNotificationsForTheWorker(List<String> bodies) {
        return jdbc.queryForObject(
                "select count(*) from notifications where recipient_id = ? and channel = 'IN_APP' "
                        + "and target_id = ? and body in (?, ?)",
                Integer.class, workerId, assignmentId, bodies.get(0), bodies.get(1));
    }

    private ResultActions completeAs(UUID actorId) throws Exception {
        return mockMvc.perform(post("/job-assignments/{assignmentId}/complete", assignmentId)
                .with(user(principal(actorId))));
    }

    private String assignmentColumn(String column) {
        return jdbc.queryForObject("select " + column + " from job_assignments where id = ?",
                String.class, assignmentId);
    }

    private int countRows(String table) {
        return jdbc.queryForObject("select count(*) from " + table + " where job_assignment_id = ?",
                Integer.class, assignmentId);
    }

    private void insertUser(UUID id, boolean employer) {
        jdbc.update("insert into users (id, created_at, is_deleted, email, password, is_employer, "
                        + "email_verified, phone_verified, identity_verified) "
                        + "values (?, now(), false, ?, 'test', ?, true, false, false)",
                id, id + "@example.com", employer);
    }

    private UserPrincipal principal(UUID id) {
        return new UserPrincipal(id, id + "@example.com", Set.of(Role.ROLE_USER), null);
    }
}
