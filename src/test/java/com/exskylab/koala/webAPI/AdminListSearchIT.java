package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.support.AbstractStaffIntegrationTest;
import org.springframework.http.MediaType;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminListSearchIT extends AbstractStaffIntegrationTest {

    private static final String STAFF_EMAIL = "list-search-%s@iskoala.com";
    private static final String TOO_LONG = "a".repeat(101);
    private static final String EXACTLY_MAX = "a".repeat(100);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final UUID employerId = UUID.randomUUID();
    private final UUID workerId = UUID.randomUUID();
    private final UUID bystanderId = UUID.randomUUID();
    private final UUID matchingJobId = UUID.randomUUID();
    private final UUID otherJobId = UUID.randomUUID();
    private final UUID matchingAssignmentId = UUID.randomUUID();
    private final UUID otherAssignmentId = UUID.randomUUID();
    private final UUID matchingPayoutId = UUID.randomUUID();
    private final UUID otherPayoutId = UUID.randomUUID();
    private final UUID matchingDisputeId = UUID.randomUUID();
    private final UUID otherDisputeId = UUID.randomUUID();
    private final UUID matchingReportId = UUID.randomUUID();
    private final UUID otherReportId = UUID.randomUUID();
    private final UUID matchingReviewId = UUID.randomUUID();
    private final UUID otherReviewId = UUID.randomUUID();
    private final UUID matchingAccountId = UUID.randomUUID();
    private final UUID otherAccountId = UUID.randomUUID();
    private final UUID matchingDeletionId = UUID.randomUUID();
    private final UUID otherDeletionId = UUID.randomUUID();

    private final String workerEmail = "worker-" + workerId + "@example.com";
    private final String employerEmail = "employer-" + employerId + "@example.com";
    private final String bystanderEmail = "bystander-" + bystanderId + "@example.com";
    private final String matchingReference = "KOALA-S" + Integer.toHexString(matchingAccountId.hashCode()).toUpperCase(java.util.Locale.ROOT);
    private final String otherReference = "KOALA-O" + Integer.toHexString(otherAccountId.hashCode()).toUpperCase(java.util.Locale.ROOT);
    private final String matchingIban = ibanFor(matchingAccountId);

    private String financeToken;
    private String trustToken;

    @BeforeEach
    void seed() {
        insertUser(employerId, employerEmail, "Işıl", "Çağlayan", true);
        insertUser(workerId, workerEmail, "Şükrü", "Öztürk", false);
        insertUser(bystanderId, bystanderEmail, "Mehmet", "Yılmaz", false);

        insertJob(matchingJobId, employerId, "Depo Görevlisi " + matchingJobId);
        insertJob(otherJobId, bystanderId, "Garson " + otherJobId);
        insertAssignment(matchingAssignmentId, matchingJobId, workerId);
        insertAssignment(otherAssignmentId, otherJobId, bystanderId);

        insertBankAccount(matchingAccountId, workerId, matchingIban, matchingReference);
        insertBankAccount(otherAccountId, bystanderId, ibanFor(otherAccountId), otherReference);

        insertPayout(matchingPayoutId, matchingAssignmentId, workerId, matchingAccountId, "PROCESSING");
        insertPayout(otherPayoutId, otherAssignmentId, bystanderId, otherAccountId, "COMPLETED");

        insertDispute(matchingDisputeId, matchingAssignmentId, workerId, "OPEN");
        insertDispute(otherDisputeId, otherAssignmentId, bystanderId, "OPEN");

        jdbc.update("""
                insert into reports (id, created_at, is_deleted, reporter_id, reported_user_id, job_id,
                                     reason, description, status)
                values (?, now(), false, ?, ?, ?, 'FRAUD', 'arama testi', 'OPEN')
                """, matchingReportId, employerId, workerId, matchingJobId);
        jdbc.update("""
                insert into reports (id, created_at, is_deleted, reporter_id, reported_user_id, job_id,
                                     reason, description, status)
                values (?, now(), false, ?, null, ?, 'SPAM', 'arama testi', 'OPEN')
                """, otherReportId, bystanderId, otherJobId);

        insertIdentityReview(matchingReviewId, workerId, "Şükrü", "Öztürk", false);
        insertIdentityReview(otherReviewId, bystanderId, "Mehmet", "Yılmaz", true);

        jdbc.update("""
                insert into account_deletion_requests
                    (id, user_id, subject_id, status, reason, requested_at, created_at, is_deleted)
                values (?, ?, ?, 'REQUESTED', 'arama testi', now(), now(), false)
                """, matchingDeletionId, workerId, workerId);
        jdbc.update("""
                insert into account_deletion_requests
                    (id, user_id, subject_id, status, reason, requested_at, created_at, is_deleted)
                values (?, ?, ?, 'REJECTED', 'arama testi', now(), now(), false)
                """, otherDeletionId, bystanderId, bystanderId);

        financeToken = staffTokenFor(STAFF_EMAIL.formatted(UUID.randomUUID()), "Finans", "FINANCE_OFFICER");
        trustToken = staffTokenFor(STAFF_EMAIL.formatted(UUID.randomUUID()), "Güven", "TRUST_LEAD");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from admin_audit_entries");
        jdbc.update("delete from account_deletion_requests where id in (?, ?)", matchingDeletionId, otherDeletionId);
        deleteStaffMembersLike("list-search-%@iskoala.com");
        jdbc.update("delete from user_verifications where id in (?, ?)", matchingReviewId, otherReviewId);
        jdbc.update("delete from reports where id in (?, ?)", matchingReportId, otherReportId);
        jdbc.update("delete from disputes where id in (?, ?)", matchingDisputeId, otherDisputeId);
        jdbc.update("delete from payouts where id in (?, ?)", matchingPayoutId, otherPayoutId);
        jdbc.update("delete from bank_accounts where id in (?, ?)", matchingAccountId, otherAccountId);
        jdbc.update("delete from job_assignments where id in (?, ?)", matchingAssignmentId, otherAssignmentId);
        jdbc.update("delete from jobs where id in (?, ?)", matchingJobId, otherJobId);
        jdbc.update("delete from users where id in (?, ?, ?)", employerId, workerId, bystanderId);
    }

    @Test
    void assignmentsAreFoundByIdTitleAndBothPartiesAndUnmatchedRowsStayOut() throws Exception {
        expectOnly("/admin/job-assignments", financeToken, matchingAssignmentId, otherAssignmentId,
                matchingAssignmentId.toString());
        expectOnly("/admin/job-assignments", financeToken, matchingAssignmentId, otherAssignmentId,
                "depo görevlisi " + matchingJobId);
        expectOnly("/admin/job-assignments", financeToken, matchingAssignmentId, otherAssignmentId, workerEmail);
        expectOnly("/admin/job-assignments", financeToken, matchingAssignmentId, otherAssignmentId, employerEmail);
        expectOnly("/admin/job-assignments", financeToken, matchingAssignmentId, otherAssignmentId,
                "sukru ozturk");
        expectOnly("/admin/job-assignments", financeToken, matchingAssignmentId, otherAssignmentId,
                "ışıl çağlayan");

        search("/admin/job-assignments", financeToken, "no-such-person-" + UUID.randomUUID())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void assignmentQueryIsTrimmedAndTheShortOrEmptyFormDoesNotFilter() throws Exception {
        search("/admin/job-assignments", financeToken, "  " + workerEmail + "  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(matchingAssignmentId.toString()));

        for (String noFilter : new String[]{"", "   ", "z", " z "}) {
            search("/admin/job-assignments", financeToken, noFilter)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[*].id", hasItem(matchingAssignmentId.toString())))
                    .andExpect(jsonPath("$.data.content[*].id", hasItem(otherAssignmentId.toString())));
        }
    }

    @Test
    void assignmentQueryLongerThanAHundredCharactersIsRejectedButExactlyAHundredIsNot() throws Exception {
        search("/admin/job-assignments", financeToken, TOO_LONG)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ADMIN_SEARCH_QUERY_TOO_LONG"));

        search("/admin/job-assignments", financeToken, EXACTLY_MAX)
                .andExpect(status().isOk());
        search("/admin/job-assignments", financeToken, " " + TOO_LONG.substring(1) + " ")
                .andExpect(status().isOk());
    }

    @Test
    void identityReviewsAreFoundByProfileNameEmailAndDeclaredNameWithinTheStatusQueue() throws Exception {
        expectOnly("/admin/identity-verifications", trustToken, matchingReviewId, otherReviewId, workerEmail);
        expectOnly("/admin/identity-verifications", trustToken, matchingReviewId, otherReviewId, "SUKRU");
        expectOnly("/admin/identity-verifications", trustToken, matchingReviewId, otherReviewId, "öztürk");

        search("/admin/identity-verifications", trustToken, workerEmail, "status", "APPROVED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        search("/admin/identity-verifications", trustToken, bystanderEmail, "status", "APPROVED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(otherReviewId.toString()));

        search("/admin/identity-verifications", trustToken, TOO_LONG)
                .andExpect(status().isBadRequest());
    }

    @Test
    void ibanVerificationsAreFoundByOwnerAndReferenceAndStillHideTheFullIban() throws Exception {
        expectOnly("/admin/bank-account-verifications", financeToken, matchingAccountId, otherAccountId,
                workerEmail, "accountId");
        expectOnly("/admin/bank-account-verifications", financeToken, matchingAccountId, otherAccountId,
                "şükrü öztürk", "accountId");
        expectOnly("/admin/bank-account-verifications", financeToken, matchingAccountId, otherAccountId,
                matchingReference.toLowerCase(java.util.Locale.ROOT), "accountId");

        search("/admin/bank-account-verifications", financeToken, workerEmail)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].reference").value(matchingReference))
                .andExpect(jsonPath("$.data.content[0].maskedIban").value(not(matchingIban)));

        search("/admin/bank-account-verifications", financeToken, workerEmail, "status", "VERIFIED")
                .andExpect(status().isBadRequest());
        search("/admin/bank-account-verifications", financeToken, TOO_LONG)
                .andExpect(status().isBadRequest());
    }

    @Test
    void payoutsAreFoundByPayoutIdAssignmentIdAndRecipientWithinTheStatusFilter() throws Exception {
        expectOnly("/admin/payouts", financeToken, matchingPayoutId, otherPayoutId, matchingPayoutId.toString());
        expectOnly("/admin/payouts", financeToken, matchingPayoutId, otherPayoutId,
                matchingAssignmentId.toString().toUpperCase(java.util.Locale.ROOT));
        expectOnly("/admin/payouts", financeToken, matchingPayoutId, otherPayoutId, workerEmail);
        expectOnly("/admin/payouts", financeToken, matchingPayoutId, otherPayoutId, "sukru");

        search("/admin/payouts", financeToken, bystanderEmail)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        search("/admin/payouts", financeToken, bystanderEmail, "status", "COMPLETED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(otherPayoutId.toString()));
        search("/admin/payouts", financeToken, workerEmail, "status", "COMPLETED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        search("/admin/payouts", financeToken, TOO_LONG)
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportsAreFoundByEitherPartyAndJobTitleWithinTheOtherFilters() throws Exception {
        expectOnly("/admin/reports", trustToken, matchingReportId, otherReportId, workerEmail);
        expectOnly("/admin/reports", trustToken, matchingReportId, otherReportId, employerEmail);
        expectOnly("/admin/reports", trustToken, matchingReportId, otherReportId, "isil caglayan");
        expectOnly("/admin/reports", trustToken, matchingReportId, otherReportId,
                "depo görevlisi " + matchingJobId);
        expectOnly("/admin/reports", trustToken, otherReportId, matchingReportId, "garson " + otherJobId);

        search("/admin/reports", trustToken, workerEmail, "status", "DISMISSED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        search("/admin/reports", trustToken, workerEmail, "reason", "SPAM")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        search("/admin/reports", trustToken, workerEmail, "reason", "FRAUD")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(matchingReportId.toString()));

        search("/admin/reports", trustToken, TOO_LONG)
                .andExpect(status().isBadRequest());
    }

    @Test
    void disputesAreFoundByIdsAndAnyPartyWithinTheStatusQueue() throws Exception {
        expectOnly("/admin/disputes", trustToken, matchingDisputeId, otherDisputeId, matchingDisputeId.toString());
        expectOnly("/admin/disputes", trustToken, matchingDisputeId, otherDisputeId,
                matchingAssignmentId.toString());
        expectOnly("/admin/disputes", trustToken, matchingDisputeId, otherDisputeId, workerEmail);
        expectOnly("/admin/disputes", trustToken, matchingDisputeId, otherDisputeId, "ISIL CAGLAYAN");

        search("/admin/disputes", trustToken, workerEmail, "status", "RESOLVED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        search("/admin/disputes", trustToken, workerEmail, "status", "OPEN")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(matchingDisputeId.toString()));

        search("/admin/disputes", trustToken, TOO_LONG)
                .andExpect(status().isBadRequest());
    }

    @Test
    void accountDeletionsAreFoundByTheRequesterEmailWithinTheStatusFilter() throws Exception {
        expectOnly("/admin/account-deletions", trustToken, matchingDeletionId, otherDeletionId, workerEmail);
        expectOnly("/admin/account-deletions", trustToken, matchingDeletionId, otherDeletionId,
                workerEmail.toUpperCase(java.util.Locale.ROOT));

        search("/admin/account-deletions", trustToken, workerEmail, "status", "REJECTED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        search("/admin/account-deletions", trustToken, bystanderEmail, "status", "REJECTED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(otherDeletionId.toString()));

        search("/admin/account-deletions", trustToken, TOO_LONG)
                .andExpect(status().isBadRequest());
    }

    @Test
    void theIdFiltersNarrowEachQueueAndIntersectWithTheSearch() throws Exception {
        expectOnlyFiltered("/admin/job-assignments", financeToken, matchingAssignmentId, otherAssignmentId,
                "workerId", workerId.toString());
        expectOnlyFiltered("/admin/job-assignments", financeToken, matchingAssignmentId, otherAssignmentId,
                "employerId", employerId.toString());
        expectOnlyFiltered("/admin/job-assignments", financeToken, otherAssignmentId, matchingAssignmentId,
                "jobId", otherJobId.toString());
        filtered("/admin/job-assignments", financeToken, "workerId", workerId.toString(), "jobId", otherJobId.toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        filtered("/admin/job-assignments", financeToken, "workerId", workerId.toString(), "query", "garson")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        UUID companyId = UUID.randomUUID();
        jdbc.update("insert into companies (id, created_at, is_deleted, name, tax_number, email, approval_status) "
                        + "values (?, now(), false, ?, ?, ?, 'APPROVED')",
                companyId, "Süzgeç Lojistik " + companyId, String.valueOf(Math.abs(companyId.hashCode())),
                "filter-company-" + companyId + "@example.com");
        jdbc.update("update jobs set company_id = ? where id = ?", companyId, otherJobId);
        try {
            expectOnlyFiltered("/admin/jobs", trustToken, matchingJobId, otherJobId, "employerId", employerId.toString());
            expectOnlyFiltered("/admin/jobs", trustToken, otherJobId, matchingJobId, "companyId", companyId.toString());
            filtered("/admin/jobs", trustToken, "companyId", companyId.toString(), "employerId", employerId.toString())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(0));
        } finally {
            jdbc.update("update jobs set company_id = null where id = ?", otherJobId);
            jdbc.update("delete from companies where id = ?", companyId);
        }

        expectOnlyFiltered("/admin/disputes", trustToken, matchingDisputeId, otherDisputeId, "userId", workerId.toString());
        expectOnlyFiltered("/admin/disputes", trustToken, matchingDisputeId, otherDisputeId, "userId", employerId.toString());
        expectOnlyFiltered("/admin/disputes", trustToken, otherDisputeId, matchingDisputeId, "userId", bystanderId.toString());

        expectOnlyFiltered("/admin/payouts", financeToken, matchingPayoutId, otherPayoutId, "workerId", workerId.toString());
        filtered("/admin/payouts", financeToken, "workerId", bystanderId.toString(), "status", "COMPLETED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(otherPayoutId.toString()));
        filtered("/admin/payouts", financeToken, "workerId", bystanderId.toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        expectOnlyFiltered("/admin/reports", trustToken, matchingReportId, otherReportId, "reporterId", employerId.toString());
        expectOnlyFiltered("/admin/reports", trustToken, otherReportId, matchingReportId, "reporterId", bystanderId.toString());
        filtered("/admin/reports", trustToken, "reporterId", employerId.toString(), "reportedUserId", bystanderId.toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        filtered("/admin/reports", trustToken, "reporterId", employerId.toString(), "reportedUserId", workerId.toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void aDeletionRequestShowsItsBlockersAndThenTheStaffMemberWhoRejectedIt() throws Exception {
        UUID pendingPaymentId = UUID.randomUUID();
        jdbc.update("""
                insert into payments (id, created_at, is_deleted, job_assignment_id, conversation_id, status,
                                      currency, verification_attempts)
                values (?, now(), false, ?, ?, 'INITIATED', 'TRY', 0)
                """, pendingPaymentId, matchingAssignmentId, "pending-" + pendingPaymentId);
        UUID trustStaffId = jdbc.queryForObject(
                "select id from staff_members where email like 'list-search-%@iskoala.com' and display_name = 'Güven'",
                UUID.class);
        var worker = user(new UserPrincipal(workerId, workerEmail, Set.of(Role.ROLE_USER), null));
        try {
            filtered("/admin/account-deletions/" + matchingDeletionId, trustToken)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("REQUESTED"))
                    .andExpect(jsonPath("$.data.rejectionReason").value(nullValue()))
                    .andExpect(jsonPath("$.data.rejectedByStaffId").value(nullValue()))
                    .andExpect(jsonPath("$.data.blockers", hasSize(2)))
                    .andExpect(jsonPath("$.data.blockers[?(@.type == 'PENDING_PAYMENT')].id")
                            .value(pendingPaymentId.toString()))
                    .andExpect(jsonPath("$.data.blockers[?(@.type == 'DISPUTE')].id")
                            .value(matchingDisputeId.toString()));
            filtered("/admin/account-deletions", trustToken, "status", "REQUESTED")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[?(@.id == '" + matchingDeletionId + "')].blockers[*].type",
                            hasItem("DISPUTE")));

            mockMvc.perform(post("/admin/account-deletions/" + matchingDeletionId + "/reject")
                            .header("Authorization", "Bearer " + trustToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"Açık uyuşmazlık kapanmadan silinemez\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("REJECTED"))
                    .andExpect(jsonPath("$.data.rejectionReason").value("Açık uyuşmazlık kapanmadan silinemez"))
                    .andExpect(jsonPath("$.data.rejectedByStaffId").value(trustStaffId.toString()))
                    .andExpect(jsonPath("$.data.blockers", hasSize(0)));
            filtered("/admin/account-deletions/" + matchingDeletionId, trustToken)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.rejectionReason").value("Açık uyuşmazlık kapanmadan silinemez"))
                    .andExpect(jsonPath("$.data.rejectedByStaffId").value(trustStaffId.toString()));
            assertEquals(trustStaffId, jdbc.queryForObject(
                    "select rejected_by_staff_id from account_deletion_requests where id = ?", UUID.class,
                    matchingDeletionId));

            mockMvc.perform(get("/legal/account-deletion").with(worker))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("REJECTED"))
                    .andExpect(jsonPath("$.data.blockedReason").value("Açık uyuşmazlık kapanmadan silinemez"))
                    .andExpect(jsonPath("$.data.rejectionReason").value("Açık uyuşmazlık kapanmadan silinemez"))
                    .andExpect(jsonPath("$.data.rejectedByStaffId").value(nullValue()))
                    .andExpect(jsonPath("$.data.blockers").value(nullValue()));
        } finally {
            jdbc.update("delete from payments where id = ?", pendingPaymentId);
        }
    }

    @Test
    void theDeletionQueueResolvesBlockersWithoutAQueryPerRow() throws Exception {
        UUID secondDeletionId = UUID.randomUUID();
        jdbc.update("""
                insert into account_deletion_requests
                    (id, user_id, subject_id, status, reason, requested_at, created_at, is_deleted)
                values (?, ?, ?, 'REQUESTED', 'n+1 testi', now() + interval '1 second', now(), false)
                """, secondDeletionId, employerId, employerId);
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean wasEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            filtered("/admin/account-deletions", trustToken, "status", "REQUESTED", "size", "1",
                    "sort", "requestedAt,desc")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(1)))
                    .andExpect(jsonPath("$.data.content[0].id").value(secondDeletionId.toString()))
                    .andExpect(jsonPath("$.data.content[0].blockers[*].type", hasItem("DISPUTE")));
            long statementsForOneRow = statistics.getPrepareStatementCount();

            statistics.clear();
            filtered("/admin/account-deletions", trustToken, "status", "REQUESTED", "size", "2",
                    "sort", "requestedAt,desc")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(2)))
                    .andExpect(jsonPath("$.data.content[*].id",
                            containsInAnyOrder(secondDeletionId.toString(), matchingDeletionId.toString())))
                    .andExpect(jsonPath("$.data.content[?(@.id == '" + matchingDeletionId + "')].blockers[*].type",
                            hasItem("DISPUTE")));
            long statementsForTwoRows = statistics.getPrepareStatementCount();

            assertEquals(statementsForOneRow, statementsForTwoRows,
                    "silme kuyruğu engelleyicileri satır sayısıyla büyüyen sorgu atıyor (N+1)");
        } finally {
            statistics.setStatisticsEnabled(wasEnabled);
            jdbc.update("delete from account_deletion_requests where id = ?", secondDeletionId);
        }
    }

    @Test
    void anIdFilterIsAnAuditedSearch() throws Exception {
        filtered("/admin/job-assignments", financeToken, "workerId", workerId.toString()).andExpect(status().isOk());

        Integer rows = jdbc.queryForObject(
                "select count(*) from admin_audit_entries where resource_type = 'job-assignments' and allowed = true",
                Integer.class);
        assertEquals(1, rows, "id süzgeciyle liste okuması denetim satırı yazmalı");
    }

    @Test
    void searchDoesNotWidenWhoMayReadAQueue() throws Exception {
        search("/admin/job-assignments", trustToken, workerEmail).andExpect(status().isForbidden());
        search("/admin/payouts", trustToken, workerEmail).andExpect(status().isForbidden());
        search("/admin/bank-account-verifications", trustToken, workerEmail).andExpect(status().isForbidden());
        search("/admin/disputes", financeToken, workerEmail).andExpect(status().isForbidden());
        search("/admin/reports", financeToken, workerEmail).andExpect(status().isForbidden());
        search("/admin/identity-verifications", financeToken, workerEmail).andExpect(status().isForbidden());
        search("/admin/account-deletions", financeToken, workerEmail).andExpect(status().isForbidden());
    }

    private void expectOnly(String path, String token, UUID expected, UUID hidden, String query) throws Exception {
        expectOnly(path, token, expected, hidden, query, "id");
    }

    private void expectOnly(String path, String token, UUID expected, UUID hidden, String query, String idField)
            throws Exception {
        search(path, token, query)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*]." + idField, hasItem(expected.toString())))
                .andExpect(jsonPath("$.data.content[*]." + idField, not(hasItem(hidden.toString()))));
    }

    private void expectOnlyFiltered(String path, String token, UUID expected, UUID hidden, String... params)
            throws Exception {
        filtered(path, token, params)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].id", hasItem(expected.toString())))
                .andExpect(jsonPath("$.data.content[*].id", not(hasItem(hidden.toString()))));
    }

    private ResultActions filtered(String path, String token, String... params) throws Exception {
        var request = get(path).header("Authorization", "Bearer " + token);
        boolean sized = false;
        for (int i = 0; i + 1 < params.length; i += 2) {
            request.param(params[i], params[i + 1]);
            sized |= "size".equals(params[i]);
        }
        if (!sized) {
            request.param("size", "100");
        }
        return mockMvc.perform(request);
    }

    private ResultActions search(String path, String token, String query, String... extraParams) throws Exception {
        var request = get(path)
                .param("query", query)
                .param("size", "100")
                .header("Authorization", "Bearer " + token);
        for (int i = 0; i + 1 < extraParams.length; i += 2) {
            request.param(extraParams[i], extraParams[i + 1]);
        }
        return mockMvc.perform(request);
    }

    private static String ibanFor(UUID id) {
        return "TR" + String.format("%024d", Math.floorMod(id.getLeastSignificantBits(), 1_000_000_000_000_000_000L));
    }

    private void insertUser(UUID id, String email, String firstName, String lastName, boolean employer) {
        jdbc.update("""
                insert into users (id, created_at, is_deleted, email, password, is_employer,
                                   email_verified, phone_verified, identity_verified, first_name, last_name)
                values (?, now(), false, ?, 'test', ?, true, false, true, ?, ?)
                """, id, email, employer, firstName, lastName);
    }

    private void insertJob(UUID id, UUID creator, String title) {
        jdbc.update("""
                insert into jobs (id, created_at, is_deleted, creator_id, title, status, employer_type,
                                  pricing_version, currency, worker_payout, commission_rate, commission_amount,
                                  total_charged, insurance_required, address_public, experience_required,
                                  min_age, max_age)
                values (?, now(), false, ?, ?, 'OPEN', 'INDIVIDUAL', '2026-09-v1', 'TRY', 1000.00, 0.0750, 75.00,
                        1075.00, false, false, false, 18, 65)
                """, id, creator, title);
    }

    private void insertAssignment(UUID id, UUID jobId, UUID worker) {
        jdbc.update("""
                insert into job_assignments (id, created_at, is_deleted, job_id, worker_id, status, payment_status,
                                             assignment_date_time, work_hours, pricing_version, currency,
                                             worker_payout, commission_rate, commission_amount, total_charged)
                values (?, now(), false, ?, ?, 'COMPLETED', 'RELEASED_TO_WORKER', now(), 8, '2026-09-v1', 'TRY',
                        1000.00, 0.0750, 75.00, 1075.00)
                """, id, jobId, worker);
    }

    private void insertBankAccount(UUID id, UUID owner, String iban, String reference) {
        jdbc.update("""
                insert into bank_accounts (id, created_at, is_deleted, user_id, iban, is_primary, is_active,
                                           verification_status, verification_reference, verification_amount,
                                           verification_started_at, verification_expires_at,
                                           verification_attempts, account_holder_name_verified)
                values (?, now(), false, ?, ?, true, true, 'PENDING', ?, 1.00, now(), now() + interval '1 day',
                        0, false)
                """, id, owner, iban, reference);
    }

    private void insertPayout(UUID id, UUID assignmentId, UUID worker, UUID accountId, String status) {
        boolean completed = "COMPLETED".equals(status);
        jdbc.update("""
                insert into payouts (id, created_at, is_deleted, job_assignment_id, worker_id, bank_account_id,
                                     amount, currency, status, provider_reference, processing_at,
                                     settlement_reference, completed_at)
                values (?, now(), false, ?, ?, ?, 1000.00, 'TRY', ?, ?, now(), ?,
                        case when ? then now() end)
                """, id, assignmentId, worker, accountId, status, "iyz-" + id,
                completed ? "stmt-" + id : null, completed);
    }

    private void insertDispute(UUID id, UUID assignmentId, UUID openedBy, String status) {
        jdbc.update("""
                insert into disputes (id, created_at, is_deleted, job_assignment_id, opened_by_id, status, reason)
                values (?, now(), false, ?, ?, ?, 'arama testi')
                """, id, assignmentId, openedBy, status);
    }

    private void insertIdentityReview(UUID id, UUID user, String firstName, String lastName, boolean verified) {
        long digits = Math.floorMod(id.getMostSignificantBits(), 8_999_999_999L) + 1_000_000_000L;
        jdbc.update("""
                insert into user_verifications (id, verification_type, created_at, is_deleted, is_verified,
                                                user_id, tc_identity_number, first_name, last_name, birth_date,
                                                tc_document_number)
                values (?, 'IDENTITY', now(), false, ?, ?, ?, ?, ?, date '1998-04-12', 'A01A00000')
                """, id, verified, user, "1" + digits, firstName, lastName);
    }
}
