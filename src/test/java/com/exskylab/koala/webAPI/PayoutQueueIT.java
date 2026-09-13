package com.exskylab.koala.webAPI;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.exskylab.koala.business.abstracts.PayoutService;
import com.exskylab.koala.core.dtos.payout.response.StaffPayoutDto;
import com.exskylab.koala.core.privacy.AccountAnonymizer;
import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.support.AbstractStaffIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class PayoutQueueIT extends AbstractStaffIntegrationTest {

    private static final String FULL_IBAN = "TR180006200119000006672315";
    private static final String IBAN_BODY = "620011900000";
    private static final String PROVIDER_REFERENCE = "iyz-transaction-4711";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PayoutService payoutService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private AccountAnonymizer accountAnonymizer;

    private final List<UUID> deletionRequests = new ArrayList<>();
    private final List<UUID> workers = new ArrayList<>();
    private final List<UUID> jobs = new ArrayList<>();
    private final List<UUID> assignments = new ArrayList<>();
    private final List<UUID> payouts = new ArrayList<>();
    private UUID worker;
    private UUID payoutId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("delete from payouts");
        worker = seedProcessingPayout("Ayşe", "Deneme", FULL_IBAN, PROVIDER_REFERENCE);
        payoutId = payouts.getFirst();
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.update("delete from admin_audit_entries");
        jdbcTemplate.update("delete from staff_members where email like 'payout-queue-%@iskoala.com'");
        for (UUID request : deletionRequests) {
            jdbcTemplate.update("delete from account_anonymization_audit where request_id = ?", request);
            jdbcTemplate.update("delete from account_deletion_requests where id = ?", request);
        }
        for (UUID assignment : assignments) {
            jdbcTemplate.update("delete from payouts where job_assignment_id = ?", assignment);
            jdbcTemplate.update("delete from job_assignments where id = ?", assignment);
        }
        for (UUID job : jobs) {
            jdbcTemplate.update("delete from jobs where id = ?", job);
        }
        for (UUID user : workers) {
            jdbcTemplate.update("delete from bank_accounts where user_id = ?", user);
            jdbcTemplate.update("delete from authorities where user_id = ?", user);
            jdbcTemplate.update("delete from users where id = ?", user);
        }
    }

    private UUID seedProcessingPayout(String firstName, String lastName, String iban, String providerReference) {
        UUID user = UUID.randomUUID();
        UUID job = UUID.randomUUID();
        UUID assignment = UUID.randomUUID();
        UUID payout = UUID.randomUUID();
        UUID account = UUID.randomUUID();

        jdbcTemplate.update("insert into users (id, created_at, is_deleted, email, password, first_name, last_name, "
                        + "is_employer, email_verified, phone_verified, identity_verified) "
                        + "values (?, now(), false, ?, 'test', ?, ?, false, true, false, true)",
                user, user + "@example.com", firstName, lastName);
        jdbcTemplate.update("insert into bank_accounts (id, created_at, is_deleted, user_id, iban, is_primary, "
                        + "is_active, verification_status, verification_attempts, account_holder_name_verified) "
                        + "values (?, now(), false, ?, ?, true, true, 'UNVERIFIED', 0, false)",
                account, user, iban);
        jdbcTemplate.update("""
                insert into jobs (id, created_at, is_deleted, title, status, employer_type,
                                  experience_required, insurance_required, min_age, max_age,
                                  start_date_time, end_date_time)
                values (?, now(), false, 'Payout queue', 'OPEN', 'INDIVIDUAL',
                        false, false, 18, 65, now() + interval '1 day', now() + interval '2 days')
                """, job);
        jdbcTemplate.update("""
                insert into job_assignments
                    (id, created_at, is_deleted, job_id, worker_id, status, payment_status,
                     pricing_version, currency,
                     worker_payout, commission_rate, commission_amount, total_charged, work_hours)
                values (?, now(), false, ?, ?, 'COMPLETED', 'RELEASED_TO_WORKER', '2026-09-v1', 'TRY',
                        1000.00, 0.0750, 75.00, 1075.00, 0)
                """, assignment, job, user);
        jdbcTemplate.update("""
                insert into payouts (id, created_at, is_deleted, job_assignment_id, worker_id, bank_account_id,
                                     amount, currency, status, provider_reference, processing_at)
                values (?, now() - interval '2 days', false, ?, ?, ?, 1000.00, 'TRY', 'PROCESSING', ?,
                        now() - interval '2 days')
                """, payout, assignment, user, account, providerReference);

        workers.add(user);
        jobs.add(job);
        assignments.add(assignment);
        payouts.add(payout);
        return user;
    }

    private void deleteTheAccountOf(UUID user) {
        UUID request = UUID.randomUUID();
        jdbcTemplate.update("""
                insert into account_deletion_requests
                    (id, user_id, subject_id, status, requested_at, created_at, is_deleted)
                values (?, ?, ?, 'REQUESTED', now(), now(), false)
                """, request, user, user);
        deletionRequests.add(request);
        transactionTemplate.executeWithoutResult(status -> accountAnonymizer.anonymize(user, request));
    }

    private String staffToken(String... roles) {
        return staffTokenFor("payout-queue-" + UUID.randomUUID() + "@iskoala.com", "Finans", roles);
    }

    private UserPrincipal principal(UUID id) {
        return new UserPrincipal(id, id + "@example.com", Set.of(Role.ROLE_USER), null);
    }

    @Test
    void aFinanceOfficerSeesTheEvidenceNeededToMatchTheStatementLine() throws Exception {
        mockMvc.perform(get("/admin/payouts")
                        .header("Authorization", "Bearer " + staffToken("FINANCE_OFFICER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id").value(payoutId.toString()))
                .andExpect(jsonPath("$.data.content[0].providerReference").value(PROVIDER_REFERENCE))
                .andExpect(jsonPath("$.data.content[0].recipient.id").value(worker.toString()))
                .andExpect(jsonPath("$.data.content[0].recipient.firstName").value("Ayşe"))
                .andExpect(jsonPath("$.data.content[0].recipient.lastName").value("Deneme"))
                .andExpect(jsonPath("$.data.content[0].maskedIban").value("TR18***2315"))
                .andExpect(jsonPath("$.data.content[0].amount").value("1000.00"))
                .andExpect(jsonPath("$.data.content[0].currency").value("TRY"))
                .andExpect(jsonPath("$.data.content[0].status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.content[0].processingAt").exists())
                .andExpect(jsonPath("$.data.content[0].iban").doesNotExist())
                .andExpect(content().string(not(containsString(FULL_IBAN))))
                .andExpect(content().string(not(containsString(IBAN_BODY))));
    }

    @Test
    void aFinanceAnalystIsRefusedTheQueueOutrightRatherThanHandedARedactedRow() throws Exception {
        mockMvc.perform(get("/admin/payouts")
                        .header("Authorization", "Bearer " + staffToken("FINANCE_ANALYST")))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString(PROVIDER_REFERENCE))))
                .andExpect(content().string(not(containsString("TR18"))));
    }

    @Test
    void aMarketplaceUserCannotReachTheQueue() throws Exception {
        mockMvc.perform(get("/admin/payouts").with(user(principal(worker))))
                .andExpect(status().isForbidden())
                .andExpect(content().string(not(containsString(PROVIDER_REFERENCE))));
    }

    @Test
    void theWorkerFacingListNeverCarriesTheStaffEvidence() throws Exception {
        mockMvc.perform(get("/users/me/payouts").with(user(principal(worker))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].status").value("PROCESSING"))
                .andExpect(jsonPath("$.data[0].providerReference").doesNotExist())
                .andExpect(jsonPath("$.data[0].maskedIban").doesNotExist())
                .andExpect(jsonPath("$.data[0].recipient").doesNotExist())
                .andExpect(content().string(not(containsString(PROVIDER_REFERENCE))))
                .andExpect(content().string(not(containsString(FULL_IBAN))));
    }

    @Test
    void aPayoutWhoseWorkerDeletedTheirAccountStaysOnTheQueueWithItsEvidence() throws Exception {
        deleteTheAccountOf(worker);

        mockMvc.perform(get("/admin/payouts")
                        .header("Authorization", "Bearer " + staffToken("FINANCE_OFFICER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id").value(payoutId.toString()))
                .andExpect(jsonPath("$.data.content[0].providerReference").value(PROVIDER_REFERENCE))
                .andExpect(jsonPath("$.data.content[0].amount").value("1000.00"))
                .andExpect(jsonPath("$.data.content[0].currency").value("TRY"))
                .andExpect(jsonPath("$.data.content[0].status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.content[0].processingAt").exists())
                .andExpect(jsonPath("$.data.content[0].recipient").value(nullValue()))
                .andExpect(jsonPath("$.data.content[0].maskedIban").value(nullValue()))
                .andExpect(content().string(not(containsString("Ayşe"))))
                .andExpect(content().string(not(containsString(FULL_IBAN))));
    }

    @Test
    void aPayoutWhoseWorkerRowWasSoftDeletedKeepsItsBankEvidence() throws Exception {
        jdbcTemplate.update("update users set is_deleted = true, deleted_at = now() where id = ?", worker);

        mockMvc.perform(get("/admin/payouts")
                        .header("Authorization", "Bearer " + staffToken("FINANCE_OFFICER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].id").value(payoutId.toString()))
                .andExpect(jsonPath("$.data.content[0].providerReference").value(PROVIDER_REFERENCE))
                .andExpect(jsonPath("$.data.content[0].maskedIban").value("TR18***2315"))
                .andExpect(jsonPath("$.data.content[0].amount").value("1000.00"))
                .andExpect(jsonPath("$.data.content[0].currency").value("TRY"))
                .andExpect(jsonPath("$.data.content[0].recipient").value(nullValue()))
                .andExpect(content().string(not(containsString(FULL_IBAN))));
    }

    @Test
    void servingTheQueueNeverWritesTheFullIbanToAnyLogLine() throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);

        try {
            mockMvc.perform(get("/admin/payouts")
                            .header("Authorization", "Bearer " + staffToken("FINANCE_OFFICER")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content[0].maskedIban").value("TR18***2315"));

            List<String> leaking = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .filter(line -> line.contains(FULL_IBAN) || line.contains(IBAN_BODY))
                    .toList();
            assertEquals(List.of(), leaking, "kuyruk servis edilirken tam IBAN loga yazildi");
        } finally {
            root.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void theQueueIsBuiltWithoutAQueryPerRecipientOrBankAccount() {
        seedProcessingPayout("Mehmet", "Deneme", "TR330006100519786457841326", "iyz-transaction-4712");
        seedProcessingPayout("Zeynep", "Deneme", "TR560006200000000000000012", "iyz-transaction-4713");
        seedProcessingPayout("Ali", "Deneme", "TR120006200000000000000045", "iyz-transaction-4714");
        seedProcessingPayout("Elif", "Deneme", "TR450006200000000000000078", "iyz-transaction-4715");
        seedProcessingPayout("Can", "Deneme", "TR780006200000000000000091", "iyz-transaction-4716");

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        boolean wasEnabled = statistics.isStatisticsEnabled();
        statistics.setStatisticsEnabled(true);
        try {
            statistics.clear();
            List<StaffPayoutDto> oneRow = transactionTemplate.execute(status ->
                    payoutService.listForStaff(null, PageRequest.of(0, 1)).content());
            long statementsForOneRow = statistics.getPrepareStatementCount();

            statistics.clear();
            List<StaffPayoutDto> fiveRows = transactionTemplate.execute(status ->
                    payoutService.listForStaff(null, PageRequest.of(0, 5)).content());
            long statementsForFiveRows = statistics.getPrepareStatementCount();

            assertEquals(1, oneRow.size());
            assertEquals(5, fiveRows.size());
            assertFalse(fiveRows.stream().map(StaffPayoutDto::maskedIban).toList().contains(null),
                    "her satir kendi hesabinin maskeli IBAN'ini tasimali");
            assertFalse(fiveRows.stream().map(StaffPayoutDto::recipient).toList().contains(null),
                    "her satir kendi alicisini tasimali");
            assertEquals(0, statistics.getEntityFetchCount(),
                    "alici ya da banka hesabi satir basina ayri sorguyla yuklendi");
            assertEquals(statementsForOneRow, statementsForFiveRows,
                    "Sorgu sayisi satir sayisiyla buyumemeli. Iki sayfa da dolu (6 tohum satir, size=1 ve "
                            + "size=5), yani ikisi de hakedisler+banka hesaplari SELECT'i, Spring Data'nin "
                            + "count sorgusu ve alicilarin tek toplu SELECT'i olmak uzere 3 statement kurmali; "
                            + "sayfa dolmasaydi count atlanir ve sayi 2 olurdu.");
        } finally {
            statistics.setStatisticsEnabled(wasEnabled);
        }
    }
}
