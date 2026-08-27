package com.exskylab.koala.core.dtos.review.response;

import java.math.BigDecimal;
import java.util.UUID;

public record RatingSummaryDto(UUID userId, int reviewCount, BigDecimal averageRating) {
}
