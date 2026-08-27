package com.exskylab.koala.core.dtos.legal.response;

import com.exskylab.koala.entities.AgreementType;

import java.time.Instant;
import java.util.UUID;

public record AgreementVersionDto(UUID id, AgreementType type, String version, String locale,
                                  String contentUrl, boolean mandatory, Instant effectiveFrom) {
}
