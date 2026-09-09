package com.exskylab.koala.webAPI;

import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class JobCompanyFilterIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID firstCompanyId = UUID.randomUUID();
    private final UUID secondCompanyId = UUID.randomUUID();
    private final UUID countryId = UUID.randomUUID();
    private final UUID cityId = UUID.randomUUID();
    private final UUID districtId = UUID.randomUUID();
    private final UUID addressId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final UUID otherCompanyJobId = UUID.randomUUID();
    private final UUID individualJobId = UUID.randomUUID();
    private final UUID closedJobId = UUID.randomUUID();
    private final List<UUID> firstCompanyJobIds = new ArrayList<>();
    private final String query = "Şirket kimliği " + UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbc.update("insert into users (id, created_at, is_deleted, email, password, is_employer, "
                        + "email_verified, phone_verified, identity_verified) "
                        + "values (?, now(), false, ?, 'test', true, true, false, false)",
                ownerId, ownerId + "@example.com");
        insertCompany(firstCompanyId);
        insertCompany(secondCompanyId);

        jdbc.update("insert into countries (id, created_at, is_deleted, name, code) values (?, now(), false, ?, ?)",
                countryId, "Türkiye", "TR");
        jdbc.update("insert into cities (id, created_at, is_deleted, name, country_id) values (?, now(), false, ?, ?)",
                cityId, "İstanbul", countryId);
        jdbc.update("insert into districts (id, created_at, is_deleted, name, city_id) values (?, now(), false, ?, ?)",
                districtId, "Kadıköy", cityId);
        jdbc.update("insert into addresses (id, created_at, is_deleted, country_id, city_id, district_id, "
                        + "latitude, longitude, canonical_version, needs_review) "
                        + "values (?, now(), false, ?, ?, ?, 40.98, 29.03, 1, false)",
                addressId, countryId, cityId, districtId);
        jdbc.update("insert into job_categories (id, name, code, requires_insurance) values (?, ?, ?, false)",
                categoryId, "Kimlik filtresi", "COMPANY_FILTER_" + categoryId);

        for (int index = 0; index < 21; index++) {
            UUID jobId = UUID.randomUUID();
            firstCompanyJobIds.add(jobId);
            insertJob(jobId, firstCompanyId, "COMPANY", "OPEN", index);
        }
        insertJob(otherCompanyJobId, secondCompanyId, "COMPANY", "OPEN", 30);
        insertJob(individualJobId, null, "INDIVIDUAL", "OPEN", 31);
        insertJob(closedJobId, firstCompanyId, "COMPANY", "CLOSED", 32);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from jobs where title like ?", query + "%");
        jdbc.update("delete from job_categories where id = ?", categoryId);
        jdbc.update("delete from addresses where id = ?", addressId);
        jdbc.update("delete from districts where id = ?", districtId);
        jdbc.update("delete from cities where id = ?", cityId);
        jdbc.update("delete from countries where id = ?", countryId);
        jdbc.update("delete from companies where id in (?, ?)", firstCompanyId, secondCompanyId);
        jdbc.update("delete from authorities where user_id = ?", ownerId);
        jdbc.update("delete from users where id = ?", ownerId);
    }

    @Test
    void aCompanyIdReturnsOnlyThatCompanysOpenJobsEvenWhenAnotherCompanyHasTheSameName() throws Exception {
        mockMvc.perform(get("/jobs")
                        .param("companyId", firstCompanyId.toString())
                        .param("query", query)
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(21))
                .andExpect(jsonPath("$.data.content[*].id", containsInAnyOrder(
                        firstCompanyJobIds.stream().map(UUID::toString).toArray(String[]::new))))
                .andExpect(jsonPath("$.data.content[*].id", not(hasItem(otherCompanyJobId.toString()))))
                .andExpect(jsonPath("$.data.content[*].id", not(hasItem(closedJobId.toString()))));
    }

    @Test
    void omittingCompanyIdPreservesTheExistingSearchCorpus() throws Exception {
        mockMvc.perform(get("/jobs").param("query", query).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(23))
                .andExpect(jsonPath("$.data.content[*].id", hasItem(otherCompanyJobId.toString())))
                .andExpect(jsonPath("$.data.content[*].id", hasItem(individualJobId.toString())));
    }

    @Test
    void anIndividualEmployersJobNeverMatchesACompanyId() throws Exception {
        mockMvc.perform(get("/jobs")
                        .param("companyId", firstCompanyId.toString())
                        .param("query", query)
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].id", not(hasItem(individualJobId.toString()))));
    }

    @Test
    void theCompanyFilterStillPaginatesAfterTwentyJobs() throws Exception {
        mockMvc.perform(get("/jobs")
                        .param("companyId", firstCompanyId.toString())
                        .param("query", query)
                        .param("page", "1")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(21))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(firstCompanyJobIds.get(20).toString()));
    }

    private void insertCompany(UUID id) {
        jdbc.update("insert into companies (id, created_at, is_deleted, name, tax_number, email, approval_status) "
                        + "values (?, now(), false, 'Aynı İsimli Şirket', ?, ?, 'APPROVED')",
                id, String.valueOf(Math.abs(id.hashCode())), id + "@example.com");
    }

    private void insertJob(UUID id, UUID companyId, String employerType, String status, int order) {
        jdbc.update("insert into jobs (id, created_at, is_deleted, creator_id, company_id, job_category_id, "
                        + "employer_type, title, description, position, address_id, address_public, type, "
                        + "salary_type, salary, min_age, max_age, experience_required, insurance_required, "
                        + "status, start_date_time, end_date_time) "
                        + "values (?, now() - (? * interval '1 second'), false, ?, ?, ?, ?, ?, 'Açıklama', "
                        + "'Pozisyon', ?, false, 'ONE_TIME', 'HOURLY', 100, 18, 65, false, false, ?, now(), "
                        + "now() + interval '2 days')",
                id, order, ownerId, companyId, categoryId, employerType, query + " " + order, addressId, status);
    }
}
