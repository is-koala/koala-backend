package com.exskylab.koala.core.helpers;

import com.exskylab.koala.business.abstracts.SettingsService;
import com.exskylab.koala.core.constants.SettingKeys;
import com.exskylab.koala.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommissionRateRuntimeIT extends AbstractIntegrationTest {

    private static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String CONVERSATION_ID = "commission-rate-runtime-it";

    private final UUID jobId = UUID.randomUUID();
    private final UUID assignmentId = UUID.randomUUID();
    private final UUID paymentId = UUID.randomUUID();

    @Autowired
    private CommissionCalculator commissionCalculator;

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearTheRateRow() {
        jdbcTemplate.update("delete from app_settings where setting_key = ?", SettingKeys.COMMISSION_RATE);
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from payments where conversation_id = ?", CONVERSATION_ID);
        jdbcTemplate.update("delete from job_assignments where id = ?", assignmentId);
        jdbcTemplate.update("delete from jobs where id = ?", jobId);
        jdbcTemplate.update("delete from app_settings where setting_key = ?", SettingKeys.COMMISSION_RATE);
    }

    @Test
    void theEnvironmentDefaultAppliesWhileTheTableHasNoRow() {
        CommissionCalculator.Breakdown breakdown = commissionCalculator.calculate(new BigDecimal("1000.00"));

        assertEquals(0, new BigDecimal("0.075").compareTo(breakdown.rate()));
        assertEquals(new BigDecimal("75.00"), breakdown.commission());
    }

    @Test
    void aStoredRateAppliesWithoutARestart() {
        settingsService.update(SettingKeys.COMMISSION_RATE, "0.20");

        CommissionCalculator.Breakdown breakdown = commissionCalculator.calculate(new BigDecimal("1000.00"));

        assertEquals(0, new BigDecimal("0.20").compareTo(breakdown.rate()));
        assertEquals(new BigDecimal("200.00"), breakdown.commission());
        assertEquals(new BigDecimal("1200.00"), breakdown.totalCharged());
    }

    @Test
    void rowsPricedBeforeTheChangeKeepTheRateTheyWereChargedWith() {
        priceARowAtTheOldRate();

        settingsService.update(SettingKeys.COMMISSION_RATE, "0.20");

        assertEquals(0, new BigDecimal("0.0750").compareTo(storedRate("jobs", jobId)));
        assertEquals(0, new BigDecimal("0.0750").compareTo(storedRate("job_assignments", assignmentId)));
        assertEquals(0, new BigDecimal("0.0750").compareTo(storedRate("payments", paymentId)));

        assertEquals(0, new BigDecimal("0.20")
                .compareTo(commissionCalculator.calculate(new BigDecimal("1000.00")).rate()));
    }

    private BigDecimal storedRate(String table, UUID id) {
        return jdbcTemplate.queryForObject(
                "select commission_rate from " + table + " where id = ?", BigDecimal.class, id);
    }

    private void priceARowAtTheOldRate() {
        jdbcTemplate.update("""
                insert into jobs (id, created_at, is_deleted, pricing_version, currency,
                                  worker_payout, commission_rate, commission_amount, total_charged)
                values (?, now(), false, '2026-09-v1', 'TRY', 1000.00, 0.0750, 75.00, 1075.00)
                """, jobId);

        jdbcTemplate.update("""
                insert into job_assignments (id, created_at, is_deleted, job_id, worker_id, pricing_version,
                                             currency, worker_payout, commission_rate, commission_amount, total_charged)
                values (?, now(), false, ?, ?, '2026-09-v1', 'TRY', 1000.00, 0.0750, 75.00, 1075.00)
                """, assignmentId, jobId, SYSTEM_USER_ID);

        jdbcTemplate.update("""
                insert into payments (id, created_at, is_deleted, job_assignment_id, conversation_id, status,
                                      currency, pricing_version, worker_payout, commission_rate,
                                      commission_amount, total_charged)
                values (?, now(), false, ?, ?, 'HELD_IN_ESCROW', 'TRY', '2026-09-v1', 1000.00, 0.0750, 75.00, 1075.00)
                """, paymentId, assignmentId, CONVERSATION_ID);
    }
}
