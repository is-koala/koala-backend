package com.exskylab.koala.core.helpers;

import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.JobStatus;
import com.exskylab.koala.core.properties.JobSearchProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
public class JobAvailability {

    private final Clock clock;
    private final JobSearchProperties properties;

    public JobAvailability(Clock clock, JobSearchProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    public boolean acceptsApplications(Job job) {
        return job != null
                && job.getStatus() == JobStatus.OPEN
                && job.getEndDateTime() != null
                && job.getEndDateTime().isAfter(currentTime());
    }

    public LocalDateTime currentTime() {
        return LocalDateTime.now(clock.withZone(properties.zone()));
    }
}
