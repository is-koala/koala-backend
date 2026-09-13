package com.exskylab.koala.core.dtos.jobApplication.response;

import com.exskylab.koala.core.dtos.user.response.SafeUserDto;
import com.exskylab.koala.entities.ApplicationStatus;
import com.exskylab.koala.entities.JobStatus;

import java.time.Instant;
import java.util.UUID;

public record ReceivedApplicationDto(UUID id, ApplicationStatus status, Instant applicationDateTime,
                                     Instant answeredDateTime, String notes, SafeUserDto applicant,
                                     UUID jobId, String postingTitle, JobStatus jobStatus,
                                     UUID companyId, String companyName, UUID assignmentId) {
}
