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
class JobAddressVisibilityIT extends AbstractIntegrationTest {

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
    private final UUID addressId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final UUID jobId = UUID.randomUUID();
    private final String title = "Gizlilik testi " + UUID.randomUUID();

    @BeforeEach
    void setUp() {
        insertUser(ownerId, "owner@example.com");
        insertUser(workerId, "worker@example.com");
        insertUser(otherId, "other@example.com");
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
        jdbc.update("insert into jobs (id, created_at, is_deleted, creator_id, job_category_id, employer_type, "
                        + "title, description, position, address_id, address_public, type, salary_type, salary, "
                        + "min_age, max_age, experience_required, insurance_required, status, start_date_time, end_date_time) "
                        + "values (?, now(), false, ?, ?, 'INDIVIDUAL', ?, 'Açıklama', 'Pozisyon', ?, false, "
                        + "'ONE_TIME', 'HOURLY', 100, 18, 65, false, false, 'OPEN', now(), now() + interval '1 day')",
                jobId, ownerId, categoryId, title, addressId);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from job_assignments where job_id = ?", jobId);
        jdbc.update("delete from jobs where id = ?", jobId);
        jdbc.update("delete from job_categories where id = ?", categoryId);
        jdbc.update("delete from addresses where id = ?", addressId);
        jdbc.update("delete from districts where id = ?", districtId);
        jdbc.update("delete from cities where id = ?", cityId);
        jdbc.update("delete from countries where id = ?", countryId);
        jdbc.update("delete from authorities where user_id in (?, ?, ?)", ownerId, workerId, otherId);
        jdbc.update("delete from users where id in (?, ?, ?)", ownerId, workerId, otherId);
    }

    @Test
    void anonymousJobDetailDoesNotExposeTheStreetDoorOrExactCoordinates() throws Exception {
        mockMvc.perform(get("/jobs/{jobId}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.addressPrecision").value("APPROXIMATE"))
                .andExpect(jsonPath("$.data.address.components.thoroughfare").doesNotExist())
                .andExpect(jsonPath("$.data.address.components.buildingNumber").doesNotExist())
                .andExpect(jsonPath("$.data.address.position.latitude").value(40.99))
                .andExpect(jsonPath("$.data.address.position.longitude").value(29.03));
    }

    @Test
    void anonymousJobSearchDoesNotExposeExactCoordinates() throws Exception {
        mockMvc.perform(get("/jobs").param("query", title))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].addressPrecision").value("APPROXIMATE"))
                .andExpect(jsonPath("$.data.content[0].latitude").value(40.99))
                .andExpect(jsonPath("$.data.content[0].longitude").value(29.03));
    }

    @Test
    void theOwnerReceivesExactCoordinatesInJobSearch() throws Exception {
        mockMvc.perform(get("/jobs")
                        .param("query", title)
                        .with(user(principal(ownerId, "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].addressPrecision").value("EXACT"))
                .andExpect(jsonPath("$.data.content[0].latitude").value(40.987654))
                .andExpect(jsonPath("$.data.content[0].longitude").value(29.034567));
    }

    @Test
    void anUnrelatedUserReceivesOnlyTheApproximateAddress() throws Exception {
        mockMvc.perform(get("/jobs/{jobId}", jobId).with(user(principal(otherId, "other@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.addressPrecision").value("APPROXIMATE"))
                .andExpect(jsonPath("$.data.address.components.unitNumber").doesNotExist());
    }

    @Test
    void theJobOwnerReceivesTheExactAddress() throws Exception {
        mockMvc.perform(get("/jobs/{jobId}", jobId).with(user(principal(ownerId, "owner@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.addressPrecision").value("EXACT"))
                .andExpect(jsonPath("$.data.address.components.buildingNumber").value("12"))
                .andExpect(jsonPath("$.data.address.position.latitude").value(40.987654));
    }

    @Test
    void anAssignedWorkerReceivesTheExactAddress() throws Exception {
        jdbc.update("insert into job_assignments (id, job_id, worker_id, status, is_deleted, created_at) "
                        + "values (?, ?, ?, 'CONFIRMED', false, now())",
                UUID.randomUUID(), jobId, workerId);

        mockMvc.perform(get("/jobs/{jobId}", jobId).with(user(principal(workerId, "worker@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.addressPrecision").value("EXACT"))
                .andExpect(jsonPath("$.data.address.components.thoroughfare").value("Caferağa Sokak"));
    }

    @Test
    void aWorkerNoLongerReceivesTheExactAddressAfterTheAssignmentCompletes() throws Exception {
        jdbc.update("insert into job_assignments (id, job_id, worker_id, status, is_deleted, created_at) "
                        + "values (?, ?, ?, 'COMPLETED', false, now())",
                UUID.randomUUID(), jobId, workerId);

        mockMvc.perform(get("/jobs/{jobId}", jobId).with(user(principal(workerId, "worker@example.com"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.addressPrecision").value("APPROXIMATE"))
                .andExpect(jsonPath("$.data.address.components.thoroughfare").doesNotExist());
    }

    @Test
    void aPublicCompanyAddressIsExactForAnAnonymousViewer() throws Exception {
        jdbc.update("update jobs set address_public = true, employer_type = 'COMPANY' where id = ?", jobId);

        mockMvc.perform(get("/jobs/{jobId}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.addressPrecision").value("EXACT"))
                .andExpect(jsonPath("$.data.address.components.unitNumber").value("4"));
    }

    private void insertUser(UUID id, String email) {
        jdbc.update("insert into users (id, created_at, is_deleted, email, password, is_employer, "
                        + "email_verified, phone_verified, identity_verified) "
                        + "values (?, now(), false, ?, 'test', false, true, false, false)", id, email);
    }

    private UserPrincipal principal(UUID id, String email) {
        return new UserPrincipal(id, email, Set.of(Role.ROLE_USER), null);
    }
}
