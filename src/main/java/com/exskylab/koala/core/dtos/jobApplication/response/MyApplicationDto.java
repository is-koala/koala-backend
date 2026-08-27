package com.exskylab.koala.core.dtos.jobApplication.response;

import com.exskylab.koala.core.dtos.job.response.JobSummaryDto;
import com.exskylab.koala.entities.ApplicationStatus;

import java.time.Instant;
import java.util.UUID;

public record MyApplicationDto(UUID id, JobSummaryDto job, ApplicationStatus status,
                               String notes, String rejectionReason,
                               Instant applicationDateTime, Instant answeredDateTime,
                               UUID assignmentId) {
}
