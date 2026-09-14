package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AssignmentReadIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final UUID employerId = UUID.randomUUID();
    private final UUID workerId = UUID.randomUUID();
    private final UUID companyAdminId = UUID.randomUUID();
    private final UUID outsiderId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID countryId = UUID.randomUUID();
    private final UUID cityId = UUID.randomUUID();
    private final UUID districtId = UUID.randomUUID();
    private final UUID addressId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();
    private final UUID secondAssignmentId = UUID.randomUUID();
    private final UUID secondWorkerId = UUID.randomUUID();
    private final UUID applicationId = UUID.randomUUID();
    private final UUID paymentId = UUID.randomUUID();
    private final UUID payoutId = UUID.randomUUID();
    private final UUID cancellationId = UUID.randomUUID();
    private final UUID logoId = UUID.randomUUID();
    private final UUID coverId = UUID.randomUUID();
    private final UUID coverEntryId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        insertUser(employerId, true);
        insertUser(workerId, false);
        insertUser(companyAdminId, true);
        insertUser(outsiderId, false);
        jdbc.update("insert into companies (id, created_at, is_deleted, name, tax_number, email, approval_status) "
                        + "values (?, now(), false, ?, ?, ?, 'APPROVED')",
                companyId, "Atama Şirketi " + companyId,
                String.valueOf(Math.abs(companyId.hashCode())), companyId + "@example.com");
        jdbc.update("insert into company_contacts (id, created_at, is_deleted, company_id, user_id, role) "
                        + "values (?, now(), false, ?, ?, 'ADMIN')",
                UUID.randomUUID(), companyId, companyAdminId);
        jdbc.update("insert into countries (id, created_at, is_deleted, name, code) values (?, now(), false, ?, ?)",
                countryId, "Türkiye", "TR");
        jdbc.update("insert into cities (id, created_at, is_deleted, name, country_id) values (?, now(), false, ?, ?)",
                cityId, "İstanbul", countryId);
        jdbc.update("insert into districts (id, created_at, is_deleted, name, city_id) values (?, now(), false, ?, ?)",
                districtId, "Kadıköy", cityId);
        jdbc.update("insert into addresses (id, created_at, is_deleted, country_id, city_id, district_id, "
                        + "latitude, longitude, canonical_version, needs_review) "
                        + "values (?, now(), false, ?, ?, ?, 40.987654, 29.034567, 1, false)",
                addressId, countryId, cityId, districtId);
        jdbc.update("insert into job_categories (id, name, code, requires_insurance) values (?, ?, ?, false)",
                categoryId, "Atama okuma", "ASSIGNMENT_READ_" + categoryId);
        jdbc.update("insert into jobs (id, created_at, is_deleted, creator_id, company_id, job_category_id, "
                        + "employer_type, title, description, position, address_id, address_public, type, salary_type, salary, "
                        + "min_age, max_age, experience_required, insurance_required, status, headcount, "
                        + "start_date_time, end_date_time, pricing_version, currency, worker_payout, "
                        + "commission_rate, commission_amount, total_charged) "
                        + "values (?, now(), false, ?, ?, ?, 'COMPANY', ?, 'Açıklama', 'Pozisyon', ?, false, "
                        + "'ONE_TIME', 'HOURLY', 100, 18, 65, false, false, 'OPEN', 1, now(), "
                        + "now() + interval '1 day', 'v1', 'TRY', 100.00, 0.1000, 10.00, 110.00)",
                jobId, employerId, companyId, categoryId, "Atama okuma " + jobId, addressId);
        jdbc.update("insert into job_assignments (id, created_at, is_deleted, job_id, worker_id, status, "
                        + "payment_status, assignment_date_time, work_hours, worker_payout, commission_rate, "
                        + "commission_amount, total_charged, pricing_version, currency) "
                        + "values (?, now(), false, ?, ?, 'CONFIRMED', 'HELD_IN_ESCROW', now(), 0, 100.00, "
                        + "0.1000, 10.00, 110.00, 'v1', 'TRY')",
                assignmentId, jobId, workerId);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from notifications where recipient_id in (?, ?, ?, ?, ?) "
                        + "or sender_id in (?, ?, ?, ?, ?) or created_by_id in (?, ?, ?, ?, ?)",
                employerId, workerId, companyAdminId, outsiderId, secondWorkerId,
                employerId, workerId, companyAdminId, outsiderId, secondWorkerId,
                employerId, workerId, companyAdminId, outsiderId, secondWorkerId);
        jdbc.update("delete from reviews where job_assignment_id in (?, ?)", assignmentId, secondAssignmentId);
        jdbc.update("delete from rating_summaries where user_id in (?, ?, ?, ?, ?)",
                employerId, workerId, companyAdminId, outsiderId, secondWorkerId);
        jdbc.update("delete from user_badges where user_id in (?, ?, ?, ?, ?)",
                employerId, workerId, companyAdminId, outsiderId, secondWorkerId);
        jdbc.update("delete from cancellations where id = ?", cancellationId);
        jdbc.update("delete from payouts where id = ?", payoutId);
        jdbc.update("delete from payments where id = ?", paymentId);
        jdbc.update("delete from job_assignments where id in (?, ?)", assignmentId, secondAssignmentId);
        jdbc.update("delete from job_applications where id = ?", applicationId);
        jdbc.update("delete from job_images where id = ?", coverEntryId);
        jdbc.update("update companies set logo_id = null where id = ?", companyId);
        jdbc.update("delete from media where id in (?, ?)", logoId, coverId);
        jdbc.update("delete from jobs where id = ?", jobId);
        jdbc.update("delete from job_categories where id = ?", categoryId);
        jdbc.update("delete from addresses where id = ?", addressId);
        jdbc.update("delete from districts where id = ?", districtId);
        jdbc.update("delete from cities where id = ?", cityId);
        jdbc.update("delete from countries where id = ?", countryId);
        jdbc.update("delete from company_contacts where company_id = ?", companyId);
        jdbc.update("delete from companies where id = ?", companyId);
        jdbc.update("delete from authorities where user_id in (?, ?, ?, ?, ?)",
                employerId, workerId, companyAdminId, outsiderId, secondWorkerId);
        jdbc.update("delete from users where id in (?, ?, ?, ?, ?)",
                employerId, workerId, companyAdminId, outsiderId, secondWorkerId);
    }

    @Test
    void theTimelineFieldsAreNullUntilTheEventsHappen() throws Exception {
        getAssignmentAs(workerId)
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.application").value(nullValue()))
                .andExpect(jsonPath("$.data.confirmedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.expectedCompletionDateTime").value(nullValue()))
                .andExpect(jsonPath("$.data.paymentSecuredAt").value(nullValue()))
                .andExpect(jsonPath("$.data.cancellation").value(nullValue()))
                .andExpect(jsonPath("$.data.payout").value(nullValue()))
                .andExpect(jsonPath("$.data.employer.id").value(employerId.toString()))
                .andExpect(jsonPath("$.data.employer.profilePictureUrl").value(nullValue()))
                .andExpect(jsonPath("$.data.company.id").value(companyId.toString()))
                .andExpect(jsonPath("$.data.company.name").value("Atama Şirketi " + companyId))
                .andExpect(jsonPath("$.data.company.logoUrl").value(nullValue()))
                .andExpect(jsonPath("$.data.job.employerType").value("COMPANY"))
                .andExpect(jsonPath("$.data.job.companyLogoUrl").value(nullValue()));
    }

    @Test
    void theTimelineFieldsCarryTheApplicationPaymentCancellationAndPayout() throws Exception {
        attachCompanyLogo();
        linkApplication();
        jdbc.update("update job_assignments set confirmed_at = now() - interval '2 days', "
                + "expected_completion_date_time = now() + interval '1 day' where id = ?", assignmentId);
        insertSucceededPayment();
        insertCancellation();
        insertPayout();

        getAssignmentAs(employerId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.application.id").value(applicationId.toString()))
                .andExpect(jsonPath("$.data.application.applicationDateTime").isNotEmpty())
                .andExpect(jsonPath("$.data.application.answeredDateTime").isNotEmpty())
                .andExpect(jsonPath("$.data.confirmedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.expectedCompletionDateTime").isNotEmpty())
                .andExpect(jsonPath("$.data.paymentSecuredAt").isNotEmpty())
                .andExpect(jsonPath("$.data.cancellation.cancelledByParty").value("EMPLOYER"))
                .andExpect(jsonPath("$.data.cancellation.cancelledAt").isNotEmpty())
                .andExpect(jsonPath("$.data.cancellation.feeRate").value("0.2500"))
                .andExpect(jsonPath("$.data.cancellation.feeAmount").value("27.50"))
                .andExpect(jsonPath("$.data.cancellation.refundAmount").value("82.50"))
                .andExpect(jsonPath("$.data.payout.id").value(payoutId.toString()))
                .andExpect(jsonPath("$.data.payout.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.payout.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.company.logoUrl").value(endsWith("logos/" + logoId + ".png")))
                .andExpect(jsonPath("$.data.job.companyLogoUrl").value(endsWith("logos/" + logoId + ".png")));

        mockMvc.perform(get("/users/me/assignments").with(user(principal(workerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].application.id").value(applicationId.toString()))
                .andExpect(jsonPath("$.data.content[0].paymentSecuredAt").isNotEmpty())
                .andExpect(jsonPath("$.data.content[0].cancellation.feeAmount").value("27.50"))
                .andExpect(jsonPath("$.data.content[0].payout.id").value(payoutId.toString()))
                .andExpect(jsonPath("$.data.content[0].employer.id").value(employerId.toString()))
                .andExpect(jsonPath("$.data.content[0].company.id").value(companyId.toString()))
                .andExpect(jsonPath("$.data.content[0].company.logoUrl").value(endsWith("logos/" + logoId + ".png")))
                .andExpect(jsonPath("$.data.content[0].job.companyLogoUrl").value(endsWith("logos/" + logoId + ".png")));
    }

    @Test
    void paymentSecuredAtIsTheFirstSucceededAttemptNotAFailedOne() throws Exception {
        UUID failed = UUID.randomUUID();
        jdbc.update("insert into payments (id, created_at, is_deleted, job_assignment_id, conversation_id, status, "
                        + "currency, verification_attempts, completed_at) "
                        + "values (?, now() - interval '3 hours', false, ?, ?, 'FAILED', 'TRY', 0, "
                        + "now() - interval '3 hours')",
                failed, assignmentId, "conv-failed-" + failed);
        try {
            getAssignmentAs(workerId).andExpect(jsonPath("$.data.paymentSecuredAt").value(nullValue()));

            insertSucceededPayment();
            getAssignmentAs(workerId).andExpect(jsonPath("$.data.paymentSecuredAt").isNotEmpty());
        } finally {
            jdbc.update("delete from payments where id = ?", failed);
        }
    }

    @Test
    void acceptingTheAssignmentAnswersWithTheTimelineDtoAndStampsConfirmedAt() throws Exception {
        attachJobCover();
        jdbc.update("update job_assignments set status = 'PENDING_CONFIRMATION', payment_status = null where id = ?",
                assignmentId);

        mockMvc.perform(post("/job-assignments/{assignmentId}/respond", assignmentId)
                        .with(user(principal(workerId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(assignmentId.toString()))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.confirmedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.job.id").value(jobId.toString()))
                .andExpect(jsonPath("$.data.employer.id").value(employerId.toString()))
                .andExpect(jsonPath("$.data.job.coverImageUrl").value(endsWith("covers/" + coverId + ".jpg")))
                .andExpect(jsonPath("$.data.messagingAvailableReason").value("PAYMENT_REQUIRED"));

        assertEquals(1, jdbc.queryForObject(
                "select count(*) from job_assignments where id = ? and confirmed_at is not null",
                Integer.class, assignmentId));
    }

    @Test
    void theEmployerAndTheCompanyAdminListTheJobsAssignments() throws Exception {
        insertSecondAssignment();
        attachJobCover();

        for (UUID actorId : new UUID[]{employerId, companyAdminId}) {
            mockMvc.perform(get("/jobs/{jobId}/assignments", jobId).with(user(principal(actorId))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(2)))
                    .andExpect(jsonPath("$.data.content[0].id").value(secondAssignmentId.toString()))
                    .andExpect(jsonPath("$.data.content[1].id").value(assignmentId.toString()))
                    .andExpect(jsonPath("$.data.content[1].worker.id").value(workerId.toString()))
                    .andExpect(jsonPath("$.data.content[1].company.id").value(companyId.toString()))
                    .andExpect(jsonPath("$.data.content[1].job.coverImageUrl")
                            .value(endsWith("covers/" + coverId + ".jpg")))
                    .andExpect(jsonPath("$.data.totalElements").value(2));
        }
    }

    @Test
    void theWorkerAndAnOutsiderGetTheConcealedNotFoundForTheJobsAssignments() throws Exception {
        for (UUID actorId : new UUID[]{workerId, outsiderId}) {
            mockMvc.perform(get("/jobs/{jobId}/assignments", jobId).with(user(principal(actorId))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));
        }
    }

    @Test
    void anUnknownSortFieldOnTheJobsAssignmentsIsRejected() throws Exception {
        mockMvc.perform(get("/jobs/{jobId}/assignments", jobId).with(user(principal(employerId)))
                        .param("sort", "workHours,desc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PAGE_SORT_FIELD_NOT_ALLOWED"));
    }

    @Test
    void theJobAssignmentListIsBuiltWithoutAQueryPerRow() throws Exception {
        insertSecondAssignment();
        attachCompanyLogo();
        attachJobCover();
        linkApplication();
        insertSucceededPayment();
        insertCancellation();
        insertPayout();
        insertReview(UUID.randomUUID(), employerId, workerId, 5, "Dakik");
        insertReview(UUID.randomUUID(), workerId, employerId, 4, "Net brief");
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean wasEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            mockMvc.perform(get("/jobs/{jobId}/assignments", jobId).param("size", "1")
                    .with(user(principal(employerId)))).andExpect(status().isOk());
            long statementsForOneRow = statistics.getPrepareStatementCount();
            long fetchesForOneRow = statistics.getEntityFetchCount();

            statistics.clear();
            mockMvc.perform(get("/jobs/{jobId}/assignments", jobId).param("size", "2")
                            .with(user(principal(employerId))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(2)))
                    .andExpect(jsonPath("$.data.content[1].myReview.rating").value(5))
                    .andExpect(jsonPath("$.data.content[1].counterpartyReviewed").value(true))
                    .andExpect(jsonPath("$.data.content[0].myReview").value(nullValue()))
                    .andExpect(jsonPath("$.data.content[0].counterpartyReviewed").value(false));
            long statementsForTwoRows = statistics.getPrepareStatementCount();

            assertEquals(statementsForOneRow, statementsForTwoRows,
                    "ilanın atama listesi satır sayısıyla büyüyen sorgu atıyor (N+1)");
            assertEquals(0, fetchesForOneRow, "atama listesi satır başına lazy entity yüklüyor");
            assertEquals(0, statistics.getEntityFetchCount(), "atama listesi satır başına lazy entity yüklüyor");

            statistics.clear();
            mockMvc.perform(get("/users/me/assignments").param("asEmployer", "true").param("size", "2")
                            .with(user(principal(employerId))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(2)));
            assertEquals(0, statistics.getEntityFetchCount(), "atamalarım listesi satır başına lazy entity yüklüyor");
        } finally {
            statistics.setStatisticsEnabled(wasEnabled);
        }
    }

    private void linkApplication() {
        jdbc.update("insert into job_applications (id, created_at, is_deleted, job_id, user_id, status, "
                        + "application_date_time, answered_date_time) "
                        + "values (?, now(), false, ?, ?, 'ACCEPTED', now() - interval '3 days', now() - interval '2 days')",
                applicationId, jobId, workerId);
        jdbc.update("update job_assignments set job_application_id = ? where id = ?", applicationId, assignmentId);
    }

    private void insertSucceededPayment() {
        jdbc.update("insert into payments (id, created_at, is_deleted, job_assignment_id, conversation_id, status, "
                        + "currency, verification_attempts, completed_at) "
                        + "values (?, now() - interval '1 hour', false, ?, ?, 'SUCCEEDED', 'TRY', 1, "
                        + "now() - interval '1 hour')",
                paymentId, assignmentId, "conv-" + paymentId);
    }

    private void insertCancellation() {
        jdbc.update("insert into cancellations (id, created_at, is_deleted, job_assignment_id, cancelled_by_id, "
                        + "cancelled_by_party, reason, notice_minutes, fee_rate, fee_amount, refund_amount, cancelled_at) "
                        + "values (?, now(), false, ?, ?, 'EMPLOYER', 'Plan değişti', 120, 0.2500, 27.50, 82.50, now())",
                cancellationId, assignmentId, employerId);
    }

    private void insertPayout() {
        jdbc.update("insert into payouts (id, created_at, is_deleted, job_assignment_id, worker_id, amount, "
                        + "currency, status, provider_reference, settlement_reference, processing_at, completed_at) "
                        + "values (?, now(), false, ?, ?, 100.00, 'TRY', 'COMPLETED', ?, ?, now(), now())",
                payoutId, assignmentId, workerId, "iyz-" + payoutId, "stl-" + payoutId);
    }

    private void insertReview(UUID id, UUID authorId, UUID subjectId, int rating, String comment) {
        jdbc.update("insert into reviews (id, created_at, is_deleted, job_assignment_id, author_id, subject_id, "
                        + "rating, comment, anonymous) values (?, now(), false, ?, ?, ?, ?, ?, false)",
                id, assignmentId, authorId, subjectId, rating, comment);
    }

    private void insertSecondAssignment() {
        insertUser(secondWorkerId, false);
        jdbc.update("insert into job_assignments (id, created_at, is_deleted, job_id, worker_id, status, "
                        + "payment_status, assignment_date_time, work_hours, worker_payout, commission_rate, "
                        + "commission_amount, total_charged, pricing_version, currency) "
                        + "values (?, now(), false, ?, ?, 'PENDING_CONFIRMATION', null, now() + interval '1 hour', "
                        + "0, 100.00, 0.1000, 10.00, 110.00, 'v1', 'TRY')",
                secondAssignmentId, jobId, secondWorkerId);
    }

    private void attachCompanyLogo() {
        jdbc.update("insert into media (id, media_type, file_name, file_type, file_url, file_size, created_at, "
                        + "is_deleted) values (?, 'IMAGE', 'logo.png', 'image/png', ?, 10, now(), false)",
                logoId, "logos/" + logoId + ".png");
        jdbc.update("update companies set logo_id = ? where id = ?", logoId, companyId);
    }

    private void attachJobCover() {
        jdbc.update("insert into media (id, media_type, file_name, file_type, file_url, file_size, created_at, "
                        + "is_deleted) values (?, 'IMAGE', 'cover.jpg', 'image/jpeg', ?, 10, now(), false)",
                coverId, "covers/" + coverId + ".jpg");
        jdbc.update("insert into job_images (id, created_at, is_deleted, job_id, image_id, sort_order, is_cover) "
                        + "values (?, now(), false, ?, ?, 0, true)",
                coverEntryId, jobId, coverId);
    }

    @Test
    void theWorkerAndTheEmployerReadTheSameAssignmentDto() throws Exception {
        for (UUID actorId : new UUID[]{workerId, employerId}) {
            getAssignmentAs(actorId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(assignmentId.toString()))
                    .andExpect(jsonPath("$.data.job.id").value(jobId.toString()))
                    .andExpect(jsonPath("$.data.worker.id").value(workerId.toString()))
                    .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
        }

        getAssignmentAs(workerId)
                .andExpect(jsonPath("$.data.job.addressPrecision").value("EXACT"))
                .andExpect(jsonPath("$.data.job.latitude").value(40.987654));
    }

    @Test
    void theWorkerSeesOnlyTheirPayoutWhileTheEmployerSideSeesThePricingBreakdown() throws Exception {
        getAssignmentAs(workerId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workerPayout").value(100.00))
                .andExpect(jsonPath("$.data.totalCharged").value(nullValue()))
                .andExpect(jsonPath("$.data.pricing").value(nullValue()));

        for (UUID actorId : new UUID[]{employerId, companyAdminId}) {
            getAssignmentAs(actorId)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.workerPayout").value(100.00))
                    .andExpect(jsonPath("$.data.totalCharged").value(110.00))
                    .andExpect(jsonPath("$.data.pricing.workerPayout").value("100.00"))
                    .andExpect(jsonPath("$.data.pricing.serviceFeeRate").value("0.1000"))
                    .andExpect(jsonPath("$.data.pricing.serviceFeeAmount").value("10.00"))
                    .andExpect(jsonPath("$.data.pricing.employerTotal").value("110.00"));
        }

        mockMvc.perform(get("/users/me/assignments").with(user(principal(workerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].workerPayout").value(100.00))
                .andExpect(jsonPath("$.data.content[0].totalCharged").value(nullValue()))
                .andExpect(jsonPath("$.data.content[0].pricing").value(nullValue()));
        mockMvc.perform(get("/users/me/assignments").param("asEmployer", "true").with(user(principal(employerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].totalCharged").value(110.00))
                .andExpect(jsonPath("$.data.content[0].pricing.serviceFeeAmount").value("10.00"));
        mockMvc.perform(get("/jobs/{jobId}/assignments", jobId).with(user(principal(companyAdminId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].totalCharged").value(110.00))
                .andExpect(jsonPath("$.data.content[0].pricing.employerTotal").value("110.00"));
    }

    @Test
    void respondingAsTheWorkerAnswersWithTheWorkerViewOfThePrice() throws Exception {
        jdbc.update("update job_assignments set status = 'PENDING_CONFIRMATION', payment_status = null where id = ?",
                assignmentId);

        mockMvc.perform(post("/job-assignments/{assignmentId}/respond", assignmentId)
                        .with(user(principal(workerId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accepted\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workerPayout").value(100.00))
                .andExpect(jsonPath("$.data.totalCharged").value(nullValue()))
                .andExpect(jsonPath("$.data.pricing").value(nullValue()))
                .andExpect(jsonPath("$.data.myReview").value(nullValue()))
                .andExpect(jsonPath("$.data.counterpartyReviewed").value(false));
    }

    @Test
    void myReviewIsTheViewersOwnAndCounterpartyReviewedLooksTheOtherWay() throws Exception {
        jdbc.update("update job_assignments set status = 'COMPLETED', payment_status = 'RELEASED_TO_WORKER' "
                + "where id = ?", assignmentId);

        for (UUID actorId : new UUID[]{workerId, employerId, companyAdminId}) {
            getAssignmentAs(actorId)
                    .andExpect(jsonPath("$.data.myReview").value(nullValue()))
                    .andExpect(jsonPath("$.data.counterpartyReviewed").value(false));
        }

        String body = mockMvc.perform(post("/job-assignments/{assignmentId}/reviews", assignmentId)
                        .with(user(principal(workerId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":4,\"comment\":\"Net brief\",\"anonymous\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.jobAssignmentId").value(assignmentId.toString()))
                .andExpect(jsonPath("$.data.author").value(nullValue()))
                .andReturn().getResponse().getContentAsString();
        String reviewId = com.jayway.jsonpath.JsonPath.read(body, "$.data.id");

        getAssignmentAs(workerId)
                .andExpect(jsonPath("$.data.myReview.id").value(reviewId))
                .andExpect(jsonPath("$.data.myReview.rating").value(4))
                .andExpect(jsonPath("$.data.myReview.comment").value("Net brief"))
                .andExpect(jsonPath("$.data.myReview.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.counterpartyReviewed").value(false));
        for (UUID actorId : new UUID[]{employerId, companyAdminId}) {
            getAssignmentAs(actorId)
                    .andExpect(jsonPath("$.data.myReview").value(nullValue()))
                    .andExpect(jsonPath("$.data.counterpartyReviewed").value(true));
        }

        UUID employerReviewId = UUID.randomUUID();
        insertReview(employerReviewId, employerId, workerId, 5, "Dakik");

        getAssignmentAs(employerId)
                .andExpect(jsonPath("$.data.myReview.id").value(employerReviewId.toString()))
                .andExpect(jsonPath("$.data.myReview.rating").value(5))
                .andExpect(jsonPath("$.data.counterpartyReviewed").value(true));
        getAssignmentAs(companyAdminId)
                .andExpect(jsonPath("$.data.myReview").value(nullValue()))
                .andExpect(jsonPath("$.data.counterpartyReviewed").value(true));
        getAssignmentAs(workerId)
                .andExpect(jsonPath("$.data.myReview.id").value(reviewId))
                .andExpect(jsonPath("$.data.counterpartyReviewed").value(true));

        mockMvc.perform(get("/users/me/assignments").with(user(principal(workerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].myReview.id").value(reviewId))
                .andExpect(jsonPath("$.data.content[0].counterpartyReviewed").value(true));

        mockMvc.perform(get("/users/{userId}/reviews", employerId).with(user(principal(outsiderId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id").value(reviewId))
                .andExpect(jsonPath("$.data.content[0].jobAssignmentId").value(nullValue()))
                .andExpect(jsonPath("$.data.content[0].author").value(nullValue()));
    }

    @Test
    void theAssignmentTellsTheClientWhetherMessagingIsOpenAndWhyNot() throws Exception {
        getAssignmentAs(workerId)
                .andExpect(jsonPath("$.data.messagingAvailable").value(true))
                .andExpect(jsonPath("$.data.messagingAvailableReason").value("AVAILABLE"));

        jdbc.update("update job_assignments set payment_status = 'PENDING_COLLECTION' where id = ?", assignmentId);
        getAssignmentAs(employerId)
                .andExpect(jsonPath("$.data.messagingAvailable").value(false))
                .andExpect(jsonPath("$.data.messagingAvailableReason").value("PAYMENT_REQUIRED"));

        jdbc.update("update job_assignments set payment_status = 'REFUNDED', status = 'CANCELLED' where id = ?",
                assignmentId);
        getAssignmentAs(workerId)
                .andExpect(jsonPath("$.data.messagingAvailable").value(false))
                .andExpect(jsonPath("$.data.messagingAvailableReason").value("CLOSED"));

        mockMvc.perform(get("/users/me/assignments").with(user(principal(workerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].messagingAvailable").value(false))
                .andExpect(jsonPath("$.data.content[0].messagingAvailableReason").value("CLOSED"));
    }

    @Test
    void aCompanyAdminReadsTheCompanyAssignment() throws Exception {
        getAssignmentAs(companyAdminId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(assignmentId.toString()))
                .andExpect(jsonPath("$.data.job.companyName").value("Atama Şirketi " + companyId))
                .andExpect(jsonPath("$.data.job.companyId").value(companyId.toString()))
                .andExpect(jsonPath("$.data.job.addressPrecision").value("APPROXIMATE"))
                .andExpect(jsonPath("$.data.job.latitude").value(40.99));
    }

    @Test
    void anOutsiderGetsTheConcealedNotFoundResponse() throws Exception {
        getAssignmentAs(outsiderId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_ASSIGNMENT_NOT_FOUND"));
    }

    @Test
    void anUnknownAssignmentGetsTheSameNotFoundResponse() throws Exception {
        mockMvc.perform(get("/job-assignments/{assignmentId}", UUID.randomUUID())
                        .with(user(principal(workerId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_ASSIGNMENT_NOT_FOUND"));
    }

    private org.springframework.test.web.servlet.ResultActions getAssignmentAs(UUID actorId) throws Exception {
        return mockMvc.perform(get("/job-assignments/{assignmentId}", assignmentId)
                .with(user(principal(actorId))));
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
