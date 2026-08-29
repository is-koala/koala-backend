package com.exskylab.koala.core.utilities.identity;

import java.time.LocalDate;

public record PassiveAuthenticationResult(String nationalIdentityNumber, LocalDate birthDate,
                                          String documentSigner, String evidenceDigest) {
}
