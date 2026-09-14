package com.exskylab.koala.core.dtos.payment.response;

import com.exskylab.koala.entities.RefundOrigin;
import com.exskylab.koala.entities.RefundStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminRefundDto(UUID id, UUID paymentId, String amount, String currency, RefundStatus status,
                             String reason, String providerRefundId, RefundOrigin origin, UUID originId,
                             Instant completedAt, Instant createdAt, Instant updatedAt) {
}
