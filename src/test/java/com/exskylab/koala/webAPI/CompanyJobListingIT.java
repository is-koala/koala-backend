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
import org.springframework.test.web.servlet.ResultActions;

import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class CompanyJobListingIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final UUID companyId = UUID.randomUUID();
    private final UUID firstContactId = UUID.randomUUID();
    private final UUID secondContactId = UUID.randomUUID();
    private final UUID outsiderId = UUID.randomUUID();
    private final UUID countryId = UUID.randomUUID();
    private final UUID cityId = UUID.randomUUID();
    private final UUID districtId = UUID.randomUUID();
    private final UUID addressId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        insertUser(firstContactId);
        insertUser(secondContactId);
        insertUser(outsiderId);

        jdbc.update("insert into companies (id, created_at, is_deleted, name, tax_number, email, approval_status) "
                        + "values (?, now(), false, ?, ?, ?, 'APPROVED')",
                companyId, "Ilan Test " + companyId, String.valueOf(Math.abs(companyId.hashCode())),
                "company-" + companyId + "@example.com");
        insertContact(firstContactId, "ADMIN");
        insertContact(secondContactId, "STAFF");

        jdbc.update("insert into countries (id, created_at, is_deleted, name, code) values (?, now(), false, ?, ?)",
                countryId, "Türkiye", "TR");
        jdbc.update("insert into cities (id, created_at, is_deleted, name, country_id) values (?, now(), false, ?, ?)",
                cityId, "İstanbul", countryId);
        jdbc.update("insert into districts (id, created_at, is_deleted, name, city_id) values (?, now(), false, ?, ?)",
                districtId, "Kadıköy", cityId);
        jdbc.update("insert into addresses (id, created_at, is_deleted, country_id, city_id, district_id, "
                        + "neighborhood, thoroughfare, building_number, unit_number, latitude, longitude, "
                        + "canonical_version, needs_review) values (?, now(), false, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, false)",
                addressId, countryId, cityId, districtId, "Moda", "Caferağa Sokak", "12", "4",
                40.987654, 29.034567);
        jdbc.update("insert into job_categories (id, name, code, requires_insurance) values (?, ?, ?, false)",
                categoryId, "Test", "TEST_" + categoryId);
        jdbc.update("insert into jobs (id, created_at, is_deleted, creator_id, company_id, job_category_id, "
                        + "employer_type, title, description, position, address_id, address_public, type, "
                        + "salary_type, salary, min_age, max_age, experience_required, insurance_required, "
                        + "status, start_date_time, end_date_time) "
                        + "values (?, now(), false, ?, ?, ?, 'COMPANY', ?, 'Açıklama', 'Pozisyon', ?, false, "
                        + "'ONE_TIME', 'HOURLY', 100, 18, 65, false, false, 'OPEN', now(), now() + interval '1 day')",
                jobId, firstContactId, companyId, categoryId, "Sirket ilani " + jobId, addressId);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from jobs where id = ?", jobId);
        jdbc.update("delete from job_categories where id = ?", categoryId);
        jdbc.update("delete from addresses where id = ?", addressId);
        jdbc.update("delete from districts where id = ?", districtId);
        jdbc.update("delete from cities where id = ?", cityId);
        jdbc.update("delete from countries where id = ?", countryId);
        jdbc.update("delete from company_contacts where company_id = ?", companyId);
        jdbc.update("delete from companies where id = ?", companyId);
        jdbc.update("delete from authorities where user_id in (?, ?, ?)",
                firstContactId, secondContactId, outsiderId);
        jdbc.update("delete from users where id in (?, ?, ?)", firstContactId, secondContactId, outsiderId);
    }

    @Test
    void theSecondContactListsTheJobTheFirstContactOpened() throws Exception {
        listCompanyJobsAs(secondContactId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(jobId.toString()))
                .andExpect(jsonPath("$.data.content[0].employerType").value("COMPANY"));
    }

    @Test
    void theTwoListingsAnswerDifferentQuestions() throws Exception {
        mockMvc.perform(get("/users/me/jobs").with(user(principal(secondContactId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(get("/users/me/jobs").with(user(principal(firstContactId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(jobId.toString()));
    }

    @Test
    void aUserWhoIsNotAContactOfTheCompanyIsForbidden() throws Exception {
        listCompanyJobsAs(outsiderId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("JOB_USER_IS_NOT_A_CONTACT_OF_COMPANY"));
    }

    @Test
    void aSortFieldOutsideTheWhitelistIsRejected() throws Exception {
        mockMvc.perform(get("/companies/{companyId}/jobs", companyId)
                        .param("sort", "creator,desc")
                        .with(user(principal(secondContactId))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PAGE_SORT_FIELD_NOT_ALLOWED"));
    }

    @Test
    void theAddressRuleOfAdr0003StillAppliesOnThisEndpoint() throws Exception {
        listCompanyJobsAs(secondContactId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].addressPrecision").value("APPROXIMATE"))
                .andExpect(jsonPath("$.data.content[0].latitude").value(40.99))
                .andExpect(jsonPath("$.data.content[0].longitude").value(29.03));

        jdbc.update("update jobs set address_public = true where id = ?", jobId);

        listCompanyJobsAs(secondContactId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].addressPrecision").value("EXACT"))
                .andExpect(jsonPath("$.data.content[0].latitude").value(40.987654))
                .andExpect(jsonPath("$.data.content[0].longitude").value(29.034567));
    }

    private ResultActions listCompanyJobsAs(UUID actorId) throws Exception {
        return mockMvc.perform(get("/companies/{companyId}/jobs", companyId).with(user(principal(actorId))));
    }

    private void insertContact(UUID userId, String role) {
        jdbc.update("insert into company_contacts (id, created_at, is_deleted, company_id, user_id, role) "
                        + "values (?, now(), false, ?, ?, ?)", UUID.randomUUID(), companyId, userId, role);
    }

    private void insertUser(UUID id) {
        jdbc.update("insert into users (id, created_at, is_deleted, email, password, is_employer, "
                        + "email_verified, phone_verified, identity_verified) "
                        + "values (?, now(), false, ?, 'test', true, true, false, false)", id, email(id));
    }

    private String email(UUID id) {
        return id + "@example.com";
    }

    private UserPrincipal principal(UUID id) {
        return new UserPrincipal(id, email(id), Set.of(Role.ROLE_USER), null);
    }
}
