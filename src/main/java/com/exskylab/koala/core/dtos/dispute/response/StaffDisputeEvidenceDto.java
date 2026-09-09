package com.exskylab.koala.core.dtos.dispute.response;

import com.exskylab.koala.core.dtos.user.response.SafeUserDto;
import com.exskylab.koala.entities.EvidenceUploaderSide;

import java.time.Instant;
import java.util.UUID;

public record StaffDisputeEvidenceDto(UUID id, UUID disputeId, SafeUserDto uploadedBy,
                                      EvidenceUploaderSide uploaderSide, String fileName,
                                      String contentType, long fileSize, String checksumSha256,
                                      Instant createdAt) {
}
