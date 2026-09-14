package com.exskylab.koala.core.dtos.payment.response;

import com.exskylab.koala.entities.RefundStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminRefundDto(UUID id, UUID paymentId, String amount, String currency, RefundStatus status,
                             String reason, String providerRefundId, Instant completedAt,
                             Instant createdAt, Instant updatedAt) {
}
