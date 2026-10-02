package com.exskylab.koala.webAPI;

import com.exskylab.koala.support.AbstractStaffIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AuditSubjectIT extends AbstractStaffIntegrationTest {

    private static final String STAFF_EMAIL_PREFIX = "audit-subject-";
    private static final String LATE_FEE_KEY = "cancellation.late-fee-rate";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final UUID workerId = UUID.randomUUID();
    private final UUID employerId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final UUID individualJobId = UUID.randomUUID();
    private final UUID companyJobId = UUID.randomUUID();
    private final UUID individualAssignmentId = UUID.randomUUID();
    private final UUID companyAssignmentId = UUID.randomUUID();
    private final UUID payoutId = UUID.randomUUID();
    private final UUID disputeId = UUID.randomUUID();
    private final UUID reportId = UUID.randomUUID();
    private final UUID deletionId = UUID.randomUUID();
    private final UUID imageId = UUID.randomUUID();
    private final UUID galleryRowId = UUID.randomUUID();
    private final UUID bankAccountId = UUID.randomUUID();
    private final String reference = "KS" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

    private String ownerToken;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from admin_audit_entries");
        insertUser(workerId);
        insertUser(employerId);
        jdbc.update("insert into companies (id, created_at, is_deleted, name, tax_number, email, approval_status) "
                        + "values (?, now(), false, ?, ?, ?, 'PENDING')",
                companyId, "Konu Şirketi " + companyId, String.valueOf(Math.abs(companyId.hashCode())),
                "subject-company-" + companyId + "@example.com");
        jdbc.update("insert into job_categories (id, name, code, requires_insurance) values (?, ?, ?, false)",
                categoryId, "Konu", "SUBJECT_" + categoryId);
        insertJob(individualJobId, null);
        insertJob(companyJobId, companyId);
        insertAssignment(individualAssignmentId, individualJobId);
        insertAssignment(companyAssignmentId, companyJobId);
        jdbc.update("""
                insert into payouts (id, created_at, is_deleted, job_assignment_id, worker_id,
                                     amount, currency, status, provider_reference, processing_at)
                values (?, now(), false, ?, ?, 100.00, 'TRY', 'PROCESSING', ?, now())
                """, payoutId, individualAssignmentId, workerId, "subject-provider-" + payoutId);
        jdbc.update("insert into disputes (id, created_at, is_deleted, job_assignment_id, opened_by_id, "
                        + "status, reason) values (?, now(), false, ?, ?, 'OPEN', 'Konu testi')",
                disputeId, companyAssignmentId, workerId);
        jdbc.update("""
                insert into reports (id, created_at, is_deleted, reporter_id, reported_user_id,
                                     reason, description, status)
                values (?, now(), false, ?, ?, 'HARASSMENT', 'konu testi', 'OPEN')
                """, reportId, employerId, workerId);
        jdbc.update("""
                insert into account_deletion_requests
                    (id, user_id, subject_id, status, reason, requested_at, created_at, is_deleted)
                values (?, ?, ?, 'REQUESTED', 'konu testi', now(), now(), false)
                """, deletionId, workerId, workerId);
        jdbc.update("insert into media (id, created_at, media_type, file_name, file_type, file_url, file_size) "
                        + "values (?, now(), 'IMAGE', 'vitrin.jpg', 'image/jpeg', ?, 2048)",
                imageId, "companies/" + imageId + ".jpg");
        jdbc.update("insert into company_images (id, created_at, is_deleted, company_id, image_id, "
                        + "sort_order, is_cover) values (?, now(), false, ?, ?, 0, true)",
                galleryRowId, companyId, imageId);
        jdbc.update("""
                insert into bank_accounts (id, created_at, is_deleted, user_id, iban, is_primary, is_active,
                                           verification_status, verification_reference, verification_amount,
                                           verification_started_at, verification_expires_at,
                                           verification_attempts, account_holder_name_verified)
                values (?, now(), false, ?, 'TR330006100519786457841326', true, true, 'PENDING', ?, 1.00, now(),
                        now() + interval '1 day', 0, false)
                """, bankAccountId, workerId, reference);

        ownerToken = staffAccessToken(createStaffMember(
                STAFF_EMAIL_PREFIX + UUID.randomUUID() + "@iskoala.com", "Konu Denemesi", "OWNER"));
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from admin_audit_entries");
        jdbc.update("delete from notifications where recipient_id in (?, ?)", workerId, employerId);
        jdbc.update("delete from user_profile_sync_outbox where user_id in (?, ?)", workerId, employerId);
        jdbc.update("delete from bank_accounts where id = ?", bankAccountId);
        jdbc.update("delete from company_images where id = ?", galleryRowId);
        jdbc.update("delete from media where id = ?", imageId);
        jdbc.update("delete from account_deletion_requests where id = ?", deletionId);
        jdbc.update("delete from reports where id = ?", reportId);
        jdbc.update("delete from disputes where id = ?", disputeId);
        jdbc.update("delete from refunds where payment_id in (select id from payments where job_assignment_id in "
                + "(?, ?))", individualAssignmentId, companyAssignmentId);
        jdbc.update("delete from payouts where job_assignment_id in (?, ?)",
                individualAssignmentId, companyAssignmentId);
        jdbc.update("delete from job_assignments where id in (?, ?)", individualAssignmentId, companyAssignmentId);
        jdbc.update("delete from jobs where id in (?, ?)", individualJobId, companyJobId);
        jdbc.update("delete from job_categories where id = ?", categoryId);
        jdbc.update("delete from companies where id = ?", companyId);
        jdbc.update("delete from authorities where user_id in (?, ?)", workerId, employerId);
        jdbc.update("delete from users where id in (?, ?)", workerId, employerId);
        jdbc.update("delete from app_settings where setting_key = ?", LATE_FEE_KEY);
        deleteStaffMembersLike(STAFF_EMAIL_PREFIX + "%");
    }

    @Test
    void aReleaseOnAnIndividualJobNamesTheWorkerAndTheEmployerWhoOpenedTheJob() throws Exception {
        perform(post("/admin/job-assignments/{id}/release", individualAssignmentId));

        Map<String, Object> row = onlyRow("payout.release");
        assertSubject(row, "USER", workerId);
        assertRelated(row, "USER", employerId);
    }

    @Test
    void aRefundOnACompanyJobNamesTheCompanyAsTheCounterparty() throws Exception {
        perform(post("/admin/job-assignments/{id}/refund", companyAssignmentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\":10.00,\"reason\":\"konu testi\"}"));

        Map<String, Object> row = onlyRow("payment.refund");
        assertSubject(row, "USER", workerId);
        assertRelated(row, "COMPANY", companyId);
    }

    @Test
    void aDisputeResolutionNamesTheAssignmentPartiesEvenAfterItIsReclassified() throws Exception {
        perform(post("/admin/disputes/{id}/resolve", disputeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resolution\":\"RELEASE_TO_WORKER\",\"note\":\"konu testi\"}"));

        List<Map<String, Object>> rows = rows();
        assertEquals(1, rows.size(), rows.toString());
        assertSubject(rows.getFirst(), "USER", workerId);
        assertRelated(rows.getFirst(), "COMPANY", companyId);
    }

    @Test
    void aPayoutReconciliationIsAboutTheWorker() throws Exception {
        perform(post("/admin/payouts/{id}/reconcile", payoutId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"settlementReference\":\"EKSTRE-1\",\"providerReference\":\"baska\","
                        + "\"amount\":100.00,\"currency\":\"TRY\",\"settledAt\":\"2026-10-01T10:00:00Z\"}"));

        Map<String, Object> row = onlyRow("payout.reconcile");
        assertSubject(row, "USER", workerId);
        assertRelated(row, null, null);
    }

    @Test
    void aDeletionRejectionIsAboutTheRequester() throws Exception {
        perform(post("/admin/account-deletions/{id}/reject", deletionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"açık yükümlülük\"}"))
                .andExpect(status().isOk());

        assertSubject(onlyRow("user.deletion.reject"), "USER", workerId);
    }

    @Test
    void aReportReviewIsAboutTheReportedUser() throws Exception {
        perform(post("/admin/reports/{id}/review", reportId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"DISMISSED\",\"note\":\"konu testi\"}"))
                .andExpect(status().isOk());

        assertSubject(onlyRow("report.review"), "USER", workerId);
    }

    @Test
    void aCompanyDecisionIsAboutTheCompany() throws Exception {
        perform(post("/admin/companies/{id}/approve", companyId)).andExpect(status().isOk());

        Map<String, Object> row = onlyRow("company.approve");
        assertSubject(row, "COMPANY", companyId);
        assertRelated(row, null, null);
    }

    @Test
    void anImageRemovalIsAboutTheCompanyWhoseGalleryHoldsIt() throws Exception {
        perform(delete("/admin/images/{id}", imageId));

        assertSubject(onlyRow("image.remove"), "COMPANY", companyId);
    }

    @Test
    void anIbanConfirmationIsAboutTheAccountOwnerFoundByItsReference() throws Exception {
        perform(post("/admin/bank-account-verifications/{reference}/confirm", reference)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"senderIban\":\"TR330006100519786457841326\",\"amount\":1.00}"));

        assertSubject(onlyRow("bank.account.verify"), "USER", workerId);
    }

    @Test
    void readsAndPlatformWritesCarryNoSubject() throws Exception {
        perform(get("/admin/users/{id}", workerId)).andExpect(status().isOk());
        perform(put("/admin/settings/" + LATE_FEE_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"value\":\"0.40\"}"))
                .andExpect(status().isOk());

        assertSubject(onlyRow("user.read_pii"), null, null);
        assertSubject(onlyRow("setting.update"), null, null);
    }

    @Test
    void theTimelineFilterFindsDecisionsAboutAPersonWhereverTheyWereRecorded() throws Exception {
        perform(post("/admin/job-assignments/{id}/release", individualAssignmentId));
        perform(post("/admin/account-deletions/{id}/reject", deletionId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"açık yükümlülük\"}"))
                .andExpect(status().isOk());
        perform(post("/admin/companies/{id}/approve", companyId)).andExpect(status().isOk());
        perform(get("/admin/users/{id}", workerId)).andExpect(status().isOk());

        perform(get("/admin/audit-log").param("subjectType", "USER").param("subjectId", workerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[*].action",
                        containsInAnyOrder("payout.release", "user.deletion.reject")))
                .andExpect(jsonPath("$.data.content[?(@.action == 'payout.release')].resourceId")
                        .value(individualAssignmentId.toString()))
                .andExpect(jsonPath("$.data.content[?(@.action == 'payout.release')].relatedSubjectId")
                        .value(employerId.toString()));

        perform(get("/admin/audit-log").param("subjectId", employerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].action").value("payout.release"))
                .andExpect(jsonPath("$.data.content[0].subjectId").value(workerId.toString()))
                .andExpect(jsonPath("$.data.content[0].relatedSubjectType").value("USER"));

        perform(get("/admin/audit-log").param("subjectType", "COMPANY").param("subjectId", employerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)));

        perform(get("/admin/audit-log").param("subjectType", "COMPANY").param("subjectId", companyId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].action").value("company.approve"))
                .andExpect(jsonPath("$.data.content[0].subjectType").value("COMPANY"));

        perform(get("/admin/audit-log").param("subjectType", "PERSON"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theTimelineIsReadWithoutAQueryPerRow() throws Exception {
        insertAuditRow(workerId);
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean wasEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            perform(get("/admin/audit-log").param("subjectId", workerId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(1)));
            long statementsForOne = statistics.getPrepareStatementCount();

            jdbc.update("delete from admin_audit_entries where action = 'audit.read'");
            insertAuditRow(workerId);
            insertAuditRow(workerId);

            statistics.clear();
            perform(get("/admin/audit-log").param("subjectId", workerId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(3)));

            assertEquals(statementsForOne, statistics.getPrepareStatementCount(),
                    "konuya göre denetim listesi satır başına sorgu atıyor (N+1)");
        } finally {
            statistics.setStatisticsEnabled(wasEnabled);
        }
    }

    private org.springframework.test.web.servlet.ResultActions perform(MockHttpServletRequestBuilder request)
            throws Exception {
        return mockMvc.perform(request.header("Authorization", "Bearer " + ownerToken));
    }

    private List<Map<String, Object>> rows() {
        return jdbc.queryForList("select * from admin_audit_entries where action <> 'audit.read' "
                + "order by occurred_at");
    }

    private Map<String, Object> onlyRow(String action) {
        List<Map<String, Object>> matching = jdbc.queryForList(
                "select * from admin_audit_entries where action = ?", action);
        assertEquals(1, matching.size(), "Tek bir '" + action + "' satırı bekleniyordu: " + rows());
        Map<String, Object> row = matching.getFirst();
        assertEquals(Boolean.TRUE, row.get("allowed"), "Personel yetkiliydi: " + row);
        return row;
    }

    private static void assertSubject(Map<String, Object> row, String type, UUID id) {
        assertEquals(type, row.get("subject_type"), "subject_type: " + row);
        assertEquals(id, row.get("subject_id"), "subject_id: " + row);
    }

    private static void assertRelated(Map<String, Object> row, String type, UUID id) {
        assertEquals(type, row.get("related_subject_type"), "related_subject_type: " + row);
        assertEquals(id, row.get("related_subject_id"), "related_subject_id: " + row);
        if (type == null) {
            assertNull(row.get("related_subject_id"));
        }
    }

    private void insertAuditRow(UUID subjectId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                insert into admin_audit_entries (id, occurred_at, actor_staff_id, actor_display_name, permission,
                                                 action, resource_type, resource_id, allowed, mutating,
                                                 sensitivity, retain_until, subject_type, subject_id)
                values (?, now(), null, 'Konu Denemesi', 'identity.review', 'identity.reject',
                        'identity-verifications', ?, true, true, 'PERSONAL_DATA', now() + interval '1 day',
                        'USER', ?)
                """, id, UUID.randomUUID().toString(), subjectId);
        jdbc.update("insert into admin_audit_entry_details (audit_entry_id, purge_after) "
                + "values (?, now() + interval '1 day')", id);
    }

    private void insertUser(UUID id) {
        jdbc.update("insert into users (id, created_at, is_deleted, email, password, is_employer, "
                        + "email_verified, phone_verified, identity_verified, first_name, last_name) "
                        + "values (?, now(), false, ?, 'test', true, true, false, false, 'Konu', 'Kişi')",
                id, id + "@example.com");
    }

    private void insertJob(UUID id, UUID company) {
        jdbc.update("insert into jobs (id, created_at, is_deleted, creator_id, company_id, job_category_id, "
                        + "employer_type, title, description, position, address_public, type, salary_type, "
                        + "salary, min_age, max_age, experience_required, insurance_required, status, headcount, "
                        + "start_date_time, end_date_time, pricing_version, currency, worker_payout, "
                        + "commission_rate, commission_amount, total_charged) "
                        + "values (?, now(), false, ?, ?, ?, ?, ?, 'Açıklama', 'Pozisyon', false, "
                        + "'ONE_TIME', 'HOURLY', 100, 18, 65, false, false, 'OPEN', 2, now() - interval '2 days', "
                        + "now() - interval '1 day', 'v1', 'TRY', 100.00, 0.1000, 10.00, 110.00)",
                id, employerId, company, categoryId, company == null ? "INDIVIDUAL" : "COMPANY",
                "Konu testi " + id);
    }

    private void insertAssignment(UUID id, UUID jobId) {
        jdbc.update("insert into job_assignments (id, created_at, is_deleted, job_id, worker_id, status, "
                        + "payment_status, assignment_date_time, work_hours, worker_payout, commission_rate, "
                        + "commission_amount, total_charged, pricing_version, currency) "
                        + "values (?, now(), false, ?, ?, 'COMPLETED', 'PENDING_COLLECTION', now() - interval '2 days', 0, "
                        + "100.00, 0.1000, 10.00, 110.00, 'v1', 'TRY')",
                id, jobId, workerId);
    }
}
