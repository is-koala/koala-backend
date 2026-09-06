package com.exskylab.koala.core.helpers;

import com.exskylab.koala.core.properties.TimesheetProperties;
import com.exskylab.koala.entities.TimesheetPlausibilityFlag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimesheetEventPolicyTest {

    private static final Instant RECEIVED_AT = Instant.parse("2026-09-06T12:00:00Z");
    private static final LocalDateTime JOB_START = LocalDateTime.parse("2026-09-06T09:00:00");
    private static final LocalDateTime JOB_END = LocalDateTime.parse("2026-09-06T18:00:00");

    private final TimesheetEventPolicy policy = new TimesheetEventPolicy(properties());

    @Test
    void aPlausibleCapturedTimeBecomesTheEffectiveTime() {
        Instant capturedAt = Instant.parse("2026-09-06T10:00:00Z");

        TimesheetEventPolicy.Outcome outcome = policy.reconcile(
                capturedAt, RECEIVED_AT, JOB_START, JOB_END, null);

        assertEquals(capturedAt, outcome.effectiveAt());
        assertTrue(outcome.flags().isEmpty());
    }

    @Test
    void anOldDeviceTimeIsPreservedAsEvidenceButUsesReceiptTime() {
        TimesheetEventPolicy.Outcome outcome = policy.reconcile(
                Instant.parse("2026-09-01T10:00:00Z"), RECEIVED_AT, JOB_START, JOB_END, null);

        assertEquals(RECEIVED_AT, outcome.effectiveAt());
        assertEquals(java.util.Set.of(TimesheetPlausibilityFlag.DEVICE_TIME_IMPLAUSIBLE), outcome.flags());
    }

    @Test
    void aTimeOutsideTheJobWindowIsClampedAndFlagged() {
        TimesheetEventPolicy.Outcome outcome = policy.reconcile(
                Instant.parse("2026-09-06T05:00:00Z"), RECEIVED_AT, JOB_START, JOB_END, null);

        assertEquals(Instant.parse("2026-09-06T06:00:00Z"), outcome.effectiveAt());
        assertEquals(java.util.Set.of(TimesheetPlausibilityFlag.OUTSIDE_JOB_WINDOW), outcome.flags());
    }

    @Test
    void checkoutCannotCreateANegativeDuration() {
        Instant checkInAt = Instant.parse("2026-09-06T10:00:00Z");

        TimesheetEventPolicy.Outcome outcome = policy.reconcile(
                Instant.parse("2026-09-06T09:00:00Z"), RECEIVED_AT, JOB_START, JOB_END, checkInAt);

        assertEquals(checkInAt, outcome.effectiveAt());
        assertEquals(java.util.Set.of(TimesheetPlausibilityFlag.NON_MONOTONIC), outcome.flags());
    }

    private static TimesheetProperties properties() {
        TimesheetProperties properties = new TimesheetProperties();
        properties.setMaxOfflineAge(Duration.ofHours(72));
        properties.setMaxFutureSkew(Duration.ofMinutes(5));
        properties.setZoneId(ZoneId.of("Europe/Istanbul"));
        return properties;
    }
}
