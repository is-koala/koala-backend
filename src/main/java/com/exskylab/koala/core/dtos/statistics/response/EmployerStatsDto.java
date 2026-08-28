package com.exskylab.koala.core.dtos.statistics.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record EmployerStatsDto(UUID userId, long jobsPosted, long completedJobs,
                               BigDecimal averageRating, int reviewCount, Instant memberSince) {
}
