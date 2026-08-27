package com.exskylab.koala.core.dtos.payment.response;

import com.exskylab.koala.entities.PaymentAttemptStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
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
    private BigDecimal workerPayout;
    private BigDecimal commissionAmount;
    private BigDecimal commissionRate;
    private BigDecimal totalCharged;
    private String failureReason;
    private Instant completedAt;
    private Instant createdAt;
}
