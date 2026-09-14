package com.exskylab.koala.core.dtos.jobAssignment.response;

import com.exskylab.koala.core.dtos.user.response.SafeUserDto;
import com.exskylab.koala.entities.CancellationParty;

import java.time.Instant;
import java.util.UUID;

public record AdminCancellationDto(UUID id, CancellationParty cancelledByParty, SafeUserDto cancelledBy,
                                   String reason, Long noticeMinutes, String feeRate, String feeAmount,
                                   String refundAmount, UUID refundId, Instant cancelledAt) {
}
