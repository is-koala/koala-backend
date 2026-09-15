package com.exskylab.koala.core.dtos.identity.response;

import com.exskylab.koala.entities.IdentityVerificationMethod;

import java.time.Instant;
import java.time.LocalDate;

public record WorkerIdentityDto(String fullName, String tcIdentityNumber, LocalDate birthDate, Instant verifiedAt,
                                IdentityVerificationMethod verificationMethod) {
}
