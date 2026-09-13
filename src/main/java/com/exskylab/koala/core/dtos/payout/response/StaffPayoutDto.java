package com.exskylab.koala.core.dtos.payout.response;

import com.exskylab.koala.core.dtos.user.response.SafeUserDto;
import com.exskylab.koala.entities.PayoutStatus;

import java.time.Instant;
import java.util.UUID;

public record StaffPayoutDto(UUID id,
                             UUID jobAssignmentId,
                             SafeUserDto recipient,
                             String maskedIban,
                             String providerReference,
                             String settlementReference,
                             String amount,
                             String currency,
                             PayoutStatus status,
                             String failureReason,
                             Instant processingAt,
                             Instant completedAt,
                             Instant createdAt) {
}
