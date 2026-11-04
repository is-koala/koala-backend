package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.support.AbstractIntegrationTest;
import com.exskylab.koala.support.QueryCounter;
import jakarta.persistence.EntityManagerFactory;
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

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class JobPricingVisibilityIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID officialId = UUID.randomUUID();
    private final UUID workerId = UUID.randomUUID();
    private final UUID countryId = UUID.randomUUID();
    private final UUID cityId = UUID.randomUUID();
    private final UUID districtId = UUID.randomUUID();
    private final UUID addressId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final UUID secondJobId = UUID.randomUUID();
    private final String marker = "Fiyat gorunurlugu " + UUID.randomUUID();

    @BeforeEach
    void setUp() {
        insertUser(ownerId, "owner@example.com");
        insertUser(officialId, "official@example.com");
        insertUser(workerId, "worker@example.com");
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
        jdbc.update("insert into companies (id, created_at, is_deleted, name, tax_number, email, address_id, approval_status) "
                        + "values (?, now(), false, ?, ?, ?, ?, 'APPROVED')",
                companyId, "Fiyat Şirketi " + companyId, String.valueOf(Math.abs(companyId.hashCode())),
                "company-" + companyId + "@example.com", addressId);
        jdbc.update("insert into company_contacts (id, created_at, is_deleted, company_id, user_id, role) "
                        + "values (?, now(), false, ?, ?, 'ADMIN')",
                UUID.randomUUID(), companyId, officialId);
        jdbc.update("insert into job_categories (id, name, code, requires_insurance) values (?, ?, ?, false)",
                categoryId, "Test", "TEST_" + categoryId);
        insertJob(jobId, marker + " bir");
        insertJob(secondJobId, marker + " iki");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from jobs where id in (?, ?)", jobId, secondJobId);
        jdbc.update("delete from job_categories where id = ?", categoryId);
        jdbc.update("delete from company_contacts where company_id = ?", companyId);
        jdbc.update("delete from companies where id = ?", companyId);
        jdbc.update("delete from addresses where id = ?", addressId);
        jdbc.update("delete from districts where id = ?", districtId);
        jdbc.update("delete from cities where id = ?", cityId);
        jdbc.update("delete from countries where id = ?", countryId);
        jdbc.update("delete from authorities where user_id in (?, ?, ?)", ownerId, officialId, workerId);
        jdbc.update("delete from users where id in (?, ?, ?)", ownerId, officialId, workerId);
    }

    @Test
    void anAnonymousViewerSeesOnlyTheWorkerPayoutInTheJobDetail() throws Exception {
        expectWorkerPricing(mockMvc.perform(get("/jobs/{jobId}", jobId)), "$.data.pricing");
    }

    @Test
    void aWorkerSeesOnlyTheWorkerPayoutInTheJobDetail() throws Exception {
        expectWorkerPricing(mockMvc.perform(get("/jobs/{jobId}", jobId).with(user(principal(workerId)))),
                "$.data.pricing");
    }

    @Test
    void theOwnerSeesTheFullBreakdownInTheJobDetail() throws Exception {
        expectFullPricing(mockMvc.perform(get("/jobs/{jobId}", jobId).with(user(principal(ownerId)))),
                "$.data.pricing");
    }

    @Test
    void aCompanyOfficialWhoDidNotOpenTheJobSeesTheFullBreakdownInTheJobDetail() throws Exception {
        makeCompanyJobs();

        expectFullPricing(mockMvc.perform(get("/jobs/{jobId}", jobId).with(user(principal(officialId)))),
                "$.data.pricing");
    }

    @Test
    void aWorkerStillSeesOnlyTheWorkerPayoutInACompanyJobDetail() throws Exception {
        makeCompanyJobs();

        expectWorkerPricing(mockMvc.perform(get("/jobs/{jobId}", jobId).with(user(principal(workerId)))),
                "$.data.pricing");
    }

    @Test
    void anAnonymousSearchCarriesOnlyTheWorkerPayout() throws Exception {
        expectWorkerPricing(mockMvc.perform(get("/jobs").param("query", marker + " bir")),
                "$.data.content[0].pricing");
    }

    @Test
    void aWorkerSearchCarriesOnlyTheWorkerPayout() throws Exception {
        expectWorkerPricing(mockMvc.perform(get("/jobs").param("query", marker + " bir")
                .with(user(principal(workerId)))), "$.data.content[0].pricing");
    }

    @Test
    void theOwnerSeesTheFullBreakdownInSearchAndInTheirOwnListing() throws Exception {
        expectFullPricing(mockMvc.perform(get("/jobs").param("query", marker + " bir")
                .with(user(principal(ownerId)))), "$.data.content[0].pricing");
        expectFullPricing(mockMvc.perform(get("/users/me/jobs").param("size", "1")
                .with(user(principal(ownerId)))), "$.data.content[0].pricing");
    }

    @Test
    void aCompanyOfficialSeesTheFullBreakdownInTheCompanyListing() throws Exception {
        makeCompanyJobs();

        expectFullPricing(mockMvc.perform(get("/companies/{companyId}/jobs", companyId).param("size", "1")
                .with(user(principal(officialId)))), "$.data.content[0].pricing");
    }

    @Test
    void theViewerSidedPricingDoesNotAddAQueryPerRowInTheSearch() throws Exception {
        for (UUID viewerId : new UUID[] {null, workerId, ownerId}) {
            assertNoQueryPerRow(size -> search(viewerId, size),
                    "ilan araması görüntüleyene göre fiyat kısarken satır sayısıyla büyüyen sorgu atıyor (N+1)");
        }
    }

    @Test
    void theOwnersListingIsBuiltWithoutAQueryPerRow() throws Exception {
        assertNoQueryPerRow(size -> mockMvc.perform(get("/users/me/jobs").param("size", String.valueOf(size))
                        .with(user(principal(ownerId)))),
                "ilanlarım listesi işveren görünümünde satır sayısıyla büyüyen sorgu atıyor (N+1)");
    }

    @Test
    void theCompanyListingIsBuiltWithoutAQueryPerRow() throws Exception {
        makeCompanyJobs();

        assertNoQueryPerRow(size -> mockMvc.perform(get("/companies/{companyId}/jobs", companyId)
                        .param("size", String.valueOf(size)).with(user(principal(officialId)))),
                "şirket ilan listesi işveren görünümünde satır sayısıyla büyüyen sorgu atıyor (N+1)");
    }

    private void assertNoQueryPerRow(PagedRequest request, String message) throws Exception {
        QueryCounter queries = QueryCounter.start(entityManagerFactory);
        try {
            queries.clear();
            request.perform(1).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(1)));
            long statementsForOneRow = queries.getPrepareStatementCount();

            queries.clear();
            request.perform(2).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(2)))
                    .andExpect(jsonPath("$.data.content[0].pricing.workerPayout").value("1000.00"))
                    .andExpect(jsonPath("$.data.content[1].pricing.workerPayout").value("1000.00"));

            assertEquals(statementsForOneRow, queries.getPrepareStatementCount(), message);
            assertEquals(0, queries.getEntityFetchCount(),
                    "ilan listesi fiyat görünümü için satır başına lazy entity yüklüyor");
        } finally {
            queries.close();
        }
    }

    @FunctionalInterface
    private interface PagedRequest {
        ResultActions perform(int size) throws Exception;
    }

    private ResultActions search(UUID viewerId, int size) throws Exception {
        var request = get("/jobs").param("query", marker).param("size", String.valueOf(size));
        if (viewerId != null) {
            request = request.with(user(principal(viewerId)));
        }
        return mockMvc.perform(request);
    }

    private static void expectWorkerPricing(ResultActions result, String path) throws Exception {
        result.andExpect(status().isOk())
                .andExpect(jsonPath(path + ".pricingVersion").value("2026-09"))
                .andExpect(jsonPath(path + ".currency").value("TRY"))
                .andExpect(jsonPath(path + ".workerPayout").value("1000.00"))
                .andExpect(jsonPath(path + ".serviceFeeRate").value(nullValue()))
                .andExpect(jsonPath(path + ".serviceFeeAmount").value(nullValue()))
                .andExpect(jsonPath(path + ".employerTotal").value(nullValue()));
    }

    private static void expectFullPricing(ResultActions result, String path) throws Exception {
        result.andExpect(status().isOk())
                .andExpect(jsonPath(path + ".pricingVersion").value("2026-09"))
                .andExpect(jsonPath(path + ".currency").value("TRY"))
                .andExpect(jsonPath(path + ".workerPayout").value("1000.00"))
                .andExpect(jsonPath(path + ".serviceFeeRate").value("0.0750"))
                .andExpect(jsonPath(path + ".serviceFeeAmount").value("75.00"))
                .andExpect(jsonPath(path + ".employerTotal").value("1075.00"));
    }

    private void makeCompanyJobs() {
        jdbc.update("update jobs set employer_type = 'COMPANY', company_id = ? where id in (?, ?)",
                companyId, jobId, secondJobId);
    }

    private void insertJob(UUID id, String title) {
        jdbc.update("insert into jobs (id, created_at, is_deleted, creator_id, job_category_id, employer_type, "
                        + "title, description, position, address_id, address_public, type, salary_type, salary, "
                        + "min_age, max_age, experience_required, insurance_required, status, start_date_time, "
                        + "end_date_time, pricing_version, currency, worker_payout, commission_rate, "
                        + "commission_amount, total_charged) "
                        + "values (?, now(), false, ?, ?, 'INDIVIDUAL', ?, 'Açıklama', 'Pozisyon', ?, false, "
                        + "'ONE_TIME', 'HOURLY', 1000, 18, 65, false, false, 'OPEN', now(), now() + interval '1 day', "
                        + "'2026-09', 'TRY', 1000.00, 0.0750, 75.00, 1075.00)",
                id, ownerId, categoryId, title, addressId);
    }

    private void insertUser(UUID id, String email) {
        jdbc.update("insert into users (id, created_at, is_deleted, email, password, is_employer, "
                        + "email_verified, phone_verified, identity_verified) "
                        + "values (?, now(), false, ?, 'test', false, true, false, false)", id, email);
    }

    private UserPrincipal principal(UUID id) {
        return new UserPrincipal(id, id + "@example.com", Set.of(Role.ROLE_USER), null);
    }
}
