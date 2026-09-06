package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@AutoConfigureMockMvc
class ExistenceConcealmentIT extends AbstractIntegrationTest {

    @FunctionalInterface
    private interface Attempt {
        ResultActions against(UUID resourceId) throws Exception;
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper objectMapper;

    private final UUID employerId = UUID.randomUUID();
    private final UUID workerId = UUID.randomUUID();
    private final UUID outsiderId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();
    private final UUID templateId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        insertUser(employerId);
        insertUser(workerId);
        insertUser(outsiderId);

        jdbc.update("insert into job_categories (id, name, code, requires_insurance) values (?, ?, ?, false)",
                categoryId, "Gizleme", "CONCEAL_" + categoryId);
        jdbc.update("insert into jobs (id, created_at, is_deleted, creator_id, job_category_id, employer_type, "
                        + "title, description, position, address_public, type, salary_type, salary, "
                        + "min_age, max_age, experience_required, insurance_required, status, "
                        + "headcount, start_date_time, end_date_time, pricing_version, currency, worker_payout, "
                        + "commission_rate, commission_amount, total_charged) "
                        + "values (?, now(), false, ?, ?, 'INDIVIDUAL', ?, 'Açıklama', 'Pozisyon', false, "
                        + "'ONE_TIME', 'HOURLY', 100, 18, 65, false, false, 'OPEN', 1, now() - interval '2 hours', "
                        + "now() + interval '1 day', 'v1', 'TRY', 100.00, 0.1000, 10.00, 110.00)",
                jobId, employerId, categoryId, "Gizleme testi " + jobId);
        jdbc.update("insert into job_assignments (id, created_at, is_deleted, job_id, worker_id, status, "
                        + "payment_status, work_hours, total_charged) "
                        + "values (?, now(), false, ?, ?, 'IN_PROGRESS', 'HELD_IN_ESCROW', 0, 110.00)",
                assignmentId, jobId, workerId);
        jdbc.update("insert into conversations (id, created_at, is_deleted, job_id, employer_id, worker_id, kind) "
                        + "values (?, now(), false, ?, ?, ?, 'DIRECT')",
                conversationId, jobId, employerId, workerId);
        jdbc.update("insert into conversation_participants (id, created_at, is_deleted, conversation_id, user_id) "
                + "values (?, now(), false, ?, ?)", UUID.randomUUID(), conversationId, employerId);
        jdbc.update("insert into conversation_participants (id, created_at, is_deleted, conversation_id, user_id) "
                + "values (?, now(), false, ?, ?)", UUID.randomUUID(), conversationId, workerId);
        jdbc.update("insert into job_templates (id, created_at, is_deleted, owner_id, template_name, title) "
                + "values (?, now(), false, ?, ?, ?)", templateId, employerId, "Şablon", "Şablon başlığı");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from job_templates where owner_id = ?", employerId);
        jdbc.update("delete from conversation_participants where conversation_id = ?", conversationId);
        jdbc.update("delete from messages where conversation_id = ?", conversationId);
        jdbc.update("delete from conversations where id = ?", conversationId);
        jdbc.update("delete from work_sessions where job_assignment_id = ?", assignmentId);
        jdbc.update("delete from reviews where job_assignment_id = ?", assignmentId);
        jdbc.update("delete from cancellations where job_assignment_id = ?", assignmentId);
        jdbc.update("delete from job_assignments where id = ?", assignmentId);
        jdbc.update("delete from jobs where id = ?", jobId);
        jdbc.update("delete from job_categories where id = ?", categoryId);
        jdbc.update("delete from notifications where recipient_id in (?, ?, ?) "
                        + "or sender_id in (?, ?, ?) or created_by_id in (?, ?, ?)",
                employerId, workerId, outsiderId, employerId, workerId, outsiderId,
                employerId, workerId, outsiderId);
        jdbc.update("delete from authorities where user_id in (?, ?, ?)", employerId, workerId, outsiderId);
        jdbc.update("delete from users where id in (?, ?, ?)", employerId, workerId, outsiderId);
    }

    @Test
    void readingSomeoneElsesConversationLooksExactlyLikeReadingOneThatDoesNotExist() throws Exception {
        assertIndistinguishable(conversationId,
                id -> mockMvc.perform(get("/conversations/{id}/messages", id).with(user(principal(outsiderId)))));
    }

    @Test
    void previewingSomeoneElsesCancellationLooksExactlyLikePreviewingOneThatDoesNotExist() throws Exception {
        assertIndistinguishable(assignmentId,
                id -> mockMvc.perform(get("/job-assignments/{id}/cancellation-preview", id)
                        .with(user(principal(outsiderId)))));
    }

    @Test
    void readingSomeoneElsesCancellationLooksExactlyLikeReadingOneThatDoesNotExist() throws Exception {
        assertIndistinguishable(assignmentId,
                id -> mockMvc.perform(get("/job-assignments/{id}/cancellation", id)
                        .with(user(principal(outsiderId)))));
    }

    @Test
    void cancellingSomeoneElsesAssignmentLooksExactlyLikeCancellingOneThatDoesNotExist() throws Exception {
        assertIndistinguishable(assignmentId,
                id -> mockMvc.perform(post("/job-assignments/{id}/cancel", id)
                        .with(user(principal(outsiderId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"olmadı\"}")));
    }

    @Test
    void readingSomeoneElsesTimesheetLooksExactlyLikeReadingOneThatDoesNotExist() throws Exception {
        assertIndistinguishable(assignmentId,
                id -> mockMvc.perform(get("/job-assignments/{id}/work-sessions", id)
                        .with(user(principal(outsiderId)))));
    }

    @Test
    void checkingIntoSomeoneElsesAssignmentLooksExactlyLikeCheckingIntoOneThatDoesNotExist() throws Exception {
        assertIndistinguishable(assignmentId,
                id -> mockMvc.perform(post("/job-assignments/{id}/check-in", id)
                        .with(user(principal(outsiderId)))));
    }

    @Test
    void completingSomeoneElsesAssignmentLooksExactlyLikeCompletingOneThatDoesNotExist() throws Exception {
        assertIndistinguishable(assignmentId,
                id -> mockMvc.perform(post("/job-assignments/{id}/complete", id)
                        .with(user(principal(outsiderId)))));
    }

    @Test
    void reviewingSomeoneElsesAssignmentLooksExactlyLikeReviewingOneThatDoesNotExist() throws Exception {
        assertIndistinguishable(assignmentId,
                id -> mockMvc.perform(post("/job-assignments/{id}/reviews", id)
                        .with(user(principal(outsiderId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5}")));
    }

    @Test
    void readingSomeoneElsesJobTemplateLooksExactlyLikeReadingOneThatDoesNotExist() throws Exception {
        assertIndistinguishable(templateId,
                id -> mockMvc.perform(get("/users/me/job-templates/{id}", id).with(user(principal(outsiderId)))));
    }

    private void assertIndistinguishable(UUID real, Attempt attempt) throws Exception {
        UUID phantom = UUID.randomUUID();

        assertEquals(envelope(attempt.against(phantom), phantom), envelope(attempt.against(real), real),
                "Var olmayan kaynağın yanıtı ile başkasına ait kaynağın yanıtı ayırt edilebiliyor");
    }

    private String envelope(ResultActions actions, UUID resourceId) throws Exception {
        String body = actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        Map<String, Object> parsed =
                new TreeMap<>(objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {
                }));
        parsed.remove("timeStamp");
        parsed.remove("requestId");
        parsed.remove("correlationId");
        return parsed.toString().replace(resourceId.toString(), "{istenen-id}");
    }

    private void insertUser(UUID id) {
        jdbc.update("insert into users (id, created_at, is_deleted, email, password, is_employer, "
                + "email_verified, phone_verified, identity_verified) "
                + "values (?, now(), false, ?, 'test', false, true, false, false)", id, email(id));
    }

    private String email(UUID id) {
        return id + "@example.com";
    }

    private UserPrincipal principal(UUID id) {
        return new UserPrincipal(id, email(id), Set.of(Role.ROLE_USER), null);
    }
}
