package com.exskylab.koala.core.utilities.identity;

import java.time.LocalDate;

public record PassiveAuthenticationResult(String nationalIdentityNumber, LocalDate birthDate,
                                          MachineReadableZone machineReadableZone,
                                          AdditionalPersonalDetails additionalDetails,
                                          String documentSigner, String evidenceDigest) {

    public java.util.Optional<AdditionalPersonalDetails> nationalName() {
        return java.util.Optional.ofNullable(additionalDetails);
    }
}
