package com.exskylab.koala.core.dtos.legal.response;

import com.exskylab.koala.entities.AgreementType;

import java.time.Instant;
import java.util.UUID;

public record ConsentRecordDto(UUID id, AgreementType type, String version, boolean granted,
                               Instant recordedAt, Instant revokedAt) {
}
