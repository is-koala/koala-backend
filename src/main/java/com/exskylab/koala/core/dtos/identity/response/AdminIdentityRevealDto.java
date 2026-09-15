package com.exskylab.koala.core.dtos.identity.response;

import java.time.Instant;
import java.util.UUID;

public record AdminIdentityRevealDto(UUID id, UUID viewerId, String viewerFullName, UUID jobAssignmentId, UUID jobId,
                                     String jobTitle, Instant revealedAt, String ipAddress) {
}
