package com.exskylab.koala.dataAccess;

import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivePaymentAttemptConstraintIT extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void databaseAllowsOnlyOneActiveAttemptPerAssignment() {
        String definition = jdbcTemplate.queryForObject(
                "select indexdef from pg_indexes where indexname = 'uk_payments_one_active_attempt'",
                String.class);

        assertTrue(definition.contains("UNIQUE INDEX"));
        assertTrue(definition.contains("job_assignment_id"));
        assertTrue(definition.contains("INITIATED"));
        assertTrue(definition.contains("IN_REVIEW"));
    }
}
