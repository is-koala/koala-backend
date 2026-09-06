package com.exskylab.koala.core.dtos.payment.response;

import com.exskylab.koala.entities.PaymentAttemptStatus;
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
public class PaymentAttemptDto {

    private UUID id;
    private PaymentAttemptStatus status;
    private String currency;
    private String workerPayout;
    private String commissionAmount;
    private String commissionRate;
    private String totalCharged;
    private PricingBreakdownDto pricing;
    private String failureReason;
    private Instant completedAt;
    private Instant createdAt;
}
