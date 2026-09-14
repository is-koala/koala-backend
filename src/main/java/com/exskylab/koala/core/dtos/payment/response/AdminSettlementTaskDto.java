package com.exskylab.koala.core.dtos.payment.response;

import com.exskylab.koala.entities.SettlementOperation;
import com.exskylab.koala.entities.SettlementTaskStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminSettlementTaskDto(UUID id, SettlementOperation operation, SettlementTaskStatus status,
                                     Integer attempts, Instant nextAttemptAt, String lastError,
                                     String providerTransactionId, String providerReference, String amount,
                                     Instant completedAt, Instant createdAt, Instant updatedAt) {
}
