package com.exskylab.koala.core.dtos.bankAccount.response;

import com.exskylab.koala.entities.BankAccountVerificationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record IbanVerificationDto(UUID accountId, String maskedIban,
                                  BankAccountVerificationStatus status, String reference,
                                  BigDecimal amount, String targetIban, Instant expiresAt,
                                  int attemptsLeft) {
}
