package com.exskylab.koala.dataAccess;

import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobSearchIT extends AbstractIntegrationTest {

    private static final LocalDateTime NO_LOWER_BOUND = LocalDateTime.of(1, 1, 1, 0, 0);
    private static final LocalDateTime NO_UPPER_BOUND = LocalDateTime.of(9999, 12, 31, 23, 59, 59);

    @Autowired
    private JobDao jobDao;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void theExactEndTimeIsNotSearchable() {
        LocalDateTime boundary = LocalDateTime.of(2026, 9, 9, 13, 0);
        UUID endingAtBoundary = UUID.randomUUID();
        UUID endingAfterBoundary = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID countryId = UUID.randomUUID();
        UUID cityId = UUID.randomUUID();
        UUID districtId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();

        try {
            jdbc.update("insert into users (id, created_at, is_deleted, email, password) "
                    + "values (?, now(), false, ?, 'test')", ownerId, ownerId + "@example.com");
            jdbc.update("insert into countries (id, created_at, is_deleted, name, code) "
                    + "values (?, now(), false, 'Türkiye', ?)", countryId, "T" + countryId.toString().substring(0, 1));
            jdbc.update("insert into cities (id, created_at, is_deleted, name, country_id) "
                    + "values (?, now(), false, 'İstanbul', ?)", cityId, countryId);
            jdbc.update("insert into districts (id, created_at, is_deleted, name, city_id) "
                    + "values (?, now(), false, 'Kadıköy', ?)", districtId, cityId);
            jdbc.update("insert into addresses (id, created_at, is_deleted, country_id, city_id, district_id, "
                            + "latitude, longitude, canonical_version, needs_review) "
                            + "values (?, now(), false, ?, ?, ?, 41.0, 29.0, 1, false)",
                    addressId, countryId, cityId, districtId);
            jdbc.update("insert into job_categories (id, name, code, requires_insurance) values (?, 'Test', ?, false)",
                    categoryId, "TEST_" + categoryId);
            insertOpenJob(endingAtBoundary, boundary, ownerId, addressId, categoryId);
            insertOpenJob(endingAfterBoundary, boundary.plusSeconds(1), ownerId, addressId, categoryId);

            var results = searchAt(boundary).map(JobDao.JobSearchHit::getId).getContent();

            assertFalse(results.contains(endingAtBoundary));
            assertTrue(results.contains(endingAfterBoundary));
        } finally {
            jdbc.update("delete from jobs where id in (?, ?)", endingAtBoundary, endingAfterBoundary);
            jdbc.update("delete from job_categories where id = ?", categoryId);
            jdbc.update("delete from addresses where id = ?", addressId);
            jdbc.update("delete from districts where id = ?", districtId);
            jdbc.update("delete from cities where id = ?", cityId);
            jdbc.update("delete from countries where id = ?", countryId);
            jdbc.update("delete from users where id = ?", ownerId);
        }
    }

    @Test
    void theSearchQueryPaginatesInTheDatabaseNotInMemory() {
        var page = assertDoesNotThrow(() -> jobDao.searchIds(
                com.exskylab.koala.entities.JobStatus.OPEN,
                java.time.LocalDateTime.now(),
                false, NO_LOWER_BOUND, NO_UPPER_BOUND, false, 0d, 0d,
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt"))));

        assertNotNull(page);
        assertTrue(page.getSize() <= 5);
    }

    @Test
    void everyFilterCombinationIsAValidQuery() {
        assertDoesNotThrow(() -> jobDao.searchIds(
                com.exskylab.koala.entities.JobStatus.OPEN,
                java.time.LocalDateTime.now(),
                true, LocalDateTime.of(2026, 9, 1, 0, 0), LocalDateTime.of(2026, 9, 30, 23, 59),
                true, 41.015137, 28.979530,
                "garson",
                null,
                java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(),
                java.util.UUID.randomUUID(),
                com.exskylab.koala.entities.JobType.ONE_TIME,
                com.exskylab.koala.entities.SalaryType.HOURLY,
                com.exskylab.koala.entities.JobEmployerType.INDIVIDUAL,
                new java.math.BigDecimal("100.00"),
                new java.math.BigDecimal("5000.00"),
                Boolean.FALSE,
                Boolean.TRUE,
                40.9, 41.2, 28.9, 29.3,
                PageRequest.of(0, 20)));
    }

    @Test
    void aBoundingBoxNarrowsTheSearchWithoutBreakingTheQuery() {
        assertDoesNotThrow(() -> jobDao.searchIds(
                com.exskylab.koala.entities.JobStatus.OPEN,
                java.time.LocalDateTime.now(),
                false, NO_LOWER_BOUND, NO_UPPER_BOUND, false, 0d, 0d,
                null, null, null, null, null, null, null, null, null, null, null,
                null, 40.98, 41.10, 28.95, 29.15,
                PageRequest.of(0, 20)));
    }

    @Test
    void loadingByIdListDoesNotBlowUpOnAnEmptyList() {
        assertTrue(jobDao.findAllByIdIn(java.util.List.of()).isEmpty());
    }

    private void insertOpenJob(UUID id, LocalDateTime endDateTime, UUID ownerId, UUID addressId, UUID categoryId) {
        jdbc.update("insert into jobs (id, created_at, is_deleted, creator_id, job_category_id, employer_type, "
                        + "address_id, status, end_date_time, address_public) "
                        + "values (?, now(), false, ?, ?, 'INDIVIDUAL', ?, 'OPEN', ?, false)",
                id, ownerId, categoryId, addressId, endDateTime);
    }

    private org.springframework.data.domain.Page<JobDao.JobSearchHit> searchAt(LocalDateTime activeAfter) {
        return jobDao.searchIds(
                com.exskylab.koala.entities.JobStatus.OPEN,
                activeAfter,
                false, NO_LOWER_BOUND, NO_UPPER_BOUND, false, 0d, 0d,
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null,
                PageRequest.of(0, 20));
    }
}
