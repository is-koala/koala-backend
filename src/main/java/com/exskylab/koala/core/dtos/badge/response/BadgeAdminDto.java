package com.exskylab.koala.core.dtos.badge.response;

import com.exskylab.koala.entities.BadgeFamily;
import com.exskylab.koala.entities.BadgeRuleType;
import com.exskylab.koala.entities.BadgeTier;

import java.math.BigDecimal;
import java.util.UUID;

public record BadgeAdminDto(UUID id, String code, BadgeFamily family, BadgeTier tier,
                            String name, String description, String iconUrl, int sortOrder,
                            BadgeRuleType ruleType, Integer minCount,
                            BigDecimal minAverageRating, Integer minReviewCount, boolean active) {
}
