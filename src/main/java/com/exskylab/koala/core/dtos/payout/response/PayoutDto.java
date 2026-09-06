package com.exskylab.koala.core.dtos.payout.response;

import com.exskylab.koala.entities.PayoutStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PayoutDto {
    private UUID id;
    private UUID jobAssignmentId;
    private String amount;
    private String currency;
    private PayoutStatus status;
    private String failureReason;
    private Instant completedAt;
    private Instant createdAt;
}
