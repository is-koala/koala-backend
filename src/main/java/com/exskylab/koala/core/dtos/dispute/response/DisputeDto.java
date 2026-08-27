package com.exskylab.koala.core.dtos.dispute.response;

import com.exskylab.koala.core.dtos.user.response.SafeUserDto;
import com.exskylab.koala.entities.DisputeResolution;
import com.exskylab.koala.entities.DisputeStatus;

import java.time.Instant;
import java.util.UUID;

public record DisputeDto(UUID id, UUID jobAssignmentId, SafeUserDto openedBy, DisputeStatus status,
                         String reason, DisputeResolution resolution, String resolutionNote,
                         Instant resolvedAt, Instant createdAt) {
}
