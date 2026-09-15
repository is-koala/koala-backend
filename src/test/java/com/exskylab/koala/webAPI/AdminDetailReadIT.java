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

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AdminDetailReadIT extends AbstractStaffIntegrationTest {

    private static final String FULL_IBAN = "TR180006200119000006672315";
    private static final String IBAN_BODY = "620011900000";
    private static final String PROVIDER_TOKEN = "checkout-token-must-never-leave-the-server";
    private static final String SUBMERCHANT_KEY = "submerchant-key-must-never-leave-the-server";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final UUID employerId = UUID.randomUUID();
    private final UUID workerId = UUID.randomUUID();
    private final UUID strangerId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID addressId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final UUID companyJobId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();
    private final UUID failedPaymentId = UUID.randomUUID();
    private final UUID paymentId = UUID.randomUUID();
    private final UUID refundId = UUID.randomUUID();
    private final UUID bankAccountId = UUID.randomUUID();
    private final UUID payoutId = UUID.randomUUID();
    private final UUID settlementTaskId = UUID.randomUUID();
    private final UUID disputeId = UUID.randomUUID();
    private final UUID reportId = UUID.randomUUID();
    private final UUID unrelatedReportId = UUID.randomUUID();
    private final UUID otherJobId = UUID.randomUUID();
    private final UUID otherJobReportId = UUID.randomUUID();
    private final UUID applicationId = UUID.randomUUID();
    private final UUID workSessionId = UUID.randomUUID();
    private final UUID reviewId = UUID.randomUUID();
    private final UUID cancelledAssignmentId = UUID.randomUUID();
    private final UUID cancelledPaymentId = UUID.randomUUID();
    private final UUID cancellationRefundId = UUID.randomUUID();
    private final UUID cancellationId = UUID.randomUUID();

    private String employerEmail;
    private String workerEmail;

    @BeforeEach
    void seed() {
        employerEmail = "detail-employer-" + employerId + "@example.com";
        workerEmail = "detail-worker-" + workerId + "@example.com";
        insertUser(employerId, employerEmail, true, "Şükrü", "Öztürk");
        insertUser(workerId, workerEmail, false, "Işıl", "Çağlayan");
        insertUser(strangerId, "detail-stranger-" + strangerId + "@example.com", false, "Yabancı", "Kişi");

        jdbcTemplate.update("insert into companies (id, created_at, is_deleted, name, tax_number, email, approval_status) "
                        + "values (?, now(), false, ?, ?, ?, 'APPROVED')",
                companyId, "Detay Lojistik " + companyId, String.valueOf(Math.abs(companyId.hashCode())),
                "detail-company-" + companyId + "@example.com");

        UUID countryId = jdbcTemplate.queryForObject("select id from countries limit 1", UUID.class);
        UUID cityId = jdbcTemplate.queryForObject("select id from cities where country_id = ? limit 1",
                UUID.class, countryId);
        UUID districtId = jdbcTemplate.queryForObject("select id from districts where city_id = ? limit 1",
                UUID.class, cityId);
        jdbcTemplate.update("""
                insert into addresses
                    (id, country_id, city_id, district_id, neighborhood, thoroughfare,
                     building_number, unit_number, postal_code, additional_directions,
                     latitude, longitude, canonical_version, needs_review, created_at, is_deleted)
                values (?, ?, ?, ?, 'Ev Mahallesi', 'Gizli Sokak', '99', '8', '34000',
                        'Mavi kapı', 41.123456, 29.654321, 1, false, now(), false)
                """, addressId, countryId, cityId, districtId);

        jdbcTemplate.update("""
                insert into jobs (id, created_at, is_deleted, creator_id, address_id, title, status, employer_type,
                                  pricing_version, currency, worker_payout, commission_rate, commission_amount,
                                  total_charged, insurance_required, address_public, experience_required,
                                  min_age, max_age, headcount)
                values (?, now(), false, ?, ?, 'Depo görevlisi', 'OPEN', 'INDIVIDUAL', '2026-09-v1', 'TRY',
                        1000.00, 0.0750, 75.00, 1075.00, false, false, false, 18, 65, 2)
                """, jobId, employerId, addressId);
        jdbcTemplate.update("""
                insert into jobs (id, created_at, is_deleted, creator_id, company_id, title, status, employer_type,
                                  insurance_required, address_public, experience_required, min_age, max_age)
                values (?, now(), false, ?, ?, 'Forklift operatörü', 'CLOSED', 'COMPANY',
                        false, true, false, 18, 65)
                """, companyJobId, employerId, companyId);

        jdbcTemplate.update("""
                insert into job_assignments (id, created_at, is_deleted, job_id, worker_id, status, payment_status,
                                             assignment_date_time, work_hours, pricing_version, currency,
                                             worker_payout, commission_rate, commission_amount, total_charged)
                values (?, now(), false, ?, ?, 'COMPLETED', 'RELEASED_TO_WORKER', now(), 8, '2026-09-v1', 'TRY',
                        1000.00, 0.0750, 75.00, 1075.00)
                """, assignmentId, jobId, workerId);
        jdbcTemplate.update("""
                insert into job_applications (id, created_at, is_deleted, job_id, user_id, status,
                                              application_date_time, answered_date_time)
                values (?, now() - interval '3 days', false, ?, ?, 'ACCEPTED', now() - interval '3 days',
                        now() - interval '2 days')
                """, applicationId, jobId, workerId);
        jdbcTemplate.update("update job_assignments set job_application_id = ?, notes = 'Kapıdan giriş', "
                + "expected_completion_date_time = now() + interval '1 day' where id = ?", applicationId, assignmentId);
        jdbcTemplate.update("""
                insert into work_sessions (id, created_at, is_deleted, job_assignment_id, check_in_at, check_out_at,
                                           check_in_plausibility_flags, check_out_plausibility_flags, minutes)
                values (?, now(), false, ?, now() - interval '9 hours', now() - interval '1 hour',
                        'OUTSIDE_JOB_WINDOW', '', 480)
                """, workSessionId, assignmentId);
        jdbcTemplate.update("""
                insert into reviews (id, created_at, is_deleted, job_assignment_id, author_id, subject_id, rating,
                                     comment, anonymous)
                values (?, now(), false, ?, ?, ?, 4, 'Dakik ve düzenli', false)
                """, reviewId, assignmentId, employerId, workerId);


        jdbcTemplate.update("""
                insert into payments (id, created_at, is_deleted, job_assignment_id, conversation_id, status,
                                      currency, pricing_version, worker_payout, commission_rate,
                                      commission_amount, total_charged, failure_reason, verification_attempts)
                values (?, now() - interval '2 hours', false, ?, ?, 'FAILED', 'TRY', '2026-09-v1', 1000.00, 0.0750,
                        75.00, 1075.00, 'Kart reddedildi', 0)
                """, failedPaymentId, assignmentId, "conv-failed-" + failedPaymentId);
        jdbcTemplate.update("""
                insert into payments (id, created_at, is_deleted, job_assignment_id, conversation_id, status,
                                      currency, pricing_version, worker_payout, commission_rate,
                                      commission_amount, total_charged, provider_payment_id,
                                      provider_transaction_id, provider_token, provider_submerchant_key,
                                      completed_at, verification_attempts)
                values (?, now() - interval '1 hour', false, ?, ?, 'SUCCEEDED', 'TRY', '2026-09-v1', 1000.00, 0.0750,
                        75.00, 1075.00, 'iyz-payment-4711', 'iyz-transaction-4711', ?, ?, now(), 1)
                """, paymentId, assignmentId, "conv-ok-" + paymentId, PROVIDER_TOKEN, SUBMERCHANT_KEY);
        jdbcTemplate.update("""
                insert into refunds (id, created_at, is_deleted, payment_id, amount, currency, status, reason,
                                     provider_refund_id)
                values (?, now(), false, ?, 100.00, 'TRY', 'COMPLETED', 'Kısmi iade', 'iyz-refund-1')
                """, refundId, paymentId);

        jdbcTemplate.update("insert into bank_accounts (id, created_at, is_deleted, user_id, iban, is_primary, "
                        + "is_active, verification_status, verification_attempts, account_holder_name_verified) "
                        + "values (?, now(), false, ?, ?, true, true, 'VERIFIED', 0, true)",
                bankAccountId, workerId, FULL_IBAN);
        jdbcTemplate.update("""
                insert into payouts (id, created_at, is_deleted, job_assignment_id, payment_id, worker_id,
                                     bank_account_id, amount, currency, status, provider_reference, processing_at)
                values (?, now(), false, ?, ?, ?, ?, 1000.00, 'TRY', 'PROCESSING', 'iyz-transaction-4711', now())
                """, payoutId, assignmentId, paymentId, workerId, bankAccountId);
        jdbcTemplate.update("""
                insert into settlement_tasks (id, created_at, is_deleted, payment_id, job_assignment_id, operation,
                                              provider_transaction_id, status, attempts, next_attempt_at,
                                              last_error, completed_at, provider_reference)
                values (?, now(), false, ?, ?, 'APPROVE', 'iyz-transaction-4711', 'SUCCEEDED', 2, now(),
                        'İlk deneme zaman aşımı', now(), 'iyz-transaction-4711')
                """, settlementTaskId, paymentId, assignmentId);

        jdbcTemplate.update("insert into disputes (id, created_at, is_deleted, job_assignment_id, opened_by_id, "
                        + "status, reason) values (?, now(), false, ?, ?, 'OPEN', ?)",
                disputeId, assignmentId, workerId, "Ücret eksik ödendi");
        jdbcTemplate.update("""
                insert into reports (id, created_at, is_deleted, reporter_id, reported_user_id, job_id,
                                     reason, description, status)
                values (?, now(), false, ?, ?, ?, 'FRAUD', 'atama detayı testi', 'OPEN')
                """, reportId, workerId, employerId, jobId);
        jdbcTemplate.update("""
                insert into reports (id, created_at, is_deleted, reporter_id, reported_user_id, job_id,
                                     reason, description, status)
                values (?, now(), false, ?, ?, ?, 'FRAUD', 'ilgisiz şikayet', 'OPEN')
                """, unrelatedReportId, strangerId, employerId, jobId);
        jdbcTemplate.update("""
                insert into jobs (id, created_at, is_deleted, creator_id, title, status, employer_type,
                                  insurance_required, address_public, experience_required, min_age, max_age)
                values (?, now(), false, ?, 'Başka ilan', 'OPEN', 'INDIVIDUAL', false, false, false, 18, 65)
                """, otherJobId, employerId);
        jdbcTemplate.update("""
                insert into job_assignments (id, created_at, is_deleted, job_id, worker_id, status, payment_status,
                                             assignment_date_time, work_hours, pricing_version, currency,
                                             worker_payout, commission_rate, commission_amount, total_charged)
                values (?, now(), false, ?, ?, 'CANCELLED', 'REFUNDED', now(), 8, '2026-09-v1', 'TRY',
                        1000.00, 0.0750, 75.00, 1075.00)
                """, cancelledAssignmentId, otherJobId, workerId);
        jdbcTemplate.update("""
                insert into payments (id, created_at, is_deleted, job_assignment_id, conversation_id, status,
                                      currency, pricing_version, worker_payout, commission_rate,
                                      commission_amount, total_charged, completed_at, verification_attempts)
                values (?, now(), false, ?, ?, 'SUCCEEDED', 'TRY', '2026-09-v1', 1000.00, 0.0750, 75.00, 1075.00,
                        now(), 1)
                """, cancelledPaymentId, cancelledAssignmentId, "conv-cancelled-" + cancelledPaymentId);
        jdbcTemplate.update("""
                insert into refunds (id, created_at, is_deleted, payment_id, amount, currency, status, reason)
                values (?, now(), false, ?, 1075.00, 'TRY', 'COMPLETED', 'İşveren iptal etti')
                """, cancellationRefundId, cancelledPaymentId);
        jdbcTemplate.update("""
                insert into cancellations (id, created_at, is_deleted, job_assignment_id, cancelled_by_id,
                                           cancelled_by_party, reason, notice_minutes, fee_rate, fee_amount,
                                           refund_amount, refund_id, cancelled_at)
                values (?, now(), false, ?, ?, 'EMPLOYER', 'Vardiya iptal', 2880, 0.0000, 0.00, 1075.00, ?, now())
                """, cancellationId, cancelledAssignmentId, employerId, cancellationRefundId);
        jdbcTemplate.update("""
                insert into reports (id, created_at, is_deleted, reporter_id, reported_user_id, job_id,
                                     reason, description, status)
                values (?, now(), false, ?, ?, ?, 'FRAUD', 'aynı taraflar, başka ilan', 'OPEN')
                """, otherJobReportId, workerId, employerId, otherJobId);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from admin_audit_entries");
        jdbcTemplate.update("delete from reports where id in (?, ?, ?)", reportId, unrelatedReportId,
                otherJobReportId);
        jdbcTemplate.update("delete from disputes where id = ?", disputeId);
        jdbcTemplate.update("delete from cancellations where id = ?", cancellationId);
        jdbcTemplate.update("delete from refunds where id = ?", cancellationRefundId);
        jdbcTemplate.update("delete from payments where job_assignment_id = ?", cancelledAssignmentId);
        jdbcTemplate.update("delete from reviews where id = ?", reviewId);
        jdbcTemplate.update("delete from work_sessions where id = ?", workSessionId);
        jdbcTemplate.update("update job_assignments set job_application_id = null where id = ?", assignmentId);
        jdbcTemplate.update("delete from settlement_tasks where id = ?", settlementTaskId);
        jdbcTemplate.update("delete from payouts where id = ?", payoutId);
        jdbcTemplate.update("delete from bank_accounts where id = ?", bankAccountId);
        jdbcTemplate.update("delete from refunds where id = ?", refundId);
        jdbcTemplate.update("delete from payments where job_assignment_id = ?", assignmentId);
        jdbcTemplate.update("delete from job_applications where job_id = ?", jobId);
        jdbcTemplate.update("delete from job_assignments where id in (?, ?)", assignmentId, cancelledAssignmentId);
        jdbcTemplate.update("delete from jobs where id in (?, ?, ?)", jobId, companyJobId, otherJobId);
        jdbcTemplate.update("delete from addresses where id = ?", addressId);
        jdbcTemplate.update("delete from companies where id = ?", companyId);
        jdbcTemplate.update("delete from users where id in (?, ?, ?)", employerId, workerId, strangerId);
        deleteStaffMembersLike("detail-read-%@iskoala.com");
    }

    private void insertUser(UUID id, String email, boolean employer, String firstName, String lastName) {
        jdbcTemplate.update("""
                insert into users (id, created_at, is_deleted, email, password, is_employer,
                                   email_verified, phone_verified, identity_verified, first_name, last_name)
                values (?, now(), false, ?, 'test', ?, true, false, true, ?, ?)
                """, id, email, employer, firstName, lastName);
    }

    private String tokenFor(String... roles) {
        return staffTokenFor("detail-read-" + UUID.randomUUID() + "@iskoala.com", "Okuyucu", roles);
    }

    private ResultActions read(String path, String token) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", "Bearer " + token));
    }

    private void assertRefusedIdentically(Function<UUID, String> path, UUID existing, String token)
            throws Exception {
        String real = normalise(read(path.apply(existing), token)
                .andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString(), existing);
        UUID random = UUID.randomUUID();
        String missing = normalise(read(path.apply(random), token)
                .andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString(), random);
        assertEquals(real, missing, "izinsiz personel var olan ile olmayan kaydı yanıttan ayırt edebiliyor");
    }

    private static String normalise(String body, UUID id) {
        return body.replace(id.toString(), "{id}")
                .replaceAll("\"timeStamp\":\"[^\"]*\"", "\"timeStamp\":\"-\"")
                .replaceAll("\"requestId\":\"[^\"]*\"", "\"requestId\":\"-\"");
    }

    @Test
    void aFinanceOfficerReadsThePayoutWithItsPartiesEvidenceAndHistory() throws Exception {
        read("/admin/payouts/" + payoutId, tokenFor("FINANCE_OFFICER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(payoutId.toString()))
                .andExpect(jsonPath("$.data.jobAssignmentId").value(assignmentId.toString()))
                .andExpect(jsonPath("$.data.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.data.jobTitle").value("Depo görevlisi"))
                .andExpect(jsonPath("$.data.paymentId").value(paymentId.toString()))
                .andExpect(jsonPath("$.data.worker.id").value(workerId.toString()))
                .andExpect(jsonPath("$.data.worker.email").value(workerEmail))
                .andExpect(jsonPath("$.data.employer.id").value(employerId.toString()))
                .andExpect(jsonPath("$.data.employer.email").value(employerEmail))
                .andExpect(jsonPath("$.data.maskedIban").value("TR18***2315"))
                .andExpect(jsonPath("$.data.amount").value("1000.00"))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.providerReference").value("iyz-transaction-4711"))
                .andExpect(jsonPath("$.data.settlementTasks", hasSize(1)))
                .andExpect(jsonPath("$.data.settlementTasks[0].id").value(settlementTaskId.toString()))
                .andExpect(jsonPath("$.data.settlementTasks[0].operation").value("APPROVE"))
                .andExpect(jsonPath("$.data.settlementTasks[0].attempts").value(2))
                .andExpect(jsonPath("$.data.settlementTasks[0].lastError").value("İlk deneme zaman aşımı"))
                .andExpect(jsonPath("$.data.paymentAttempts", hasSize(2)))
                .andExpect(jsonPath("$.data.paymentAttempts[*].id", hasItem(paymentId.toString())))
                .andExpect(jsonPath("$.data.paymentAttempts[*].status", hasItem("FAILED")))
                .andExpect(content().string(not(containsString(FULL_IBAN))))
                .andExpect(content().string(not(containsString(IBAN_BODY))));
    }

    @Test
    void aFinanceAnalystIsRefusedThePayoutDetailWhetherOrNotItExists() throws Exception {
        assertRefusedIdentically(id -> "/admin/payouts/" + id, payoutId, tokenFor("FINANCE_ANALYST"));
    }

    @Test
    void aFinanceOfficerReachesAMissingPayout() throws Exception {
        read("/admin/payouts/" + UUID.randomUUID(), tokenFor("FINANCE_OFFICER"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PAYOUT_NOT_FOUND"));
    }

    @Test
    void aFinanceAnalystListsTheAttemptsOfAnAssignmentWithoutCardDataOrTokens() throws Exception {
        read("/admin/job-assignments/" + assignmentId + "/payments", tokenFor("FINANCE_ANALYST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].id").value(paymentId.toString()))
                .andExpect(jsonPath("$.data[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data[0].jobAssignmentId").value(assignmentId.toString()))
                .andExpect(jsonPath("$.data[0].worker.id").value(workerId.toString()))
                .andExpect(jsonPath("$.data[0].worker.firstName").value("Işıl"))
                .andExpect(jsonPath("$.data[0].employer.id").value(employerId.toString()))
                .andExpect(jsonPath("$.data[0].worker.email").doesNotExist())
                .andExpect(jsonPath("$.data[0].totalCharged").value("1075.00"))
                .andExpect(jsonPath("$.data[0].pricing.employerTotal").value("1075.00"))
                .andExpect(jsonPath("$.data[0].providerPaymentId").value("iyz-payment-4711"))
                .andExpect(jsonPath("$.data[0].providerTransactionId").value("iyz-transaction-4711"))
                .andExpect(jsonPath("$.data[0].conversationId").value("conv-ok-" + paymentId))
                .andExpect(jsonPath("$.data[0].refunds", hasSize(1)))
                .andExpect(jsonPath("$.data[0].refunds[0].id").value(refundId.toString()))
                .andExpect(jsonPath("$.data[0].refunds[0].amount").value("100.00"))
                .andExpect(jsonPath("$.data[0].refunds[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data[1].id").value(failedPaymentId.toString()))
                .andExpect(jsonPath("$.data[1].failureReason").value("Kart reddedildi"))
                .andExpect(jsonPath("$.data[1].refunds", hasSize(0)))
                .andExpect(jsonPath("$.data[0].providerToken").doesNotExist())
                .andExpect(content().string(not(containsString(PROVIDER_TOKEN))))
                .andExpect(content().string(not(containsString(SUBMERCHANT_KEY))))
                .andExpect(content().string(not(containsString(workerEmail))))
                .andExpect(content().string(not(containsString(employerEmail))));
    }

    @Test
    void aFinanceAnalystReadsASinglePaymentAttempt() throws Exception {
        read("/admin/payments/" + paymentId, tokenFor("FINANCE_ANALYST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(paymentId.toString()))
                .andExpect(jsonPath("$.data.jobId").value(jobId.toString()))
                .andExpect(jsonPath("$.data.jobTitle").value("Depo görevlisi"))
                .andExpect(jsonPath("$.data.worker.id").value(workerId.toString()))
                .andExpect(jsonPath("$.data.employer.firstName").value("Şükrü"))
                .andExpect(jsonPath("$.data.workerPayout").value("1000.00"))
                .andExpect(jsonPath("$.data.commissionAmount").value("75.00"))
                .andExpect(jsonPath("$.data.verificationAttempts").value(1))
                .andExpect(jsonPath("$.data.refunds[0].providerRefundId").value("iyz-refund-1"))
                .andExpect(jsonPath("$.data.employer.email").doesNotExist())
                .andExpect(content().string(not(containsString(PROVIDER_TOKEN))))
                .andExpect(content().string(not(containsString(SUBMERCHANT_KEY))))
                .andExpect(content().string(not(containsString(workerEmail))))
                .andExpect(content().string(not(containsString(employerEmail))));
    }

    @Test
    void aDisputeHandlerIsRefusedThePaymentSurfacesWhetherOrNotTheyExist() throws Exception {
        String token = tokenFor("DISPUTE_HANDLER");
        assertRefusedIdentically(id -> "/admin/payments/" + id, paymentId, token);
        assertRefusedIdentically(id -> "/admin/job-assignments/" + id + "/payments", assignmentId, token);
    }

    @Test
    void aFinanceAnalystReachesAMissingPaymentAndAMissingAssignment() throws Exception {
        String token = tokenFor("FINANCE_ANALYST");
        read("/admin/payments/" + UUID.randomUUID(), token)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_NOT_FOUND"));
        read("/admin/job-assignments/" + UUID.randomUUID() + "/payments", token)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_ASSIGNMENT_NOT_FOUND"));
    }

    @Test
    void theAssignmentDetailSummarisesItsPaymentsPayoutDisputesAndReports() throws Exception {
        read("/admin/job-assignments/" + assignmentId, tokenFor("FINANCE_ANALYST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(assignmentId.toString()))
                .andExpect(jsonPath("$.data.jobTitle").value("Depo görevlisi"))
                .andExpect(jsonPath("$.data.pricing.employerTotal").value("1075.00"))
                .andExpect(jsonPath("$.data.payments", hasSize(2)))
                .andExpect(jsonPath("$.data.payments[0].id").value(paymentId.toString()))
                .andExpect(jsonPath("$.data.payments[0].status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.payout.id").value(payoutId.toString()))
                .andExpect(jsonPath("$.data.payout.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.disputes", hasSize(1)))
                .andExpect(jsonPath("$.data.disputes[0].id").value(disputeId.toString()))
                .andExpect(jsonPath("$.data.disputes[0].status").value("OPEN"))
                .andExpect(jsonPath("$.data.reports", hasSize(1)))
                .andExpect(jsonPath("$.data.reports[0].id").value(reportId.toString()))
                .andExpect(jsonPath("$.data.reports[0].status").value("OPEN"))
                .andExpect(jsonPath("$.data.transport").value(nullValue()))
                .andExpect(content().string(not(containsString(unrelatedReportId.toString()))))
                .andExpect(content().string(not(containsString(otherJobReportId.toString()))));
    }

    @Test
    void aSupportAgentListsJobsFilteredByStatusAndSearchedByEmployerEmail() throws Exception {
        String token = tokenFor("SUPPORT_AGENT");

        read("/admin/jobs?query=" + employerEmail, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(3)))
                .andExpect(jsonPath("$.data.content[*].id", hasItem(jobId.toString())))
                .andExpect(jsonPath("$.data.content[*].id", hasItem(companyJobId.toString())))
                .andExpect(jsonPath("$.data.content[*].id", hasItem(otherJobId.toString())))
                .andExpect(jsonPath("$.data.content[*].employer.email", hasItem(employerEmail)));

        read("/admin/jobs?status=CLOSED&query=" + employerEmail, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id").value(companyJobId.toString()))
                .andExpect(jsonPath("$.data.content[0].status").value("CLOSED"));

        read("/admin/jobs?status=OPEN&query=depo görevlisi", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].id", hasItem(jobId.toString())))
                .andExpect(jsonPath("$.data.content[*].id", not(hasItem(companyJobId.toString()))))
                .andExpect(jsonPath("$.data.content[?(@.id == '" + jobId + "')].applicationCount").value(1))
                .andExpect(jsonPath("$.data.content[?(@.id == '" + jobId + "')].assignmentCount").value(1))
                .andExpect(jsonPath("$.data.content[?(@.id == '" + jobId + "')].employer.id")
                        .value(employerId.toString()))
                .andExpect(jsonPath("$.data.content[?(@.id == '" + jobId + "')].pricing.employerTotal")
                        .value("1075.00"));

        read("/admin/jobs?query=detay lojistik " + companyId, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id").value(companyJobId.toString()))
                .andExpect(jsonPath("$.data.content[0].companyId").value(companyId.toString()));

        read("/admin/jobs?query=sukru ozturk", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].id", hasItem(jobId.toString())));

        read("/admin/jobs?query=" + "x".repeat(101), token)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("ADMIN_SEARCH_QUERY_TOO_LONG"));
    }

    @Test
    void aSupportAgentReadsTheJobWithItsFullAddressCountsAndImages() throws Exception {
        read("/admin/jobs/" + jobId, tokenFor("SUPPORT_AGENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(jobId.toString()))
                .andExpect(jsonPath("$.data.title").value("Depo görevlisi"))
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.employer.email").value(employerEmail))
                .andExpect(jsonPath("$.data.company").value(nullValue()))
                .andExpect(jsonPath("$.data.addressPublic").value(false))
                .andExpect(jsonPath("$.data.address.components.thoroughfare").value("Gizli Sokak"))
                .andExpect(jsonPath("$.data.address.components.buildingNumber").value("99"))
                .andExpect(jsonPath("$.data.address.components.unitNumber").value("8"))
                .andExpect(jsonPath("$.data.address.components.postalCode").value("34000"))
                .andExpect(jsonPath("$.data.address.position.latitude").value(41.123456))
                .andExpect(jsonPath("$.data.applicationCount").value(1))
                .andExpect(jsonPath("$.data.assignmentCount").value(1))
                .andExpect(jsonPath("$.data.headcount").value(2))
                .andExpect(jsonPath("$.data.images", hasSize(0)))
                .andExpect(jsonPath("$.data.pricing.employerTotal").value("1075.00"));
    }

    @Test
    void theJobSurfacesAreRefusedWithoutJobReadWhetherOrNotTheJobExists() throws Exception {
        for (String role : List.of("FINANCE_ANALYST", "CATALOG_EDITOR", "AUDITOR")) {
            String token = tokenFor(role);
            String real = normalise(read("/admin/jobs/" + jobId, token)
                    .andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString(), jobId);
            UUID random = UUID.randomUUID();
            String missing = normalise(read("/admin/jobs/" + random, token)
                    .andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString(), random);
            assertEquals(real, missing, role + " var olan ile olmayan ilanı yanıttan ayırt edebiliyor");
            read("/admin/jobs", token).andExpect(status().isForbidden());
        }
    }

    @Test
    void aSupportAgentReachesAMissingJob() throws Exception {
        read("/admin/jobs/" + UUID.randomUUID(), tokenFor("SUPPORT_AGENT"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_NOT_FOUND"));
    }

    @Test
    void theJobListIsBuiltWithoutAQueryPerRow() throws Exception {
        String token = tokenFor("SUPPORT_AGENT");
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean wasEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            read("/admin/jobs?size=1&query=" + employerEmail, token).andExpect(status().isOk());
            long statementsForOneRow = statistics.getPrepareStatementCount();
            long fetchesForOneRow = statistics.getEntityFetchCount();

            statistics.clear();
            read("/admin/jobs?size=2&query=" + employerEmail, token)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(2)));
            long statementsForTwoRows = statistics.getPrepareStatementCount();

            assertEquals(statementsForOneRow, statementsForTwoRows,
                    "ilan listesi satır sayısıyla büyüyen sorgu atıyor (N+1)");
            assertEquals(0, fetchesForOneRow, "ilan listesi satır başına lazy entity yüklüyor");
            assertEquals(0, statistics.getEntityFetchCount(), "ilan listesi satır başına lazy entity yüklüyor");
        } finally {
            statistics.setStatisticsEnabled(wasEnabled);
        }
    }

    @Test
    void thePaymentListIsBuiltWithoutAQueryPerAttempt() throws Exception {
        String token = tokenFor("FINANCE_ANALYST");
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean wasEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            read("/admin/job-assignments/" + assignmentId + "/payments", token).andExpect(status().isOk());
            long statementsForTwoAttempts = statistics.getPrepareStatementCount();

            UUID third = UUID.randomUUID();
            jdbcTemplate.update("""
                    insert into payments (id, created_at, is_deleted, job_assignment_id, conversation_id, status,
                                          currency, verification_attempts)
                    values (?, now() - interval '3 hours', false, ?, ?, 'FAILED', 'TRY', 0)
                    """, third, assignmentId, "conv-third-" + third);
            statistics.clear();
            read("/admin/job-assignments/" + assignmentId + "/payments", token)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data", hasSize(3)));
            long statementsForThreeAttempts = statistics.getPrepareStatementCount();

            assertEquals(statementsForTwoAttempts, statementsForThreeAttempts,
                    "ödeme denemeleri listesi deneme sayısıyla büyüyen sorgu atıyor (N+1)");
        } finally {
            statistics.setStatisticsEnabled(wasEnabled);
        }
    }

    @Test
    void theAssignmentDetailDrawsTheTimelineFromApplicationToReview() throws Exception {
        read("/admin/job-assignments/" + assignmentId, tokenFor("FINANCE_ANALYST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employerType").value("INDIVIDUAL"))
                .andExpect(jsonPath("$.data.notes").value("Kapıdan giriş"))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.expectedCompletionDateTime").isNotEmpty())
                .andExpect(jsonPath("$.data.application.id").value(applicationId.toString()))
                .andExpect(jsonPath("$.data.application.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.application.applicationDateTime").isNotEmpty())
                .andExpect(jsonPath("$.data.application.answeredDateTime").isNotEmpty())
                .andExpect(jsonPath("$.data.application.rejectionReason").value(nullValue()))
                .andExpect(jsonPath("$.data.workSessions", hasSize(1)))
                .andExpect(jsonPath("$.data.workSessions[0].id").value(workSessionId.toString()))
                .andExpect(jsonPath("$.data.workSessions[0].minutes").value(480))
                .andExpect(jsonPath("$.data.workSessions[0].checkInPlausibilityFlags[0]").value("OUTSIDE_JOB_WINDOW"))
                .andExpect(jsonPath("$.data.workSessions[0].checkOutPlausibilityFlags", hasSize(0)))
                .andExpect(jsonPath("$.data.cancellation").value(nullValue()))
                .andExpect(jsonPath("$.data.reviews", hasSize(1)))
                .andExpect(jsonPath("$.data.reviews[0].id").value(reviewId.toString()))
                .andExpect(jsonPath("$.data.reviews[0].authorId").value(employerId.toString()))
                .andExpect(jsonPath("$.data.reviews[0].subjectId").value(workerId.toString()))
                .andExpect(jsonPath("$.data.reviews[0].rating").value(4))
                .andExpect(jsonPath("$.data.reviews[0].comment").value("Dakik ve düzenli"));
    }

    @Test
    void aCancelledAssignmentCarriesItsCancellationAndTheRefundNamesIt() throws Exception {
        String token = tokenFor("FINANCE_ANALYST");

        read("/admin/job-assignments/" + cancelledAssignmentId, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.application").value(nullValue()))
                .andExpect(jsonPath("$.data.cancellation.id").value(cancellationId.toString()))
                .andExpect(jsonPath("$.data.cancellation.cancelledByParty").value("EMPLOYER"))
                .andExpect(jsonPath("$.data.cancellation.cancelledBy.id").value(employerId.toString()))
                .andExpect(jsonPath("$.data.cancellation.cancelledBy.firstName").value("Şükrü"))
                .andExpect(jsonPath("$.data.cancellation.reason").value("Vardiya iptal"))
                .andExpect(jsonPath("$.data.cancellation.noticeMinutes").value(2880))
                .andExpect(jsonPath("$.data.cancellation.feeAmount").value("0.00"))
                .andExpect(jsonPath("$.data.cancellation.refundAmount").value("1075.00"))
                .andExpect(jsonPath("$.data.cancellation.refundId").value(cancellationRefundId.toString()))
                .andExpect(jsonPath("$.data.cancellation.cancelledAt").isNotEmpty());

        read("/admin/job-assignments/" + cancelledAssignmentId + "/payments", token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].payoutId").value(nullValue()))
                .andExpect(jsonPath("$.data[0].refunds", hasSize(1)))
                .andExpect(jsonPath("$.data[0].refunds[0].origin").value("CANCELLATION"))
                .andExpect(jsonPath("$.data[0].refunds[0].originId").value(cancellationId.toString()));
    }

    @Test
    void aRefundWithoutACancellationIsAttributedToTheDisputeOrToStaff() throws Exception {
        String token = tokenFor("FINANCE_ANALYST");

        read("/admin/payments/" + paymentId, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payoutId").value(payoutId.toString()))
                .andExpect(jsonPath("$.data.refunds[0].origin").value("STAFF"))
                .andExpect(jsonPath("$.data.refunds[0].originId").value(nullValue()));
        read("/admin/payments/" + failedPaymentId, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payoutId").value(nullValue()));

        jdbcTemplate.update("update disputes set status = 'RESOLVED', resolution = 'REFUND_EMPLOYER', "
                + "resolved_at = now() where id = ?", disputeId);

        read("/admin/payments/" + paymentId, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refunds[0].origin").value("DISPUTE"))
                .andExpect(jsonPath("$.data.refunds[0].originId").value(disputeId.toString()));
    }

    @Test
    void thePayoutDetailNamesItsReleaseOriginBankAccountAndReconciler() throws Exception {
        String token = tokenFor("FINANCE_OFFICER");
        jdbcTemplate.update("update payouts set release_origin = 'AUTO_COMPLETION' where id = ?", payoutId);

        read("/admin/payouts/" + payoutId, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bankAccountId").value(bankAccountId.toString()))
                .andExpect(jsonPath("$.data.releaseOrigin").value("AUTO_COMPLETION"))
                .andExpect(jsonPath("$.data.settledAt").value(nullValue()))
                .andExpect(jsonPath("$.data.reconciledByStaffId").value(nullValue()));

        UUID reconciler = createStaffMember("detail-read-reconciler-" + UUID.randomUUID() + "@iskoala.com",
                "Mutabakatçı", "FINANCE_OFFICER");
        String settledAt = java.time.Instant.now().toString();
        mockMvc.perform(post("/admin/payouts/" + payoutId + "/reconcile")
                        .header("Authorization", "Bearer " + staffAccessToken(reconciler))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"settlementReference": "TRF-283-%s", "providerReference": "iyz-transaction-4711",
                                 "amount": 1000.00, "currency": "TRY", "settledAt": "%s"}
                                """.formatted(payoutId, settledAt)))
                .andExpect(status().isOk());

        read("/admin/payouts/" + payoutId, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.settledAt").isNotEmpty())
                .andExpect(jsonPath("$.data.reconciledByStaffId").value(reconciler.toString()));
        assertEquals(reconciler, jdbcTemplate.queryForObject(
                "select reconciled_by_staff_id from payouts where id = ?", UUID.class, payoutId));
    }

    @Test
    void theJobDetailNamesItsCreatorAndCancellation() throws Exception {
        String token = tokenFor("SUPPORT_AGENT");

        read("/admin/jobs/" + jobId, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.creator.id").value(employerId.toString()))
                .andExpect(jsonPath("$.data.creator.firstName").value("Şükrü"))
                .andExpect(jsonPath("$.data.creator.email").doesNotExist())
                .andExpect(jsonPath("$.data.cancelledAt").value(nullValue()))
                .andExpect(jsonPath("$.data.cancelReason").value(nullValue()));

        var employer = user(new UserPrincipal(employerId, employerEmail, Set.of(Role.ROLE_USER), null));

        mockMvc.perform(post("/jobs/" + otherJobId + "/cancel").with(employer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"" + "x".repeat(501) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[0].field").value("reason"))
                .andExpect(jsonPath("$.details[0].message").value("İptal gerekçesi en fazla 500 karakter olabilir."));
        read("/admin/jobs/" + otherJobId, token)
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andExpect(jsonPath("$.data.cancelledAt").value(nullValue()));

        mockMvc.perform(post("/jobs/" + otherJobId + "/cancel").with(employer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  Etkinlik ertelendi  \"}"))
                .andExpect(status().isOk());
        read("/admin/jobs/" + otherJobId, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelledAt").isNotEmpty())
                .andExpect(jsonPath("$.data.cancelReason").value("Etkinlik ertelendi"));

        mockMvc.perform(post("/jobs/" + companyJobId + "/cancel").with(employer))
                .andExpect(status().isOk());
        read("/admin/jobs/" + companyJobId, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelledAt").isNotEmpty())
                .andExpect(jsonPath("$.data.cancelReason").value(nullValue()));

        UUID blankReasonJobId = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into jobs (id, created_at, is_deleted, creator_id, title, status, employer_type,
                                  insurance_required, address_public, experience_required, min_age, max_age)
                values (?, now(), false, ?, 'Boş gerekçe', 'OPEN', 'INDIVIDUAL', false, false, false, 18, 65)
                """, blankReasonJobId, employerId);
        try {
            mockMvc.perform(post("/jobs/" + blankReasonJobId + "/cancel").with(employer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"reason\":\"   \"}"))
                    .andExpect(status().isOk());
            read("/admin/jobs/" + blankReasonJobId, token)
                    .andExpect(jsonPath("$.data.cancelledAt").isNotEmpty())
                    .andExpect(jsonPath("$.data.cancelReason").value(nullValue()));
        } finally {
            jdbcTemplate.update("delete from jobs where id = ?", blankReasonJobId);
        }
    }

    @Test
    void theUserDetailCarriesContextAndRevealsContactDetailsOnlyWithThePiiPermission() throws Exception {
        read("/admin/users/" + employerId, tokenFor("SUPPORT_AGENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(employerId.toString()))
                .andExpect(jsonPath("$.data.firstName").value("Şükrü"))
                .andExpect(jsonPath("$.data.email").value(nullValue()))
                .andExpect(jsonPath("$.data.phoneNumber").value(nullValue()))
                .andExpect(jsonPath("$.data.emailVerified").value(true))
                .andExpect(jsonPath("$.data.phoneVerified").value(false))
                .andExpect(jsonPath("$.data.identityVerified").value(true))
                .andExpect(jsonPath("$.data.isEmployer").value(true))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.companies", hasSize(0)))
                .andExpect(jsonPath("$.data.badges", hasSize(0)))
                .andExpect(jsonPath("$.data.counts.applications").value(0))
                .andExpect(jsonPath("$.data.counts.assignmentsAsWorker").value(0))
                .andExpect(jsonPath("$.data.counts.assignmentsAsEmployer").value(2))
                .andExpect(jsonPath("$.data.counts.payments").value(3))
                .andExpect(jsonPath("$.data.counts.payouts").value(0))
                .andExpect(jsonPath("$.data.counts.disputes").value(1))
                .andExpect(jsonPath("$.data.counts.reportsReceived").value(3))
                .andExpect(jsonPath("$.data.counts.reportsMade").value(0))
                .andExpect(jsonPath("$.data.identityReviewId").value(nullValue()))
                .andExpect(jsonPath("$.data.identityReviewStatus").value(nullValue()))
                .andExpect(jsonPath("$.data.pendingDeletionRequestId").value(nullValue()))
                .andExpect(jsonPath("$.data.sessionSummary.activeSessionCount").value(0))
                .andExpect(jsonPath("$.data.sessionSummary.lastLoginAt").value(nullValue()))
                .andExpect(jsonPath("$.data.sessionSummary.lastDevicePlatform").value(nullValue()))
                .andExpect(content().string(not(containsString(employerEmail))));

        read("/admin/users/" + workerId, tokenFor("TRUST_LEAD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(workerEmail))
                .andExpect(jsonPath("$.data.isEmployer").value(false))
                .andExpect(jsonPath("$.data.counts.applications").value(1))
                .andExpect(jsonPath("$.data.counts.assignmentsAsWorker").value(2))
                .andExpect(jsonPath("$.data.counts.assignmentsAsEmployer").value(0))
                .andExpect(jsonPath("$.data.counts.payouts").value(1))
                .andExpect(jsonPath("$.data.counts.disputes").value(1))
                .andExpect(jsonPath("$.data.counts.reportsMade").value(2));

        List<String> permissions = jdbcTemplate.queryForList(
                "select permission from admin_audit_entries where resource_id in (?, ?) order by occurred_at",
                String.class, employerId.toString(), workerId.toString());
        assertEquals(List.of("user.read", "user.read_pii"), permissions,
                "PII dönen okuma user.read_pii izniyle, dönmeyen user.read ile denetlenmeli");
    }

    @Test
    void theDisputeCounterAndTheDisputeFilterAgreeOnWhoIsInvolved() throws Exception {
        UUID openedByStranger = UUID.randomUUID();
        jdbcTemplate.update("insert into disputes (id, created_at, is_deleted, job_assignment_id, opened_by_id, "
                        + "status, reason) values (?, now(), false, ?, ?, 'OPEN', ?)",
                openedByStranger, cancelledAssignmentId, strangerId, "Üçüncü kişi açtı");
        try {
            read("/admin/users/" + strangerId, tokenFor("SUPPORT_AGENT"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.counts.disputes").value(1));
            read("/admin/disputes?userId=" + strangerId + "&size=100", tokenFor("DISPUTE_HANDLER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalElements").value(1))
                    .andExpect(jsonPath("$.data.content[0].id").value(openedByStranger.toString()));
        } finally {
            jdbcTemplate.update("delete from disputes where id = ?", openedByStranger);
        }
    }

    @Test
    void theUserDetailIsRefusedWithoutUserReadWhetherOrNotTheUserExists() throws Exception {
        assertRefusedIdentically(id -> "/admin/users/" + id, workerId, tokenFor("CATALOG_EDITOR"));
    }

    @Test
    void theUserDetailNamesItsLatestIdentityReviewAndSummarisesSessionsWithoutDeviceIdentity() throws Exception {
        UUID olderReviewId = UUID.randomUUID();
        UUID latestReviewId = UUID.randomUUID();
        UUID androidDeviceId = UUID.randomUUID();
        UUID iosDeviceId = UUID.randomUUID();
        UUID revokedSessionId = UUID.randomUUID();
        UUID androidSessionId = UUID.randomUUID();
        UUID iosSessionId = UUID.randomUUID();
        String clientDeviceId = "client-device-" + iosDeviceId;
        String refreshHash = "refresh-hash-" + iosSessionId;
        try {
            insertIdentityReview(olderReviewId, workerId, "now() - interval '2 days'", true);
            insertIdentityReview(latestReviewId, workerId, "now() - interval '1 day'", false);
            jdbcTemplate.update("""
                    insert into devices (id, created_at, is_deleted, user_id, type, name, client_device_id)
                    values (?, now() - interval '3 days', false, ?, 'ANDROID', 'Pixel', ?)
                    """, androidDeviceId, workerId, "client-device-" + androidDeviceId);
            jdbcTemplate.update("""
                    insert into devices (id, created_at, is_deleted, user_id, type, name, client_device_id)
                    values (?, now() - interval '1 hour', false, ?, 'IOS', 'iPhone', ?)
                    """, iosDeviceId, workerId, clientDeviceId);
            insertSession(revokedSessionId, androidDeviceId, "now() - interval '3 days'", false, refreshHash);
            insertSession(androidSessionId, androidDeviceId, "now() - interval '2 days'", true, refreshHash);
            insertSession(iosSessionId, iosDeviceId, "now() - interval '1 hour'", true, refreshHash);

            read("/admin/users/" + workerId, tokenFor("SUPPORT_AGENT"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.identityReviewId").value(latestReviewId.toString()))
                    .andExpect(jsonPath("$.data.identityReviewStatus").value("PENDING"))
                    .andExpect(jsonPath("$.data.sessionSummary.activeSessionCount").value(2))
                    .andExpect(jsonPath("$.data.sessionSummary.lastLoginAt").isNotEmpty())
                    .andExpect(jsonPath("$.data.sessionSummary.lastDevicePlatform").value("IOS"))
                    .andExpect(content().string(not(containsString(clientDeviceId))))
                    .andExpect(content().string(not(containsString(iosDeviceId.toString()))))
                    .andExpect(content().string(not(containsString(iosSessionId.toString()))))
                    .andExpect(content().string(not(containsString(refreshHash))))
                    .andExpect(content().string(not(containsString("10.0.0.7"))));
        } finally {
            jdbcTemplate.update("delete from sessions where id in (?, ?, ?)", revokedSessionId, androidSessionId,
                    iosSessionId);
            jdbcTemplate.update("delete from devices where id in (?, ?)", androidDeviceId, iosDeviceId);
            jdbcTemplate.update("delete from user_verifications where id in (?, ?)", olderReviewId, latestReviewId);
        }
    }

    @Test
    void theBankAccountsOfAUserAreMaskedAndOpenOnlyToThePiiPermission() throws Exception {
        read("/admin/users/" + workerId + "/bank-accounts", tokenFor("TRUST_LEAD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value(bankAccountId.toString()))
                .andExpect(jsonPath("$.data[0].maskedIban").value("TR18***2315"))
                .andExpect(jsonPath("$.data[0].verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.data[0].primary").value(true))
                .andExpect(jsonPath("$.data[0].accountHolderNameVerified").value(true))
                .andExpect(jsonPath("$.data[0].createdAt").isNotEmpty())
                .andExpect(content().string(not(containsString(FULL_IBAN))))
                .andExpect(content().string(not(containsString(IBAN_BODY))));
        read("/admin/users/" + strangerId + "/bank-accounts", tokenFor("TRUST_LEAD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
        read("/admin/users/" + UUID.randomUUID() + "/bank-accounts", tokenFor("TRUST_LEAD"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND_BY_ID"));

        assertRefusedIdentically(id -> "/admin/users/" + id + "/bank-accounts", workerId, tokenFor("SUPPORT_AGENT"));
        assertRefusedIdentically(id -> "/admin/users/" + id + "/bank-accounts", workerId, tokenFor("FINANCE_OFFICER"));

        List<String> permissions = jdbcTemplate.queryForList(
                "select permission from admin_audit_entries where resource_id = ? and allowed = true",
                String.class, workerId.toString());
        assertEquals(List.of("user.read_pii"), permissions,
                "banka hesabı okuması user.read_pii izniyle denetlenmeli");
    }

    @Test
    void theCompanyIdentityTravelsWithTheAssignmentItsPaymentsAndItsPayout() throws Exception {
        read("/admin/job-assignments/" + assignmentId, tokenFor("FINANCE_ANALYST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyId").value(nullValue()))
                .andExpect(jsonPath("$.data.companyName").value(nullValue()));

        jdbcTemplate.update("update jobs set company_id = ? where id = ?", companyId, jobId);
        try {
            String companyName = "Detay Lojistik " + companyId;
            read("/admin/job-assignments/" + assignmentId, tokenFor("FINANCE_ANALYST"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.companyId").value(companyId.toString()))
                    .andExpect(jsonPath("$.data.companyName").value(companyName));
            read("/admin/job-assignments?jobId=" + jobId, tokenFor("FINANCE_ANALYST"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].companyId").value(companyId.toString()))
                    .andExpect(jsonPath("$.data.content[0].companyName").value(companyName));
            read("/admin/payments/" + paymentId, tokenFor("FINANCE_ANALYST"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.companyId").value(companyId.toString()))
                    .andExpect(jsonPath("$.data.companyName").value(companyName));
            read("/admin/job-assignments/" + assignmentId + "/payments", tokenFor("FINANCE_ANALYST"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[*].companyId", hasItem(companyId.toString())));
            read("/admin/payouts/" + payoutId, tokenFor("FINANCE_OFFICER"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.companyId").value(companyId.toString()))
                    .andExpect(jsonPath("$.data.companyName").value(companyName));
        } finally {
            jdbcTemplate.update("update jobs set company_id = null where id = ?", jobId);
        }
    }

    private void insertIdentityReview(UUID id, UUID user, String createdAt, boolean verified) {
        long digits = Math.floorMod(id.getMostSignificantBits(), 8_999_999_999L) + 1_000_000_000L;
        jdbcTemplate.update("""
                insert into user_verifications (id, verification_type, created_at, is_deleted, is_verified,
                                                user_id, tc_identity_number, first_name, last_name, birth_date,
                                                tc_document_number)
                values (?, 'IDENTITY', %s, false, ?, ?, ?, 'Işıl', 'Çağlayan', date '1998-04-12', 'A01A00000')
                """.formatted(createdAt), id, verified, user, "1" + digits);
    }

    private void insertSession(UUID id, UUID deviceId, String createdAt, boolean active, String refreshHash) {
        jdbcTemplate.update("""
                insert into sessions (id, created_at, is_deleted, device_id, refresh_token_hash, refresh_expires_at,
                                      ip_address, last_accessed_at, is_active)
                values (?, %s, false, ?, ?, now() + interval '30 days', '10.0.0.7', now(), ?)
                """.formatted(createdAt), id, deviceId, refreshHash, active);
    }
}
