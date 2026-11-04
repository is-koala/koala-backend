package com.exskylab.koala.webAPI;

import com.exskylab.koala.core.security.UserPrincipal;
import com.exskylab.koala.entities.Role;
import com.exskylab.koala.support.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class JobLocationRuleIT extends AbstractIntegrationTest {

    private static final double EXACT_LAT = 40.987654;
    private static final double EXACT_LNG = 29.034567;
    private static final double ROUNDED_LAT = 40.99;
    private static final double ROUNDED_LNG = 29.03;

    enum Viewer { OWNER, ASSIGNED_WORKER, OTHER_USER, ANONYMOUS }

    enum Kind { PUBLIC_COMPANY, HIDDEN_COMPANY, INDIVIDUAL }

    enum Detail { FULL, STREET_LEVEL, APPROXIMATE }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final UUID ownerId = UUID.randomUUID();
    private final UUID workerId = UUID.randomUUID();
    private final UUID otherId = UUID.randomUUID();
    private final UUID countryId = UUID.randomUUID();
    private final UUID cityId = UUID.randomUUID();
    private final UUID districtId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final UUID companyId = UUID.randomUUID();
    private final String title = "Konum kurali " + UUID.randomUUID();
    private final Map<Kind, UUID> jobs = new EnumMap<>(Kind.class);
    private final Map<Kind, UUID> assignments = new EnumMap<>(Kind.class);
    private final List<UUID> addressIds = new ArrayList<>();

    static Stream<Arguments> matrix() {
        return Stream.of(
                Arguments.of(Viewer.OWNER, Kind.PUBLIC_COMPANY, Detail.FULL, true),
                Arguments.of(Viewer.OWNER, Kind.HIDDEN_COMPANY, Detail.FULL, true),
                Arguments.of(Viewer.OWNER, Kind.INDIVIDUAL, Detail.FULL, true),
                Arguments.of(Viewer.ASSIGNED_WORKER, Kind.PUBLIC_COMPANY, Detail.FULL, true),
                Arguments.of(Viewer.ASSIGNED_WORKER, Kind.HIDDEN_COMPANY, Detail.FULL, false),
                Arguments.of(Viewer.ASSIGNED_WORKER, Kind.INDIVIDUAL, Detail.FULL, false),
                Arguments.of(Viewer.OTHER_USER, Kind.PUBLIC_COMPANY, Detail.STREET_LEVEL, true),
                Arguments.of(Viewer.OTHER_USER, Kind.HIDDEN_COMPANY, Detail.APPROXIMATE, false),
                Arguments.of(Viewer.OTHER_USER, Kind.INDIVIDUAL, Detail.APPROXIMATE, false),
                Arguments.of(Viewer.ANONYMOUS, Kind.PUBLIC_COMPANY, Detail.STREET_LEVEL, true),
                Arguments.of(Viewer.ANONYMOUS, Kind.HIDDEN_COMPANY, Detail.APPROXIMATE, false),
                Arguments.of(Viewer.ANONYMOUS, Kind.INDIVIDUAL, Detail.APPROXIMATE, false));
    }

    @BeforeEach
    void setUp() {
        insertUser(ownerId);
        insertUser(workerId);
        insertUser(otherId);
        jdbc.update("insert into countries (id, created_at, is_deleted, name, code) values (?, now(), false, ?, ?)",
                countryId, "Türkiye", "TR");
        jdbc.update("insert into cities (id, created_at, is_deleted, name, country_id) values (?, now(), false, ?, ?)",
                cityId, "İstanbul", countryId);
        jdbc.update("insert into districts (id, created_at, is_deleted, name, city_id) values (?, now(), false, ?, ?)",
                districtId, "Kadıköy", cityId);
        jdbc.update("insert into companies (id, created_at, is_deleted, name, tax_number, email, approval_status) "
                        + "values (?, now(), false, ?, ?, ?, 'APPROVED')",
                companyId, "Konum Şirketi " + companyId, String.valueOf(Math.abs(companyId.hashCode())),
                "company-" + companyId + "@example.com");
        jdbc.update("insert into company_contacts (id, created_at, is_deleted, company_id, user_id, role) "
                        + "values (?, now(), false, ?, ?, 'ADMIN')", UUID.randomUUID(), companyId, ownerId);
        jdbc.update("insert into job_categories (id, name, code, requires_insurance) values (?, ?, ?, false)",
                categoryId, "Konum", "LOCATION_RULE_" + categoryId);

        insertJob(Kind.PUBLIC_COMPANY, companyId, true, 0);
        insertJob(Kind.HIDDEN_COMPANY, companyId, false, 1);
        insertJob(Kind.INDIVIDUAL, null, false, 2);
        for (Kind kind : Kind.values()) {
            UUID assignmentId = UUID.randomUUID();
            assignments.put(kind, assignmentId);
            jdbc.update("insert into job_assignments (id, created_at, is_deleted, job_id, worker_id, status, "
                            + "payment_status, assignment_date_time, work_hours, worker_payout, commission_rate, "
                            + "commission_amount, total_charged, pricing_version, currency) "
                            + "values (?, now(), false, ?, ?, 'CONFIRMED', 'HELD_IN_ESCROW', now(), 0, 100.00, "
                            + "0.1000, 10.00, 110.00, 'v1', 'TRY')",
                    assignmentId, jobs.get(kind), workerId);
        }
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from job_assignments where worker_id = ?", workerId);
        jdbc.update("delete from jobs where title = ?", title);
        for (UUID addressId : addressIds) {
            jdbc.update("delete from addresses where id = ?", addressId);
        }
        jdbc.update("delete from job_categories where id = ?", categoryId);
        jdbc.update("delete from company_contacts where company_id = ?", companyId);
        jdbc.update("delete from companies where id = ?", companyId);
        jdbc.update("delete from districts where id = ?", districtId);
        jdbc.update("delete from cities where id = ?", cityId);
        jdbc.update("delete from countries where id = ?", countryId);
        jdbc.update("delete from authorities where user_id in (?, ?, ?)", ownerId, workerId, otherId);
        jdbc.update("delete from users where id in (?, ?, ?)", ownerId, workerId, otherId);
    }

    @ParameterizedTest(name = "{0} / {1}: detay {2}")
    @MethodSource("matrix")
    void theJobDetailShowsWhatTheRuleAllows(Viewer viewer, Kind kind, Detail detail, boolean exactOnMap)
            throws Exception {
        ResultActions result = mockMvc.perform(as(viewer, get("/jobs/{jobId}", jobs.get(kind))))
                .andExpect(status().isOk());

        switch (detail) {
            case FULL -> result
                    .andExpect(jsonPath("$.data.addressPrecision").value("EXACT"))
                    .andExpect(jsonPath("$.data.address.components.thoroughfare").value("Caferağa Sokak"))
                    .andExpect(jsonPath("$.data.address.components.buildingNumber").value("12"))
                    .andExpect(jsonPath("$.data.address.components.floor").value("3"))
                    .andExpect(jsonPath("$.data.address.components.unitNumber").value("4"))
                    .andExpect(jsonPath("$.data.address.components.additionalDirections").value("Zil bozuk"))
                    .andExpect(jsonPath("$.data.address.position.latitude").value(EXACT_LAT))
                    .andExpect(jsonPath("$.data.address.displayAddress")
                            .value("Moda, Caferağa Sokak, No: 12, Kat: 3, Daire: 4, Kadıköy/İstanbul, 34710"));
            case STREET_LEVEL -> result
                    .andExpect(jsonPath("$.data.addressPrecision").value("EXACT"))
                    .andExpect(jsonPath("$.data.address.components.thoroughfare").value("Caferağa Sokak"))
                    .andExpect(jsonPath("$.data.address.components.buildingNumber").value("12"))
                    .andExpect(jsonPath("$.data.address.components.postalCode").value("34710"))
                    .andExpect(jsonPath("$.data.address.components.floor").doesNotExist())
                    .andExpect(jsonPath("$.data.address.components.unitNumber").doesNotExist())
                    .andExpect(jsonPath("$.data.address.components.additionalDirections").doesNotExist())
                    .andExpect(jsonPath("$.data.address.position.latitude").value(EXACT_LAT))
                    .andExpect(jsonPath("$.data.address.position.longitude").value(EXACT_LNG))
                    .andExpect(jsonPath("$.data.address.displayAddress")
                            .value("Moda, Caferağa Sokak, No: 12, Kadıköy/İstanbul, 34710"));
            case APPROXIMATE -> result
                    .andExpect(jsonPath("$.data.addressPrecision").value("APPROXIMATE"))
                    .andExpect(jsonPath("$.data.address.components.neighborhood").value("Moda"))
                    .andExpect(jsonPath("$.data.address.components.thoroughfare").doesNotExist())
                    .andExpect(jsonPath("$.data.address.components.buildingNumber").doesNotExist())
                    .andExpect(jsonPath("$.data.address.components.unitNumber").doesNotExist())
                    .andExpect(jsonPath("$.data.address.position.latitude").value(ROUNDED_LAT))
                    .andExpect(jsonPath("$.data.address.position.longitude").value(ROUNDED_LNG));
        }
    }

    @ParameterizedTest(name = "{0} / {1}: haritada kesin={3}")
    @MethodSource("matrix")
    void theMapListingNeverLetsAnAssignmentRevealAnAddress(Viewer viewer, Kind kind, Detail detail,
                                                          boolean exactOnMap) throws Exception {
        String card = "$.data.content[?(@.id == '" + jobs.get(kind) + "')]";

        mockMvc.perform(as(viewer, get("/jobs").param("query", title).param("size", "10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(Kind.values().length)))
                .andExpect(jsonPath(card + ".addressPrecision", contains(exactOnMap ? "EXACT" : "APPROXIMATE")))
                .andExpect(jsonPath(card + ".latitude", contains(exactOnMap ? EXACT_LAT : ROUNDED_LAT)))
                .andExpect(jsonPath(card + ".longitude", contains(exactOnMap ? EXACT_LNG : ROUNDED_LNG)));
    }

    @Test
    void theCompanyListingFollowsTheMapRuleForAContactWhoIsAlsoTheAssignedWorker() throws Exception {
        jdbc.update("insert into company_contacts (id, created_at, is_deleted, company_id, user_id, role) "
                + "values (?, now(), false, ?, ?, 'STAFF')", UUID.randomUUID(), companyId, workerId);
        String hidden = "$.data.content[?(@.id == '" + jobs.get(Kind.HIDDEN_COMPANY) + "')]";
        String shown = "$.data.content[?(@.id == '" + jobs.get(Kind.PUBLIC_COMPANY) + "')]";

        mockMvc.perform(as(Viewer.ASSIGNED_WORKER, get("/companies/{companyId}/jobs", companyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath(hidden + ".addressPrecision", contains("APPROXIMATE")))
                .andExpect(jsonPath(hidden + ".latitude", contains(ROUNDED_LAT)))
                .andExpect(jsonPath(shown + ".addressPrecision", contains("EXACT")));
    }

    @Test
    void theAssignedWorkerKeepsTheExactLocationOnTheirOwnWorkSurfaces() throws Exception {
        mockMvc.perform(as(Viewer.ASSIGNED_WORKER, get("/users/me/assignments").param("size", "10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(Kind.values().length)))
                .andExpect(jsonPath("$.data.content[*].job.addressPrecision", everyItem(is("EXACT"))))
                .andExpect(jsonPath("$.data.content[*].job.latitude", everyItem(is(EXACT_LAT))));

        mockMvc.perform(as(Viewer.ASSIGNED_WORKER, get("/users/me/work")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items", hasSize(Kind.values().length)))
                .andExpect(jsonPath("$.data.items[*].job.addressPrecision", everyItem(is("EXACT"))));

        for (Kind kind : Kind.values()) {
            mockMvc.perform(as(Viewer.ASSIGNED_WORKER, get("/job-assignments/{id}", assignments.get(kind))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.job.addressPrecision").value("EXACT"))
                    .andExpect(jsonPath("$.data.job.latitude").value(EXACT_LAT));
        }
    }

    @Test
    void aCompanyJobPostedWithoutTheFieldPublishesItsLocation() throws Exception {
        LocalDateTime start = LocalDateTime.now().plusDays(2).withNano(0);
        String body = """
                {
                  "categoryId": "%s",
                  "title": "%s",
                  "description": "Açıklama",
                  "position": "Pozisyon",
                  "address": {
                    "countryId": "%s",
                    "cityId": "%s",
                    "districtId": "%s",
                    "components": {
                      "neighborhood": "Moda",
                      "thoroughfare": "Caferağa Sokak",
                      "buildingNumber": "12",
                      "floor": "3",
                      "unitNumber": "4",
                      "postalCode": "34710"
                    },
                    "position": {
                      "latitude": %s,
                      "longitude": %s,
                      "source": "USER_CONFIRMED_PIN"
                    }
                  },
                  "type": "ONE_TIME",
                  "salaryType": "HOURLY",
                  "salary": 100,
                  "startDateTime": "%s",
                  "endDateTime": "%s",
                  "minAge": 18,
                  "maxAge": 65,
                  "experienceRequired": false,
                  "insuranceRequired": false
                }
                """.formatted(categoryId, title, countryId, cityId, districtId, EXACT_LAT, EXACT_LNG,
                start, start.plusHours(8));

        String created = mockMvc.perform(post("/companies/{companyId}/jobs", companyId)
                        .with(user(principal(ownerId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.addressPublic").value(true))
                .andReturn().getResponse().getContentAsString();
        UUID jobId = UUID.fromString(JsonPath.read(created, "$.data.id"));
        addressIds.add(jdbc.queryForObject("select address_id from jobs where id = ?", UUID.class, jobId));

        assertEquals(Boolean.TRUE,
                jdbc.queryForObject("select address_public from jobs where id = ?", Boolean.class, jobId));
        mockMvc.perform(get("/jobs/{jobId}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.addressPrecision").value("EXACT"))
                .andExpect(jsonPath("$.data.address.position.latitude").value(EXACT_LAT))
                .andExpect(jsonPath("$.data.address.components.floor").doesNotExist());
    }

    private MockHttpServletRequestBuilder as(Viewer viewer, MockHttpServletRequestBuilder request) {
        return switch (viewer) {
            case OWNER -> request.with(user(principal(ownerId)));
            case ASSIGNED_WORKER -> request.with(user(principal(workerId)));
            case OTHER_USER -> request.with(user(principal(otherId)));
            case ANONYMOUS -> request;
        };
    }

    private void insertJob(Kind kind, UUID jobCompanyId, boolean addressPublic, int order) {
        UUID addressId = UUID.randomUUID();
        addressIds.add(addressId);
        jdbc.update("insert into addresses (id, created_at, is_deleted, country_id, city_id, district_id, "
                        + "neighborhood, thoroughfare, building_number, floor, unit_number, postal_code, "
                        + "additional_directions, latitude, longitude, canonical_version, needs_review) "
                        + "values (?, now(), false, ?, ?, ?, 'Moda', 'Caferağa Sokak', '12', '3', '4', '34710', "
                        + "'Zil bozuk', ?, ?, 1, false)",
                addressId, countryId, cityId, districtId, EXACT_LAT, EXACT_LNG);
        UUID jobId = UUID.randomUUID();
        jobs.put(kind, jobId);
        jdbc.update("insert into jobs (id, created_at, is_deleted, creator_id, company_id, job_category_id, "
                        + "employer_type, title, description, position, address_id, address_public, type, salary_type, "
                        + "salary, min_age, max_age, experience_required, insurance_required, status, headcount, "
                        + "start_date_time, end_date_time, pricing_version, currency, worker_payout, "
                        + "commission_rate, commission_amount, total_charged) "
                        + "values (?, now() - (? * interval '1 second'), false, ?, ?, ?, ?, ?, 'Açıklama', "
                        + "'Pozisyon', ?, ?, 'ONE_TIME', 'HOURLY', 100, 18, 65, false, false, 'OPEN', 3, now(), "
                        + "now() + interval '1 day', 'v1', 'TRY', 100.00, 0.1000, 10.00, 110.00)",
                jobId, order, ownerId, jobCompanyId, categoryId, jobCompanyId == null ? "INDIVIDUAL" : "COMPANY",
                title, addressId, addressPublic);
    }

    private void insertUser(UUID id) {
        jdbc.update("insert into users (id, created_at, is_deleted, email, password, is_employer, "
                        + "email_verified, phone_verified, identity_verified) "
                        + "values (?, now(), false, ?, 'test', false, true, false, false)", id, id + "@example.com");
    }

    private UserPrincipal principal(UUID id) {
        return new UserPrincipal(id, id + "@example.com", Set.of(Role.ROLE_USER), null);
    }
}
