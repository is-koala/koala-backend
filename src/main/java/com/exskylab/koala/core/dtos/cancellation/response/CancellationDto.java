package com.exskylab.koala.core.dtos.cancellation.response;

import com.exskylab.koala.entities.CancellationParty;
import com.exskylab.koala.entities.RefundStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CancellationDto(UUID id, UUID jobAssignmentId, CancellationParty cancelledByParty,
                              String reason, long noticeMinutes, BigDecimal feeRate,
                              BigDecimal feeAmount, BigDecimal refundAmount, Instant cancelledAt,
                              RefundStatus refundStatus, Instant refundCompletedAt) {
}
