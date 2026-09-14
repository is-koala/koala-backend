package com.exskylab.koala.webAPI;

import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class JobSearchDateDistanceIT extends AbstractIntegrationTest {

    private static final BigDecimal CENTER_LAT = new BigDecimal("40.990000");
    private static final BigDecimal CENTER_LNG = new BigDecimal("29.030000");

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
    private final String query = "Tarih ve mesafe " + UUID.randomUUID();

    private final UUID atLowerBound = UUID.randomUUID();
    private final UUID inside = UUID.randomUUID();
    private final UUID atUpperBound = UUID.randomUUID();
    private final UUID justBefore = UUID.randomUUID();
    private final UUID justAfter = UUID.randomUUID();

    private final UUID near = UUID.randomUUID();
    private final UUID mid = UUID.randomUUID();
    private final UUID far = UUID.randomUUID();
    private final UUID individualAtExactSpot = UUID.randomUUID();
    private final UUID publicCompanyAtExactSpot = UUID.randomUUID();
    private final UUID withoutCoordinates = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbc.update("insert into users (id, created_at, is_deleted, email, password, is_employer, "
                        + "email_verified, phone_verified, identity_verified) "
                        + "values (?, now(), false, ?, 'test', true, true, false, false)",
                ownerId, ownerId + "@example.com");
        jdbc.update("insert into companies (id, created_at, is_deleted, name, tax_number, email, approval_status) "
                        + "values (?, now(), false, 'Mesafe Şirketi', ?, ?, 'APPROVED')",
                companyId, String.valueOf(Math.abs(companyId.hashCode())), companyId + "@example.com");
        jdbc.update("insert into countries (id, created_at, is_deleted, name, code) values (?, now(), false, ?, ?)",
                countryId, "Türkiye", "TR");
        jdbc.update("insert into cities (id, created_at, is_deleted, name, country_id) values (?, now(), false, ?, ?)",
                cityId, "İstanbul", countryId);
        jdbc.update("insert into districts (id, created_at, is_deleted, name, city_id) values (?, now(), false, ?, ?)",
                districtId, "Kadıköy", cityId);
        jdbc.update("insert into job_categories (id, name, code, requires_insurance) values (?, ?, ?, false)",
                categoryId, "Tarih mesafe", "DATE_DISTANCE_" + categoryId);

        LocalDateTime lowerBound = LocalDateTime.of(2027, 3, 10, 0, 0, 0);
        LocalDateTime upperBound = LocalDateTime.of(2027, 3, 11, 23, 59, 59);
        insertJob(atLowerBound, null, false, "40.990000", "29.030000", lowerBound, 0);
        insertJob(inside, null, false, "40.990000", "29.030000", lowerBound.plusHours(12), 1);
        insertJob(atUpperBound, null, false, "40.990000", "29.030000", upperBound, 2);
        insertJob(justBefore, null, false, "40.990000", "29.030000", lowerBound.minusSeconds(1), 3);
        insertJob(justAfter, null, false, "40.990000", "29.030000", upperBound.plusSeconds(1), 4);

        LocalDateTime distanceStart = LocalDateTime.of(2027, 5, 1, 9, 0);
        insertJob(far, null, false, "41.050000", "29.100000", distanceStart, 10);
        insertJob(near, null, false, "40.990000", "29.040000", distanceStart, 11);
        insertJob(mid, null, false, "41.010000", "29.030000", distanceStart, 12);
        insertJob(individualAtExactSpot, null, false, "40.994999", "29.030000", distanceStart, 13);
        insertJob(publicCompanyAtExactSpot, companyId, true, "40.994999", "29.030000", distanceStart, 14);
        insertJob(withoutCoordinates, null, false, null, null, distanceStart, 9);
    }

    @AfterEach
    void tearDown() {
        jdbc.update("delete from jobs where title like ?", query + "%");
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
    void theStartRangeIsInclusiveAndReadInTheConfiguredWallClock() throws Exception {
        mockMvc.perform(get("/jobs")
                        .param("query", query)
                        .param("startFrom", "2027-03-09T21:00:00Z")
                        .param("startTo", "2027-03-11T20:59:59Z")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[*].id", containsInAnyOrder(
                        atLowerBound.toString(), inside.toString(), atUpperBound.toString())))
                .andExpect(jsonPath("$.data.content[*].id", not(hasItem(justBefore.toString()))))
                .andExpect(jsonPath("$.data.content[*].id", not(hasItem(justAfter.toString()))));
    }

    @Test
    void anOffsetInTheRangeIsHonouredNotDropped() throws Exception {
        mockMvc.perform(get("/jobs")
                        .param("query", query)
                        .param("startFrom", "2027-03-10T00:00:00+03:00")
                        .param("startTo", "2027-03-10T00:00:00+03:00")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].id", contains(atLowerBound.toString())));
    }

    @Test
    void anOpenEndedRangeWorksFromEitherSide() throws Exception {
        mockMvc.perform(get("/jobs")
                        .param("query", query)
                        .param("startFrom", "2027-03-11T21:00:00Z")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(7))
                .andExpect(jsonPath("$.data.content[*].id", hasItem(justAfter.toString())))
                .andExpect(jsonPath("$.data.content[*].id", not(hasItem(atUpperBound.toString()))));

        mockMvc.perform(get("/jobs")
                        .param("query", query)
                        .param("startTo", "2027-03-09T20:59:59Z")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].id", contains(justBefore.toString())));
    }

    @Test
    void aReversedRangeIsRejectedWithAFieldError() throws Exception {
        mockMvc.perform(get("/jobs")
                        .param("startFrom", "2027-03-11T21:00:00Z")
                        .param("startTo", "2027-03-09T21:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCategory").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[*].field", hasItem("startRangeOrdered")));
    }

    @Test
    void aRangeBoundWithoutAnOffsetIsRejected() throws Exception {
        mockMvc.perform(get("/jobs").param("startFrom", "2027-03-10T00:00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field", hasItem("startFrom")));
    }

    @Test
    void distanceSortOrdersFromNearestToFarthestAndFillsDistanceKm() throws Exception {
        mockMvc.perform(get("/jobs")
                        .param("query", query)
                        .param("startFrom", "2027-05-01T00:00:00Z")
                        .param("lat", CENTER_LAT.toPlainString())
                        .param("lng", CENTER_LNG.toPlainString())
                        .param("sort", "distance,asc")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(6)))
                .andExpect(jsonPath("$.data.content[0].id").value(individualAtExactSpot.toString()))
                .andExpect(jsonPath("$.data.content[0].distanceKm").value(0.0))
                .andExpect(jsonPath("$.data.content[1].id").value(publicCompanyAtExactSpot.toString()))
                .andExpect(jsonPath("$.data.content[1].distanceKm").value(0.6))
                .andExpect(jsonPath("$.data.content[2].id").value(near.toString()))
                .andExpect(jsonPath("$.data.content[2].distanceKm").value(0.8))
                .andExpect(jsonPath("$.data.content[3].id").value(mid.toString()))
                .andExpect(jsonPath("$.data.content[3].distanceKm").value(2.2))
                .andExpect(jsonPath("$.data.content[4].id").value(far.toString()))
                .andExpect(jsonPath("$.data.content[4].distanceKm").value(8.9))
                .andExpect(jsonPath("$.data.content[5].id").value(withoutCoordinates.toString()))
                .andExpect(jsonPath("$.data.content[5].distanceKm").value(nullValue()));
    }

    @Test
    void aJobWithoutCoordinatesComesLastInBothDirectionsOfTheDistanceSort() throws Exception {
        mockMvc.perform(get("/jobs")
                        .param("query", query)
                        .param("startFrom", "2027-05-01T00:00:00Z")
                        .param("lat", CENTER_LAT.toPlainString())
                        .param("lng", CENTER_LNG.toPlainString())
                        .param("sort", "distance,desc")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(6)))
                .andExpect(jsonPath("$.data.content[0].id").value(far.toString()))
                .andExpect(jsonPath("$.data.content[4].id").value(individualAtExactSpot.toString()))
                .andExpect(jsonPath("$.data.content[5].id").value(withoutCoordinates.toString()))
                .andExpect(jsonPath("$.data.content[5].distanceKm").value(nullValue()));

        mockMvc.perform(get("/jobs")
                        .param("query", query)
                        .param("startFrom", "2027-05-01T00:00:00Z")
                        .param("lat", CENTER_LAT.toPlainString())
                        .param("lng", CENTER_LNG.toPlainString())
                        .param("sort", "distance,asc")
                        .param("size", "2")
                        .param("page", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(6))
                .andExpect(jsonPath("$.data.content[1].id").value(withoutCoordinates.toString()));
    }

    @Test
    void distanceKmIsFilledWithoutDistanceSortAndOtherSortsStillWork() throws Exception {
        mockMvc.perform(get("/jobs")
                        .param("query", query)
                        .param("startFrom", "2027-05-01T00:00:00Z")
                        .param("lat", CENTER_LAT.toPlainString())
                        .param("lng", CENTER_LNG.toPlainString())
                        .param("sort", "createdAt,desc")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(6)))
                .andExpect(jsonPath("$.data.content[0].id").value(withoutCoordinates.toString()))
                .andExpect(jsonPath("$.data.content[0].distanceKm").value(nullValue()))
                .andExpect(jsonPath("$.data.content[1].id").value(far.toString()))
                .andExpect(jsonPath("$.data.content[1].distanceKm").value(8.9))
                .andExpect(jsonPath("$.data.content[5].id").value(publicCompanyAtExactSpot.toString()))
                .andExpect(jsonPath("$.data.content[5].distanceKm").value(0.6));
    }

    @Test
    void distanceKmStaysNullWhenNoLocationIsGiven() throws Exception {
        mockMvc.perform(get("/jobs")
                        .param("query", query)
                        .param("startFrom", "2027-05-01T00:00:00Z")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(6)))
                .andExpect(jsonPath("$.data.content[*].distanceKm", everyItem(nullValue())));
    }

    @Test
    void distanceSortWithoutALocationIsRejectedLikeAnyUnknownSortField() throws Exception {
        mockMvc.perform(get("/jobs").param("sort", "distance,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("PAGE_SORT_FIELD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.errorCategory").value("VALIDATION_FAILED"));
    }

    @Test
    void aLoneCoordinateOrTooManyDecimalsIsRejected() throws Exception {
        mockMvc.perform(get("/jobs").param("lat", CENTER_LAT.toPlainString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field", hasItem("locationComplete")));

        mockMvc.perform(get("/jobs")
                        .param("lat", "40.9912345")
                        .param("lng", CENTER_LNG.toPlainString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field", hasItem("lat")));

        mockMvc.perform(get("/jobs")
                        .param("lat", CENTER_LAT.toPlainString())
                        .param("lng", "180.000000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details[*].field", hasItem("lng")));
    }

    private void insertJob(UUID id, UUID jobCompanyId, boolean addressPublic, String latitude, String longitude,
                           LocalDateTime startDateTime, int order) {
        UUID addressId = UUID.randomUUID();
        addressIds.add(addressId);
        jdbc.update("insert into addresses (id, created_at, is_deleted, country_id, city_id, district_id, "
                        + "latitude, longitude, canonical_version, needs_review) "
                        + "values (?, now(), false, ?, ?, ?, ?, ?, 1, false)",
                addressId, countryId, cityId, districtId,
                latitude == null ? null : new BigDecimal(latitude), longitude == null ? null : new BigDecimal(longitude));
        jdbc.update("insert into jobs (id, created_at, is_deleted, creator_id, company_id, job_category_id, "
                        + "employer_type, title, description, position, address_id, address_public, type, "
                        + "salary_type, salary, min_age, max_age, experience_required, insurance_required, "
                        + "status, start_date_time, end_date_time) "
                        + "values (?, now() - (? * interval '1 second'), false, ?, ?, ?, ?, ?, 'Açıklama', "
                        + "'Pozisyon', ?, ?, 'ONE_TIME', 'HOURLY', 100, 18, 65, false, false, 'OPEN', ?, "
                        + "cast(? as timestamp) + interval '8 hours')",
                id, order, ownerId, jobCompanyId, categoryId, jobCompanyId == null ? "INDIVIDUAL" : "COMPANY",
                query + " " + order, addressId, addressPublic, startDateTime, startDateTime);
    }
}
