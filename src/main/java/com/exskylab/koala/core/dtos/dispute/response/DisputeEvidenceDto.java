package com.exskylab.koala.core.dtos.dispute.response;

import com.exskylab.koala.core.dtos.user.response.SafeUserDto;

import java.time.Instant;
import java.util.UUID;

public record DisputeEvidenceDto(UUID id, UUID disputeId, SafeUserDto uploadedBy, String fileName,
                                 String contentType, long fileSize, String checksumSha256,
                                 Instant createdAt) {
}
