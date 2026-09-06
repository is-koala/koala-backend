package com.exskylab.koala.core.dtos.bankAccount.response;

import com.exskylab.koala.entities.BankAccountVerificationStatus;

import java.util.UUID;

public record BankAccountDto(UUID id, String maskedIban, boolean primary,
                             BankAccountVerificationStatus verificationStatus) {
}
