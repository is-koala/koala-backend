package com.exskylab.koala.core.helpers;

import com.exskylab.koala.core.properties.TimesheetProperties;
import com.exskylab.koala.entities.TimesheetPlausibilityFlag;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

@Component
public class TimesheetEventPolicy {

    private final TimesheetProperties properties;

    public TimesheetEventPolicy(TimesheetProperties properties) {
        this.properties = properties;
    }

    public Outcome reconcile(Instant capturedAt, Instant receivedAt,
                             LocalDateTime jobStart, LocalDateTime jobEnd,
                             Instant earliestEffectiveAt) {
        EnumSet<TimesheetPlausibilityFlag> flags = EnumSet.noneOf(TimesheetPlausibilityFlag.class);
        Instant effectiveAt = capturedAt == null ? receivedAt : capturedAt;

        if (capturedAt != null && (capturedAt.isBefore(receivedAt.minus(properties.getMaxOfflineAge()))
                || capturedAt.isAfter(receivedAt.plus(properties.getMaxFutureSkew())))) {
            effectiveAt = receivedAt;
            flags.add(TimesheetPlausibilityFlag.DEVICE_TIME_IMPLAUSIBLE);
        }

        if (jobStart != null) {
            Instant start = jobStart.atZone(properties.getZoneId()).toInstant();
            if (effectiveAt.isBefore(start)) {
                effectiveAt = start;
                flags.add(TimesheetPlausibilityFlag.OUTSIDE_JOB_WINDOW);
            }
        }

        if (jobEnd != null) {
            Instant end = jobEnd.atZone(properties.getZoneId()).toInstant();
            if (effectiveAt.isAfter(end)) {
                effectiveAt = end;
                flags.add(TimesheetPlausibilityFlag.OUTSIDE_JOB_WINDOW);
            }
        }

        if (earliestEffectiveAt != null && effectiveAt.isBefore(earliestEffectiveAt)) {
            effectiveAt = earliestEffectiveAt;
            flags.add(TimesheetPlausibilityFlag.NON_MONOTONIC);
        }

        return new Outcome(effectiveAt, Collections.unmodifiableSet(flags));
    }

    public record Outcome(Instant effectiveAt, Set<TimesheetPlausibilityFlag> flags) {
    }
}
