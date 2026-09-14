package com.exskylab.koala.core.dtos.payout.response;

import com.exskylab.koala.entities.PayoutStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PayoutDto {
    private UUID id;
    private UUID jobAssignmentId;
    @Schema(description = "Hakedişin ait olduğu ilanın başlığı.")
    private String jobTitle;
    @Schema(types = {"string", "null"},
            description = "İlanı açan şirketin adı; bireysel ilanda null.")
    private String companyName;
    private String amount;
    private String currency;
    private PayoutStatus status;
    private String failureReason;
    private Instant completedAt;
    private Instant createdAt;
}
