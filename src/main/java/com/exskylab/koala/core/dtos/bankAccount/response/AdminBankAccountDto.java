package com.exskylab.koala.core.dtos.bankAccount.response;

import com.exskylab.koala.entities.BankAccountVerificationStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminBankAccountDto(UUID id, String maskedIban, BankAccountVerificationStatus verificationStatus,
                                  boolean primary, boolean accountHolderNameVerified, Instant verifiedAt,
                                  Instant createdAt) {
}
