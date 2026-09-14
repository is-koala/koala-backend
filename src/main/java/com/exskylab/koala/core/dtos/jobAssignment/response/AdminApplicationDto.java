package com.exskylab.koala.core.dtos.jobAssignment.response;

import com.exskylab.koala.entities.ApplicationStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminApplicationDto(UUID id, ApplicationStatus status, Instant applicationDateTime,
                                  Instant answeredDateTime, String rejectionReason) {
}
