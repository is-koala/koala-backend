package com.exskylab.koala.dataAccess;

import com.exskylab.koala.support.AbstractIntegrationTest;
import com.exskylab.koala.entities.LoginAttempt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimestampTimeZoneIT extends AbstractIntegrationTest {

    private final TimeZone originalZone = TimeZone.getDefault();

    @Autowired
    private LoginAttempDaoAccessor accessor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void restoreZone() {
        TimeZone.setDefault(originalZone);
    }

    @Test
    void anInstantSurvivesAServerTimeZoneChange() {
        Instant written = Instant.parse("2026-08-27T09:30:00Z");

        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Istanbul"));
        var saved = accessor.save(written);

        TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
        Instant readBack = accessor.findTimestamp(saved);

        assertEquals(written, readBack,
                "Sunucu saat dilimi degisince kayitli an kaymamaliydi.");
    }

    @Test
    void systemTimestampColumnsAreTimeZoneAware() {
        List<String> naive = jdbcTemplate.queryForList("""
                select c.table_name || '.' || c.column_name
                from information_schema.columns c
                join information_schema.tables t
                  on t.table_schema = c.table_schema and t.table_name = c.table_name
                where c.table_schema = 'public'
                  and t.table_type = 'BASE TABLE'
                  and c.data_type = 'timestamp without time zone'
                  and c.table_name not in ('databasechangelog', 'databasechangeloglock')
                """, String.class);

        assertEquals(List.of("jobs.start_date_time", "jobs.end_date_time").size(), naive.size(),
                "Saat dilimi tasimayan kolonlar: " + naive);
        assertTrue(naive.containsAll(List.of("jobs.start_date_time", "jobs.end_date_time")),
                "Yalnizca is takvimi alanlari duvar saati kalmali, bulunanlar: " + naive);
    }
}
