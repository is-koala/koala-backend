package com.exskylab.koala.core.dtos.payout.response;

import com.exskylab.koala.entities.PayoutStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Atamanın hakediş kaydı; emanet henüz çıkışa verilmediyse null.")
public record AssignmentPayoutDto(UUID id, PayoutStatus status,
                                  @Schema(types = {"string", "null"}, format = "date-time",
                                          description = "Hakedişin işçinin hesabına ulaştığı doğrulanan an; henüz doğrulanmadıysa null.")
                                  Instant completedAt) {
}
