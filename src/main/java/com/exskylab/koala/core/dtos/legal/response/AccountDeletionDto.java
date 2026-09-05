package com.exskylab.koala.core.dtos.legal.response;

import com.exskylab.koala.entities.AccountDeletionStatus;

import java.time.Instant;
import java.util.UUID;

public record AccountDeletionDto(
        UUID id,
        UUID subjectId,
        AccountDeletionStatus status,
        Instant requestedAt,
        Instant processedAt,
        String blockedReason) {
}
