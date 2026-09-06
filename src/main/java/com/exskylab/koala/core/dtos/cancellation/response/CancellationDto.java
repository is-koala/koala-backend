package com.exskylab.koala.core.dtos.cancellation.response;

import com.exskylab.koala.entities.CancellationParty;
import com.exskylab.koala.entities.RefundStatus;

import java.time.Instant;
import java.util.UUID;

public record CancellationDto(UUID id, UUID jobAssignmentId, CancellationParty cancelledByParty,
                              String reason, long noticeMinutes, String feeRate,
                              String feeAmount, String refundAmount, Instant cancelledAt,
                              RefundStatus refundStatus, Instant refundCompletedAt) {
}
