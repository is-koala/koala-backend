package com.exskylab.koala.webAPI;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.support.AbstractStaffIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class WorkerIdentityRevealIT extends AbstractStaffIntegrationTest {

    private static final String WORKER_TCKN = "10000000146";
    private static final String REVEAL_MESSAGE =
            "İşvereniniz sigorta bildirimi için kimlik bilgilerinizi görüntüledi.";
    private static final Pattern ELEVEN_DIGITS = Pattern.compile("(?<!\\d)\\d{11}(?!\\d)");
    private static final Pattern UUID_TOKEN =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private final UUID employerId = UUID.randomUUID();
    private final UUID companyAdminId = UUID.randomUUID();
    private final UUID workerId = UUID.randomUUID();
    private final UUID unverifiedWorkerId = UUID.randomUUID();
    private final UUID outsiderId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final UUID companyJobId = UUID.randomUUID();
    private final UUID confirmedAssignmentId = UUID.randomUUID();
    private final UUID companyAssignmentId = UUID.randomUUID();
    private final UUID pendingAssignmentId = UUID.randomUUID();
    private final UUID unverifiedAssignmentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        insertUser(employerId, "İşveren", "Kişi", false);
        insertUser(companyAdminId, "Yetkili", "Kişi", false);
        insertUser(workerId, "Çağrı", "Yılmaz", true);
        insertUser(unverifiedWorkerId, "Doğrulanmamış", "İşçi", false);
        insertUser(outsiderId, "Yabancı", "Kişi", false);
        jdbc.update("update users set tc_identity_number = ?, birth_date = '1990-05-17', "
                        + "identity_verified_at = '2026-09-01T10:15:30Z', identity_verification_method = 'DOCUMENT' "
                        + "where id = ?", WORKER_TCKN, workerId);

        jdbc.update("insert into companies (id, created_at, is_deleted, name, tax_number, email, approval_status) "
                        + "values (?, now(), false, ?, ?, ?, 'APPROVED')",
                companyId, "Kimlik Şirketi " + companyId,
                String.valueOf(Math.abs(companyId.hashCode())), companyId + "@example.com");
        jdbc.update("insert into company_contacts (id, created_at, is_deleted, company_id, user_id, role) "
                        + "values (?, now(), false, ?, ?, 'ADMIN')",
                UUID.randomUUID(), companyId, companyAdminId);
        jdbc.update("insert into job_categories (id, name, code, requires_insurance) values (?, ?, ?, false)",
                categoryId, "Kimlik", "IDENTITY_REVEAL_" + categoryId);
        insertJob(jobId, null, "INDIVIDUAL");
        insertJob(companyJobId, companyId, "COMPANY");
        insertAssignment(confirmedAssignmentId, jobId, workerId, "CONFIRMED");
        insertAssignment(companyAssignmentId, companyJobId, workerId, "IN_PROGRESS");
        insertAssignment(pendingAssignmentId, jobId, workerId, "PENDING_CONFIRMATION");
        insertAssignment(unverifiedAssignmentId, jobId, unverifiedWorkerId, "COMPLETED");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from admin_audit_entries");
        jdbc.update("delete from identity_reveals where worker_id in (?, ?)", workerId, unverifiedWorkerId);
        jdbc.update("delete from notifications where recipient_id in (?, ?, ?, ?, ?) "
                        + "or sender_id in (?, ?, ?, ?, ?) or created_by_id in (?, ?, ?, ?, ?)",
                employerId, companyAdminId, workerId, unverifiedWorkerId, outsiderId,
                employerId, companyAdminId, workerId, unverifiedWorkerId, outsiderId,
                employerId, companyAdminId, workerId, unverifiedWorkerId, outsiderId);
        jdbc.update("delete from job_assignments where id in (?, ?, ?, ?)",
                confirmedAssignmentId, companyAssignmentId, pendingAssignmentId, unverifiedAssignmentId);
        jdbc.update("delete from jobs where id in (?, ?)", jobId, companyJobId);
        jdbc.update("delete from job_categories where id = ?", categoryId);
        jdbc.update("delete from company_contacts where company_id = ?", companyId);
        jdbc.update("delete from companies where id = ?", companyId);
        jdbc.update("delete from authorities where user_id in (?, ?, ?, ?, ?)",
                employerId, companyAdminId, workerId, unverifiedWorkerId, outsiderId);
        jdbc.update("delete from users where id in (?, ?, ?, ?, ?)",
                employerId, companyAdminId, workerId, unverifiedWorkerId, outsiderId);
        deleteStaffMembersLike("identity-reveal-%@iskoala.com");
    }

    @Test
    void theJobOwnerReadsTheHiredWorkersIdentityFromTheCard() throws Exception {
        reveal(confirmedAssignmentId, employerId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fullName").value("Çağrı Yılmaz"))
                .andExpect(jsonPath("$.data.tcIdentityNumber").value(WORKER_TCKN))
                .andExpect(jsonPath("$.data.birthDate").value("1990-05-17"))
                .andExpect(jsonPath("$.data.verifiedAt").value("2026-09-01T10:15:30Z"))
                .andExpect(jsonPath("$.data.verificationMethod").value("DOCUMENT"));
    }

    @Test
    void aCompanyContactReadsTheIdentityOnTheCompanysAssignment() throws Exception {
        reveal(companyAssignmentId, companyAdminId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tcIdentityNumber").value(WORKER_TCKN));
    }

    @Test
    void theWorkerCannotReadTheirOwnIdentityThroughTheEmployerEndpoint() throws Exception {
        assertIndistinguishableFromMissing(confirmedAssignmentId, workerId);
    }

    @Test
    void anOutsiderSeesTheAssignmentAsMissing() throws Exception {
        assertIndistinguishableFromMissing(confirmedAssignmentId, outsiderId);
    }

    @Test
    void anAssignmentTheWorkerHasNotAcceptedYetLooksMissingEvenToTheEmployer() throws Exception {
        assertIndistinguishableFromMissing(pendingAssignmentId, employerId);
        assertEquals(0, revealCount(pendingAssignmentId), "başvuru aşamasındaki atama kayıt yazmamalı");
    }

    @Test
    void anUnverifiedWorkerIsRefusedWithConflictAndNothingIsRecorded() throws Exception {
        reveal(unverifiedAssignmentId, employerId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("WORKER_IDENTITY_NOT_VERIFIED"))
                .andExpect(jsonPath("$.data").doesNotExist());
        assertEquals(0, revealCount(unverifiedAssignmentId));
        assertEquals(0, notificationsForWorker(unverifiedWorkerId));
    }

    @Test
    void everyRevealIsRecordedAndTheWorkerIsToldOncePerDay() throws Exception {
        reveal(confirmedAssignmentId, employerId).andExpect(status().isOk());
        reveal(confirmedAssignmentId, employerId).andExpect(status().isOk());

        assertEquals(2, revealCount(confirmedAssignmentId), "her çağrı kayda geçmeli");
        assertEquals(1, notificationsForWorker(workerId), "24 saat içinde tekrar okuma bildirim üretmemeli");

        Map<String, Object> record = jdbc.queryForMap(
                "select viewer_id, worker_id, ip_address, revealed_at from identity_reveals "
                        + "where job_assignment_id = ? order by revealed_at limit 1", confirmedAssignmentId);
        assertEquals(employerId, record.get("viewer_id"));
        assertEquals(workerId, record.get("worker_id"));
        assertEquals("127.0.***", record.get("ip_address"), "IP maskeli saklanmalı");

        assertEquals("ACCOUNT_SECURITY", jdbc.queryForObject(
                "select category from notifications where recipient_id = ? and channel = 'IN_APP' and body = ?",
                String.class, workerId, REVEAL_MESSAGE));
    }

    @Test
    void aRevealOlderThanADayNotifiesTheWorkerAgain() throws Exception {
        jdbc.update("insert into identity_reveals (id, viewer_id, worker_id, job_assignment_id, revealed_at, "
                        + "ip_address) values (?, ?, ?, ?, ?, '10.0.***')",
                UUID.randomUUID(), employerId, workerId, confirmedAssignmentId,
                Timestamp.from(Instant.now().minus(25, ChronoUnit.HOURS)));

        reveal(confirmedAssignmentId, employerId).andExpect(status().isOk());

        assertEquals(2, revealCount(confirmedAssignmentId));
        assertEquals(1, notificationsForWorker(workerId), "sessiz pencere dolunca bildirim yeniden gitmeli");
    }

    @Test
    void aDifferentEmployerOfTheSameWorkerStillTriggersANotification() throws Exception {
        reveal(confirmedAssignmentId, employerId).andExpect(status().isOk());
        reveal(companyAssignmentId, companyAdminId).andExpect(status().isOk());

        assertEquals(2, notificationsForWorker(workerId), "sessiz pencere işveren + atama çiftine özeldir");
    }

    @Test
    void theIdentityNumberNeverReachesTheLog() throws Exception {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            reveal(confirmedAssignmentId, employerId).andExpect(status().isOk());
            reveal(unverifiedAssignmentId, employerId).andExpect(status().isConflict());
            reveal(confirmedAssignmentId, outsiderId).andExpect(status().isNotFound());
        } finally {
            root.detachAppender(appender);
            appender.stop();
        }

        List<String> leaking = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .map(message -> UUID_TOKEN.matcher(message).replaceAll("{uuid}"))
                .filter(message -> message.contains(WORKER_TCKN) || ELEVEN_DIGITS.matcher(message).find())
                .toList();
        assertTrue(leaking.isEmpty(), "log satırında TCKN görünüyor: " + leaking);
        assertTrue(appender.list.stream()
                        .anyMatch(event -> event.getFormattedMessage().startsWith("Worker identity revealed")),
                "görüntüleme INFO satırı yazılmalı");
    }

    @Test
    void staffWithThePiiPermissionSeeWhoViewedTheWorkersIdentity() throws Exception {
        reveal(confirmedAssignmentId, employerId).andExpect(status().isOk());
        reveal(companyAssignmentId, companyAdminId).andExpect(status().isOk());

        mockMvc.perform(get("/admin/users/{userId}/identity-reveals", workerId)
                        .header("Authorization", "Bearer " + staffToken("TRUST_LEAD")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].viewerId").value(companyAdminId.toString()))
                .andExpect(jsonPath("$.data.content[0].viewerFullName").value("Yetkili Kişi"))
                .andExpect(jsonPath("$.data.content[0].jobAssignmentId").value(companyAssignmentId.toString()))
                .andExpect(jsonPath("$.data.content[0].jobId").value(companyJobId.toString()))
                .andExpect(jsonPath("$.data.content[0].jobTitle").value("Kimlik ilanı " + companyJobId))
                .andExpect(jsonPath("$.data.content[0].ipAddress").value("127.0.***"))
                .andExpect(jsonPath("$.data.content[0].revealedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.content[1].viewerId").value(employerId.toString()))
                .andExpect(content().string(not(containsString(WORKER_TCKN))));

        List<String> permissions = jdbc.queryForList(
                "select permission from admin_audit_entries where resource_id = ? and allowed = true",
                String.class, workerId.toString());
        assertEquals(List.of("user.read_pii"), permissions, "okuma user.read_pii izniyle denetlenmeli");

        mockMvc.perform(get("/admin/users/{userId}/identity-reveals", UUID.randomUUID())
                        .header("Authorization", "Bearer " + staffToken("TRUST_LEAD")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("USER_NOT_FOUND_BY_ID"));

        mockMvc.perform(get("/admin/users/{userId}/identity-reveals", workerId)
                        .header("Authorization", "Bearer " + staffToken("SUPPORT_AGENT")))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/users/{userId}/identity-reveals", workerId)
                        .param("sort", "ipAddress,desc")
                        .header("Authorization", "Bearer " + staffToken("TRUST_LEAD")))
                .andExpect(status().isBadRequest());
    }

    private ResultActions reveal(UUID assignmentId, UUID actorId) throws Exception {
        return mockMvc.perform(get("/job-assignments/{assignmentId}/worker-identity", assignmentId)
                .with(user(principal(actorId))));
    }

    private void assertIndistinguishableFromMissing(UUID real, UUID actorId) throws Exception {
        UUID phantom = UUID.randomUUID();
        assertEquals(envelope(reveal(phantom, actorId), phantom), envelope(reveal(real, actorId), real),
                "var olmayan atama ile erişilemeyen atamanın yanıtı ayırt edilebiliyor");
        reveal(real, actorId).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_ASSIGNMENT_NOT_FOUND"))
                .andExpect(content().string(not(containsString(WORKER_TCKN))));
    }

    private String envelope(ResultActions actions, UUID resourceId) throws Exception {
        String body = actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Map<String, Object> parsed =
                new TreeMap<>(objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {
                }));
        parsed.remove("timeStamp");
        parsed.remove("requestId");
        return parsed.toString().replace(resourceId.toString(), "{istenen-id}");
    }

    private int revealCount(UUID assignmentId) {
        return jdbc.queryForObject("select count(*) from identity_reveals where job_assignment_id = ?",
                Integer.class, assignmentId);
    }

    private int notificationsForWorker(UUID recipientId) {
        return jdbc.queryForObject(
                "select count(*) from notifications where recipient_id = ? and channel = 'IN_APP' and body = ?",
                Integer.class, recipientId, REVEAL_MESSAGE);
    }

    private String staffToken(String role) {
        return staffTokenFor("identity-reveal-" + UUID.randomUUID() + "@iskoala.com", "Okuyucu", role);
    }

    private void insertUser(UUID id, String firstName, String lastName, boolean identityVerified) {
        jdbc.update("insert into users (id, created_at, is_deleted, email, password, first_name, last_name, "
                        + "is_employer, email_verified, phone_verified, identity_verified) "
                        + "values (?, now(), false, ?, 'test', ?, ?, false, true, false, ?)",
                id, id + "@example.com", firstName, lastName, identityVerified);
    }

    private void insertJob(UUID id, UUID company, String employerType) {
        jdbc.update("insert into jobs (id, created_at, is_deleted, creator_id, company_id, job_category_id, "
                        + "employer_type, title, description, position, address_public, type, salary_type, salary, "
                        + "min_age, max_age, experience_required, insurance_required, status, headcount, "
                        + "start_date_time, end_date_time, pricing_version, currency, worker_payout, "
                        + "commission_rate, commission_amount, total_charged) "
                        + "values (?, now(), false, ?, ?, ?, ?, ?, 'Açıklama', 'Pozisyon', false, 'ONE_TIME', "
                        + "'HOURLY', 100, 18, 65, false, true, 'OPEN', 1, now() - interval '2 hours', "
                        + "now() + interval '1 day', 'v1', 'TRY', 100.00, 0.1000, 10.00, 110.00)",
                id, employerId, company, categoryId, employerType, "Kimlik ilanı " + id);
    }

    private void insertAssignment(UUID id, UUID job, UUID worker, String status) {
        jdbc.update("insert into job_assignments (id, created_at, is_deleted, job_id, worker_id, status, "
                        + "payment_status, work_hours, total_charged) "
                        + "values (?, now(), false, ?, ?, ?, 'HELD_IN_ESCROW', 0, 110.00)",
                id, job, worker, status);
    }

    private UserPrincipal principal(UUID id) {
        return new UserPrincipal(id, id + "@example.com", Set.of(Role.ROLE_USER), null);
    }
}
