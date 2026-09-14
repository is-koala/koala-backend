package com.exskylab.koala.core.dtos.payout.response;

import com.exskylab.koala.core.dtos.common.LinkedRecordDto;
import com.exskylab.koala.core.dtos.payment.response.AdminSettlementTaskDto;
import com.exskylab.koala.core.dtos.user.response.StaffPartyDto;
import com.exskylab.koala.entities.PayoutReleaseOrigin;
import com.exskylab.koala.entities.PayoutStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminPayoutDetailDto(UUID id, UUID jobAssignmentId, UUID jobId, String jobTitle, String companyName,
                                   StaffPartyDto worker, StaffPartyDto employer, UUID paymentId,
                                   UUID bankAccountId, String maskedIban, String amount, String currency,
                                   PayoutStatus status, PayoutReleaseOrigin releaseOrigin,
                                   String providerReference, String settlementReference, String failureReason,
                                   Instant processingAt, Instant completedAt, Instant settledAt,
                                   UUID reconciledByStaffId, Instant createdAt, Instant updatedAt,
                                   List<AdminSettlementTaskDto> settlementTasks,
                                   List<LinkedRecordDto> paymentAttempts) {
}
