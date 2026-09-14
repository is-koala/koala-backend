package com.exskylab.koala.core.dtos.payment.response;

import com.exskylab.koala.core.dtos.user.response.StaffPartyDto;
import com.exskylab.koala.entities.PaymentAttemptStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminPaymentDto(UUID id, UUID jobAssignmentId, UUID jobId, String jobTitle, String companyName,
                              StaffPartyDto worker, StaffPartyDto employer,
                              PaymentAttemptStatus status, String currency, String pricingVersion,
                              PricingBreakdownDto pricing, String workerPayout, String commissionAmount,
                              String commissionRate, String totalCharged,
                              String conversationId, String providerPaymentId, String providerTransactionId,
                              String failureReason, Integer verificationAttempts, Instant nextVerificationAt,
                              Instant completedAt, Instant createdAt, Instant updatedAt,
                              List<AdminRefundDto> refunds) {
}
