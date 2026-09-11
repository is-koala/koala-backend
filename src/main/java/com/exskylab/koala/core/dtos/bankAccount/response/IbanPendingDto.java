package com.exskylab.koala.core.dtos.bankAccount.response;

import com.exskylab.koala.core.dtos.user.response.SafeUserDto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record IbanPendingDto(UUID accountId, SafeUserDto user, String maskedIban,
                             String reference, BigDecimal amount, Instant startedAt,
                             Instant expiresAt, Instant createdAt) {
}
