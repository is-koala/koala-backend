package com.exskylab.koala.core.dtos.dispute.response;

import com.exskylab.koala.entities.EvidenceScrubFailure;

import java.util.UUID;

public record EvidenceScrubFailureDto(UUID evidenceId, EvidenceScrubFailure reason) {
}
