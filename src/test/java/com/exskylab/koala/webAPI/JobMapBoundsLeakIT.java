package com.exskylab.koala.webAPI;

import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class JobMapBoundsLeakIT extends AbstractIntegrationTest {

    private static final String EXACT_LAT = "40.994999";
    private static final String EXACT_LNG = "29.030000";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final UUID countryId = UUID.randomUUID();
    private final UUID cityId = UUID.randomUUID();
    private final UUID districtId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final List<UUID> addressIds = new ArrayList<>();
    private final String query = "Harita kutusu " + UUID.randomUUID();

    private final UUID individual = UUID.randomUUID();
    private final UUID hiddenCompany = UUID.randomUUID();
    private final UUID publicCompany = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbc.update("insert into users (id, created_at, is_deleted, email, password, is_employer, "
                        + "email_verified, phone_verified, identity_verified) "
                        + "values (?, now(), false, ?, 'test', true, true, false, false)",
                ownerId, ownerId + "@example.com");
        jdbc.update("insert into companies (id, created_at, is_deleted, name, tax_number, email, approval_status) "
                        + "values (?, now(), false, 'Kutu Şirketi', ?, ?, 'APPROVED')",
                companyId, String.valueOf(Math.abs(companyId.hashCode())), companyId + "@example.com");
        jdbc.update("insert into countries (id, created_at, is_deleted, name, code) values (?, now(), false, ?, ?)",
                countryId, "Türkiye", "TR");
        jdbc.update("insert into cities (id, created_at, is_deleted, name, country_id) values (?, now(), false, ?, ?)",
                cityId, "İstanbul", countryId);
        jdbc.update("insert into districts (id, created_at, is_deleted, name, city_id) values (?, now(), false, ?, ?)",
                districtId, "Kadıköy", cityId);
        jdbc.update("insert into job_categories (id, name, code, requires_insurance) values (?, ?, ?, false)",
                categoryId, "Harita kutusu", "MAP_BOUNDS_" + categoryId);

        insertJob(individual, null, false, 0);
        insertJob(hiddenCompany, companyId, false, 1);
        insertJob(publicCompany, companyId, true, 2);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from jobs where title = ?", query);
        jdbc.update("delete from job_categories where id = ?", categoryId);
        for (UUID addressId : addressIds) {
            jdbc.update("delete from addresses where id = ?", addressId);
        }
        jdbc.update("delete from districts where id = ?", districtId);
        jdbc.update("delete from cities where id = ?", cityId);
        jdbc.update("delete from countries where id = ?", countryId);
        jdbc.update("delete from companies where id = ?", companyId);
        jdbc.update("delete from authorities where user_id = ?", ownerId);
        jdbc.update("delete from users where id = ?", ownerId);
    }

    @Test
    void aBoxThatHoldsOnlyTheExactSpotDoesNotFindAHiddenAddress() throws Exception {
        searchWithin("40.994900", "40.995100", "29.029900", "29.030100")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].id", contains(publicCompany.toString())));
    }

    @Test
    void aHiddenAddressIsFoundOnlyWhereItsPublicCellIs() throws Exception {
        searchWithin("40.989900", "40.990100", "29.029900", "29.030100")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].id",
                        containsInAnyOrder(individual.toString(), hiddenCompany.toString())));
    }

    @Test
    void narrowingTheBoxCannotSplitTheRoundingCell() throws Exception {
        searchWithin("40.985000", "40.994000", "29.025000", "29.035000")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].id",
                        containsInAnyOrder(individual.toString(), hiddenCompany.toString())));

        searchWithin("40.990001", "40.999999", "29.025000", "29.035000")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].id", contains(publicCompany.toString())));

        searchWithin("40.994950", "40.994999", "29.029999", "29.030000")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].id", contains(publicCompany.toString())));

        searchWithin("40.995001", "40.999999", "29.029999", "29.030001")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].id", empty()));
    }

    @Test
    void theDistanceOfAHiddenCompanyAddressIsMeasuredFromItsPublicCell() throws Exception {
        mockMvc.perform(get("/jobs")
                        .param("query", query)
                        .param("lat", "40.990000")
                        .param("lng", "29.030000")
                        .param("sort", "distance,asc")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == '" + hiddenCompany + "')].distanceKm", contains(0.0)))
                .andExpect(jsonPath("$.data.content[?(@.id == '" + individual + "')].distanceKm", contains(0.0)))
                .andExpect(jsonPath("$.data.content[?(@.id == '" + publicCompany + "')].distanceKm", contains(0.6)));
    }

    private ResultActions searchWithin(String minLat, String maxLat, String minLng, String maxLng) throws Exception {
        return mockMvc.perform(get("/jobs")
                .param("query", query)
                .param("minLatitude", minLat)
                .param("maxLatitude", maxLat)
                .param("minLongitude", minLng)
                .param("maxLongitude", maxLng)
                .param("size", "10"));
    }

    private void insertJob(UUID id, UUID jobCompanyId, boolean addressPublic, int order) {
        UUID addressId = UUID.randomUUID();
        addressIds.add(addressId);
        jdbc.update("insert into addresses (id, created_at, is_deleted, country_id, city_id, district_id, "
                        + "latitude, longitude, canonical_version, needs_review) "
                        + "values (?, now(), false, ?, ?, ?, ?, ?, 1, false)",
                addressId, countryId, cityId, districtId, new BigDecimal(EXACT_LAT), new BigDecimal(EXACT_LNG));
        jdbc.update("insert into jobs (id, created_at, is_deleted, creator_id, company_id, job_category_id, "
                        + "employer_type, title, description, position, address_id, address_public, type, "
                        + "salary_type, salary, min_age, max_age, experience_required, insurance_required, "
                        + "status, start_date_time, end_date_time) "
                        + "values (?, now() - (? * interval '1 second'), false, ?, ?, ?, ?, ?, 'Açıklama', "
                        + "'Pozisyon', ?, ?, 'ONE_TIME', 'HOURLY', 100, 18, 65, false, false, 'OPEN', now(), "
                        + "now() + interval '1 day')",
                id, order, ownerId, jobCompanyId, categoryId, jobCompanyId == null ? "INDIVIDUAL" : "COMPANY",
                query, addressId, addressPublic);
    }
}
