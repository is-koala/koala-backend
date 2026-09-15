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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "rate-limit.enabled=true",
        "rate-limit.identity-reveal.max-attempts=2",
        "rate-limit.identity-reveal.window=1h"
})
class WorkerIdentityRevealRateLimitIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final UUID employerId = UUID.randomUUID();
    private final UUID otherEmployerId = UUID.randomUUID();
    private final UUID workerId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final UUID otherJobId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();
    private final UUID otherAssignmentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        insertUser(employerId, false);
        insertUser(otherEmployerId, false);
        insertUser(workerId, true);
        jdbc.update("update users set tc_identity_number = '10000000146', birth_date = '1990-05-17' where id = ?",
                workerId);
        jdbc.update("insert into job_categories (id, name, code, requires_insurance) values (?, ?, ?, false)",
                categoryId, "Kimlik sınırı", "IDENTITY_REVEAL_LIMIT_" + categoryId);
        insertJob(jobId, employerId);
        insertJob(otherJobId, otherEmployerId);
        insertAssignment(assignmentId, jobId);
        insertAssignment(otherAssignmentId, otherJobId);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from identity_reveals where worker_id = ?", workerId);
        jdbc.update("delete from notifications where recipient_id in (?, ?, ?) "
                        + "or sender_id in (?, ?, ?) or created_by_id in (?, ?, ?)",
                employerId, otherEmployerId, workerId, employerId, otherEmployerId, workerId,
                employerId, otherEmployerId, workerId);
        jdbc.update("delete from job_assignments where id in (?, ?)", assignmentId, otherAssignmentId);
        jdbc.update("delete from jobs where id in (?, ?)", jobId, otherJobId);
        jdbc.update("delete from job_categories where id = ?", categoryId);
        jdbc.update("delete from authorities where user_id in (?, ?, ?)", employerId, otherEmployerId, workerId);
        jdbc.update("delete from users where id in (?, ?, ?)", employerId, otherEmployerId, workerId);
    }

    @Test
    void anEmployerIsCutOffAfterTheHourlyBudgetAndTheRefusalIsNotRecorded() throws Exception {
        reveal(assignmentId, employerId).andExpect(status().isOk());
        reveal(assignmentId, employerId).andExpect(status().isOk());

        reveal(assignmentId, employerId)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.errorCode").value("IDENTITY_REVEAL_TOO_MANY_REQUESTS"))
                .andExpect(jsonPath("$.data").doesNotExist());

        assertEquals(2, jdbc.queryForObject("select count(*) from identity_reveals where viewer_id = ?",
                Integer.class, employerId), "reddedilen çağrı kayda geçmemeli");

        reveal(otherAssignmentId, otherEmployerId).andExpect(status().isOk());
    }

    private ResultActions reveal(UUID assignment, UUID actorId) throws Exception {
        return mockMvc.perform(get("/job-assignments/{assignmentId}/worker-identity", assignment)
                .with(user(new UserPrincipal(actorId, actorId + "@example.com", Set.of(Role.ROLE_USER), null))));
    }

    private void insertUser(UUID id, boolean identityVerified) {
        jdbc.update("insert into users (id, created_at, is_deleted, email, password, first_name, last_name, "
                        + "is_employer, email_verified, phone_verified, identity_verified) "
                        + "values (?, now(), false, ?, 'test', 'Ad', 'Soyad', false, true, false, ?)",
                id, id + "@example.com", identityVerified);
    }

    private void insertJob(UUID id, UUID creator) {
        jdbc.update("insert into jobs (id, created_at, is_deleted, creator_id, job_category_id, employer_type, "
                        + "title, description, position, address_public, type, salary_type, salary, "
                        + "min_age, max_age, experience_required, insurance_required, status, headcount, "
                        + "start_date_time, end_date_time, pricing_version, currency, worker_payout, "
                        + "commission_rate, commission_amount, total_charged) "
                        + "values (?, now(), false, ?, ?, 'INDIVIDUAL', ?, 'Açıklama', 'Pozisyon', false, "
                        + "'ONE_TIME', 'HOURLY', 100, 18, 65, false, true, 'OPEN', 1, now() - interval '2 hours', "
                        + "now() + interval '1 day', 'v1', 'TRY', 100.00, 0.1000, 10.00, 110.00)",
                id, creator, categoryId, "Sınır ilanı " + id);
    }

    private void insertAssignment(UUID id, UUID job) {
        jdbc.update("insert into job_assignments (id, created_at, is_deleted, job_id, worker_id, status, "
                        + "payment_status, work_hours, total_charged) "
                        + "values (?, now(), false, ?, ?, 'CONFIRMED', 'HELD_IN_ESCROW', 0, 110.00)",
                id, job, workerId);
    }
}
