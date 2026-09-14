package com.exskylab.koala.core.dtos.bankAccount.response;

import com.exskylab.koala.core.dtos.user.response.SafeUserDto;
import com.exskylab.koala.entities.BankAccountVerificationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record IbanPendingDto(UUID accountId, SafeUserDto user, String maskedIban,
                             String reference, BigDecimal amount, Instant startedAt,
                             Instant expiresAt, BankAccountVerificationStatus status, Integer attemptsLeft,
                             Instant createdAt) {
}
