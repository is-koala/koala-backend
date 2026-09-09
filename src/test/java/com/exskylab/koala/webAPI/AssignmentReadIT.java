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

import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class AssignmentReadIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

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
        jdbc.update("delete from job_assignments where id = ?", assignmentId);
        jdbc.update("delete from jobs where id = ?", jobId);
        jdbc.update("delete from job_categories where id = ?", categoryId);
        jdbc.update("delete from addresses where id = ?", addressId);
        jdbc.update("delete from districts where id = ?", districtId);
        jdbc.update("delete from cities where id = ?", cityId);
        jdbc.update("delete from countries where id = ?", countryId);
        jdbc.update("delete from company_contacts where company_id = ?", companyId);
        jdbc.update("delete from companies where id = ?", companyId);
        jdbc.update("delete from authorities where user_id in (?, ?, ?, ?)",
                employerId, workerId, companyAdminId, outsiderId);
        jdbc.update("delete from users where id in (?, ?, ?, ?)",
                employerId, workerId, companyAdminId, outsiderId);
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
    void aCompanyAdminReadsTheCompanyAssignment() throws Exception {
        getAssignmentAs(companyAdminId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(assignmentId.toString()))
                .andExpect(jsonPath("$.data.job.companyName").value("Atama Şirketi " + companyId))
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
