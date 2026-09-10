package com.exskylab.koala.core.helpers;

import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.JobStatus;
import com.exskylab.koala.core.properties.JobSearchProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobAvailabilityTest {

    private static final Instant NOW = Instant.parse("2026-09-09T10:00:00Z");
    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private final JobAvailability availability = new JobAvailability(
            Clock.fixed(NOW, ISTANBUL), new JobSearchProperties());

    @Test
    void anOpenJobStopsAcceptingApplicationsAtItsExactEndTime() {
        Job job = openJobEndingAt(LocalDateTime.of(2026, 9, 9, 13, 0));

        assertFalse(availability.acceptsApplications(job));
    }

    @Test
    void anOpenJobAcceptsApplicationsBeforeItsEndTime() {
        Job job = openJobEndingAt(LocalDateTime.of(2026, 9, 9, 13, 0, 0, 1));

        assertTrue(availability.acceptsApplications(job));
    }

    @Test
    void statusStillClosesAnUnexpiredJob() {
        Job job = openJobEndingAt(LocalDateTime.of(2026, 9, 10, 13, 0));
        job.setStatus(JobStatus.CLOSED);

        assertFalse(availability.acceptsApplications(job));
    }

    @Test
    void aMissingEndTimeFailsClosedForApplications() {
        Job job = openJobEndingAt(null);

        assertFalse(availability.acceptsApplications(job));
    }

    private Job openJobEndingAt(LocalDateTime endDateTime) {
        Job job = new Job();
        job.setStatus(JobStatus.OPEN);
        job.setEndDateTime(endDateTime);
        return job;
    }
}
