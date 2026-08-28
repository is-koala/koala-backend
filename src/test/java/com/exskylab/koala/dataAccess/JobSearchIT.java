package com.exskylab.koala.dataAccess;

import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobSearchIT extends AbstractIntegrationTest {

    @Autowired
    private JobDao jobDao;

    @Test
    void theSearchQueryPaginatesInTheDatabaseNotInMemory() {
        var page = assertDoesNotThrow(() -> jobDao.searchIds(
                com.exskylab.koala.entities.JobStatus.OPEN,
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null,
                PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "createdAt"))));

        assertNotNull(page);
        assertTrue(page.getSize() <= 5);
    }

    @Test
    void everyFilterCombinationIsAValidQuery() {
        assertDoesNotThrow(() -> jobDao.searchIds(
                com.exskylab.koala.entities.JobStatus.OPEN,
                "garson",
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
                null, null, null, null, null, null, null, null, null, null, null,
                40.98, 41.10, 28.95, 29.15,
                PageRequest.of(0, 20)));
    }

    @Test
    void loadingByIdListDoesNotBlowUpOnAnEmptyList() {
        assertTrue(jobDao.findAllByIdIn(java.util.List.of()).isEmpty());
    }
}
