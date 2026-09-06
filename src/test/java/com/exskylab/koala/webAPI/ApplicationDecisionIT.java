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

import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ApplicationDecisionIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID firstWorkerId = UUID.randomUUID();
    private final UUID secondWorkerId = UUID.randomUUID();
    private final UUID thirdWorkerId = UUID.randomUUID();
    private final UUID countryId = UUID.randomUUID();
    private final UUID cityId = UUID.randomUUID();
    private final UUID districtId = UUID.randomUUID();
    private final UUID addressId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID firstApplicationId = UUID.randomUUID();
    private final UUID secondApplicationId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        insertUser(ownerId);
        insertUser(firstWorkerId);
        insertUser(secondWorkerId);
        insertUser(thirdWorkerId);

        jdbc.update("insert into countries (id, created_at, is_deleted, name, code) values (?, now(), false, ?, ?)",
                countryId, "Türkiye", "TR");
        jdbc.update("insert into cities (id, created_at, is_deleted, name, country_id) values (?, now(), false, ?, ?)",
                cityId, "İstanbul", countryId);
        jdbc.update("insert into districts (id, created_at, is_deleted, name, city_id) values (?, now(), false, ?, ?)",
                districtId, "Kadıköy", cityId);
        jdbc.update("insert into addresses (id, created_at, is_deleted, country_id, city_id, district_id, "
                        + "neighborhood, thoroughfare, building_number, unit_number, latitude, longitude, "
                        + "canonical_version, needs_review) values (?, now(), false, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, false)",
                addressId, countryId, cityId, districtId, "Moda", "Örnek Sokak", "7", "3",
                40.123456, 29.654321);
        jdbc.update("insert into job_categories (id, name, code, requires_insurance) values (?, ?, ?, false)",
                categoryId, "Test", "TEST_" + categoryId);

        insertJob(1);
        insertApplication(firstApplicationId, firstWorkerId);
        insertApplication(secondApplicationId, secondWorkerId);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from job_assignments where job_id = ?", jobId);
        jdbc.update("delete from job_applications where job_id = ?", jobId);
        jdbc.update("delete from jobs where id = ?", jobId);
        jdbc.update("delete from companies where id = ?", companyId);
        jdbc.update("delete from job_categories where id = ?", categoryId);
        jdbc.update("delete from addresses where id = ?", addressId);
        jdbc.update("delete from districts where id = ?", districtId);
        jdbc.update("delete from cities where id = ?", cityId);
        jdbc.update("delete from countries where id = ?", countryId);
        jdbc.update("delete from notifications where recipient_id in (?, ?, ?, ?) "
                        + "or sender_id in (?, ?, ?, ?) or created_by_id in (?, ?, ?, ?)",
                ownerId, firstWorkerId, secondWorkerId, thirdWorkerId,
                ownerId, firstWorkerId, secondWorkerId, thirdWorkerId,
                ownerId, firstWorkerId, secondWorkerId, thirdWorkerId);
        jdbc.update("delete from authorities where user_id in (?, ?, ?, ?)", ownerId, firstWorkerId, secondWorkerId, thirdWorkerId);
        jdbc.update("delete from users where id in (?, ?, ?, ?)", ownerId, firstWorkerId, secondWorkerId, thirdWorkerId);
    }

    @Test
    void anOfferStillWaitingForItsWorkerHoldsTheOnlyOpening() throws Exception {
        accept(firstApplicationId).andExpect(status().isOk());
        assertAssignmentStatus(firstWorkerId, "PENDING_CONFIRMATION");

        accept(secondApplicationId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("JOB_APPLICATION_JOB_HEADCOUNT_FULL"));

        assertAssignmentCount(1);
    }

    @Test
    void aCompletedAssignmentStillHoldsItsOpening() throws Exception {
        accept(firstApplicationId).andExpect(status().isOk());
        jdbc.update("update job_assignments set status = 'COMPLETED' where job_id = ?", jobId);

        accept(secondApplicationId).andExpect(status().isConflict());

        assertAssignmentCount(1);
    }

    @Test
    void aRejectedOfferGivesTheOpeningBack() throws Exception {
        accept(firstApplicationId).andExpect(status().isOk());
        jdbc.update("update job_assignments set status = 'REJECTED' where job_id = ?", jobId);

        accept(secondApplicationId).andExpect(status().isOk());

        assertAssignmentStatus(secondWorkerId, "PENDING_CONFIRMATION");
    }

    @Test
    void aCancelledAssignmentGivesTheOpeningBack() throws Exception {
        accept(firstApplicationId).andExpect(status().isOk());
        jdbc.update("update job_assignments set status = 'CANCELLED' where job_id = ?", jobId);

        accept(secondApplicationId).andExpect(status().isOk());
    }

    @Test
    void aTwoPersonJobTakesASecondWorkerButNotAThird() throws Exception {
        jdbc.update("update jobs set headcount = 2 where id = ?", jobId);

        accept(firstApplicationId).andExpect(status().isOk());
        accept(secondApplicationId).andExpect(status().isOk());
        assertAssignmentCount(2);

        UUID thirdApplicationId = UUID.randomUUID();
        insertApplication(thirdApplicationId, thirdWorkerId);

        accept(thirdApplicationId).andExpect(status().isConflict());
        assertAssignmentCount(2);
    }

    @Test
    void bothAcceptedWorkersSeeTheFullAddressAndAnOutsiderDoesNot() throws Exception {
        jdbc.update("update jobs set headcount = 2 where id = ?", jobId);

        accept(firstApplicationId).andExpect(status().isOk());
        accept(secondApplicationId).andExpect(status().isOk());

        for (UUID workerId : new UUID[]{firstWorkerId, secondWorkerId}) {
            mockMvc.perform(get("/jobs/{jobId}", jobId).with(user(principal(workerId))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.addressPrecision").value("EXACT"))
                    .andExpect(jsonPath("$.data.address.components.thoroughfare").value("Örnek Sokak"))
                    .andExpect(jsonPath("$.data.address.components.buildingNumber").value("7"))
                    .andExpect(jsonPath("$.data.address.position.latitude").value(40.123456));
        }

        mockMvc.perform(get("/jobs/{jobId}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.addressPrecision").value("APPROXIMATE"))
                .andExpect(jsonPath("$.data.address.components.thoroughfare").doesNotExist());
    }

    @Test
    void aRefusedAcceptanceNeverOpensTheAddress() throws Exception {
        accept(firstApplicationId).andExpect(status().isOk());

        accept(secondApplicationId).andExpect(status().isConflict());

        mockMvc.perform(get("/jobs/{jobId}", jobId).with(user(principal(secondWorkerId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.addressPrecision").value("APPROXIMATE"))
                .andExpect(jsonPath("$.data.address.components.thoroughfare").doesNotExist())
                .andExpect(jsonPath("$.data.address.components.buildingNumber").doesNotExist());
    }

    @Test
    void aCancelledJobRefusesTheDecisionAndCreatesNoAssignment() throws Exception {
        jdbc.update("update jobs set status = 'CANCELLED' where id = ?", jobId);

        accept(firstApplicationId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("JOB_APPLICATION_JOB_CANCELLED"));

        assertAssignmentCount(0);
    }

    @Test
    void aClosedJobStillLetsTheOwnerAnswerAnExistingApplication() throws Exception {
        jdbc.update("update jobs set status = 'CLOSED' where id = ?", jobId);

        accept(firstApplicationId).andExpect(status().isOk());

        assertAssignmentCount(1);
    }

    @Test
    void aClosedJobTakesNoNewApplication() throws Exception {
        jdbc.update("update jobs set status = 'CLOSED' where id = ?", jobId);

        mockMvc.perform(post("/jobs/{jobId}/applications", jobId)
                        .with(user(principal(firstWorkerId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"notes\":\"merhaba\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("JOB_APPLICATION_JOB_NOT_OPEN"));
    }

    @Test
    void anOutsiderCannotTellAnAnsweredApplicationFromAMissingOne() throws Exception {
        accept(firstApplicationId).andExpect(status().isOk());

        respondAsOutsider(firstApplicationId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_APPLICATION_NOT_FOUND"));

        respondAsOutsider(UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_APPLICATION_NOT_FOUND"));
    }

    @Test
    void anOutsiderCannotTellACompanyJobApplicationFromAnIndividualOne() throws Exception {
        jdbc.update("insert into companies (id, created_at, is_deleted, name, tax_number, email, approval_status) "
                        + "values (?, now(), false, ?, ?, ?, 'APPROVED')",
                companyId, "Test Sirket " + companyId, String.valueOf(Math.abs(companyId.hashCode())),
                "company-" + companyId + "@example.com");
        jdbc.update("update jobs set employer_type = 'COMPANY', company_id = ? where id = ?", companyId, jobId);

        respondAsOutsider(firstApplicationId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("JOB_APPLICATION_NOT_FOUND"));
    }

    private org.springframework.test.web.servlet.ResultActions respondAsOutsider(UUID applicationId) throws Exception {
        return mockMvc.perform(post("/job-applications/{id}/respond", applicationId)
                .with(user(principal(secondWorkerId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACCEPTED\"}"));
    }

    @Test
    void theOwnerAnsweringTwiceGetsAConflict() throws Exception {
        accept(firstApplicationId).andExpect(status().isOk());

        accept(firstApplicationId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("JOB_APPLICATION_ALREADY_RESPONDED"));
    }

    private org.springframework.test.web.servlet.ResultActions accept(UUID applicationId) throws Exception {
        return mockMvc.perform(post("/job-applications/{id}/respond", applicationId)
                .with(user(principal(ownerId)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACCEPTED\"}"));
    }

    private void assertAssignmentCount(int expected) {
        Integer actual = jdbc.queryForObject(
                "select count(*) from job_assignments where job_id = ? and is_deleted = false",
                Integer.class, jobId);
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }

    private void assertAssignmentStatus(UUID workerId, String expected) {
        String actual = jdbc.queryForObject(
                "select status from job_assignments where job_id = ? and worker_id = ?",
                String.class, jobId, workerId);
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }

    private void insertJob(int headcount) {
        jdbc.update("insert into jobs (id, created_at, is_deleted, creator_id, job_category_id, employer_type, "
                        + "title, description, position, address_id, address_public, type, salary_type, salary, "
                        + "min_age, max_age, experience_required, insurance_required, status, headcount, "
                        + "start_date_time, end_date_time, pricing_version, currency, worker_payout, "
                        + "commission_rate, commission_amount, total_charged) "
                        + "values (?, now(), false, ?, ?, 'INDIVIDUAL', ?, 'Açıklama', 'Pozisyon', ?, false, "
                        + "'ONE_TIME', 'HOURLY', 100, 18, 65, false, false, 'OPEN', ?, now(), "
                        + "now() + interval '1 day', 'v1', 'TRY', 100.00, 0.1000, 10.00, 110.00)",
                jobId, ownerId, categoryId, "Karar testi " + jobId, addressId, headcount);
    }

    private void insertApplication(UUID applicationId, UUID applicantId) {
        jdbc.update("insert into job_applications (id, created_at, is_deleted, job_id, user_id, status, "
                        + "application_date_time) values (?, now(), false, ?, ?, 'APPLIED', now())",
                applicationId, jobId, applicantId);
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
