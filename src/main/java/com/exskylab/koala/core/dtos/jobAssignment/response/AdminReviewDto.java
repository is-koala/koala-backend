package com.exskylab.koala.core.dtos.jobAssignment.response;

import java.time.Instant;
import java.util.UUID;

public record AdminReviewDto(UUID id, UUID authorId, UUID subjectId, Integer rating, String comment,
                             Boolean anonymous, Instant createdAt) {
}
