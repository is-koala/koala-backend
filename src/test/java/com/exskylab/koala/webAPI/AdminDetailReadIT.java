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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
                                              application_date_time)
                values (?, now(), false, ?, ?, 'ACCEPTED', now())
                """, UUID.randomUUID(), jobId, workerId);

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
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from admin_audit_entries");
        jdbcTemplate.update("delete from staff_members where email like 'detail-read-%@iskoala.com'");
        jdbcTemplate.update("delete from reports where id in (?, ?)", reportId, unrelatedReportId);
        jdbcTemplate.update("delete from disputes where id = ?", disputeId);
        jdbcTemplate.update("delete from settlement_tasks where id = ?", settlementTaskId);
        jdbcTemplate.update("delete from payouts where id = ?", payoutId);
        jdbcTemplate.update("delete from bank_accounts where id = ?", bankAccountId);
        jdbcTemplate.update("delete from refunds where id = ?", refundId);
        jdbcTemplate.update("delete from payments where job_assignment_id = ?", assignmentId);
        jdbcTemplate.update("delete from job_applications where job_id = ?", jobId);
        jdbcTemplate.update("delete from job_assignments where id = ?", assignmentId);
        jdbcTemplate.update("delete from jobs where id in (?, ?)", jobId, companyJobId);
        jdbcTemplate.update("delete from addresses where id = ?", addressId);
        jdbcTemplate.update("delete from companies where id = ?", companyId);
        jdbcTemplate.update("delete from users where id in (?, ?, ?)", employerId, workerId, strangerId);
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

    private void assertRefusedIdentically(Function<UUID, String> path, String token) throws Exception {
        String real = normalise(read(path.apply(payoutId), token)
                .andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString(), payoutId);
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
        assertRefusedIdentically(id -> "/admin/payouts/" + id, tokenFor("FINANCE_ANALYST"));
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
                .andExpect(jsonPath("$.data[0].worker.email").value(workerEmail))
                .andExpect(jsonPath("$.data[0].employer.email").value(employerEmail))
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
                .andExpect(content().string(not(containsString(SUBMERCHANT_KEY))));
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
                .andExpect(content().string(not(containsString(PROVIDER_TOKEN))))
                .andExpect(content().string(not(containsString(SUBMERCHANT_KEY))));
    }

    @Test
    void aDisputeHandlerIsRefusedThePaymentSurfacesWhetherOrNotTheyExist() throws Exception {
        String token = tokenFor("DISPUTE_HANDLER");
        assertRefusedIdentically(id -> "/admin/payments/" + id, token);
        assertRefusedIdentically(id -> "/admin/job-assignments/" + id + "/payments", token);
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
                .andExpect(content().string(not(containsString(unrelatedReportId.toString()))));
    }

    @Test
    void aSupportAgentListsJobsFilteredByStatusAndSearchedByEmployerEmail() throws Exception {
        String token = tokenFor("SUPPORT_AGENT");

        read("/admin/jobs?query=" + employerEmail, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[*].id", hasItem(jobId.toString())))
                .andExpect(jsonPath("$.data.content[*].id", hasItem(companyJobId.toString())))
                .andExpect(jsonPath("$.data.content[*].employer.email", hasItem(employerEmail)));

        read("/admin/jobs?status=OPEN&query=" + employerEmail, token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id").value(jobId.toString()))
                .andExpect(jsonPath("$.data.content[0].status").value("OPEN"))
                .andExpect(jsonPath("$.data.content[0].applicationCount").value(1))
                .andExpect(jsonPath("$.data.content[0].assignmentCount").value(1))
                .andExpect(jsonPath("$.data.content[0].employer.id").value(employerId.toString()))
                .andExpect(jsonPath("$.data.content[0].pricing.employerTotal").value("1075.00"));

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
}
