package com.exskylab.koala.core.dtos.legal.response;

import com.exskylab.koala.entities.AccountDeletionStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AccountDeletionDto(
        UUID id,
        UUID subjectId,
        AccountDeletionStatus status,
        Instant requestedAt,
        Instant processedAt,
        String blockedReason,
        String rejectionReason,
        UUID rejectedByStaffId,
        List<DeletionBlockerDto> blockers) {
}
