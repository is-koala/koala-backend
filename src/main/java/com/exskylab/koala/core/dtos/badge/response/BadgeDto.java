package com.exskylab.koala.core.dtos.badge.response;

import com.exskylab.koala.entities.BadgeFamily;
import com.exskylab.koala.entities.BadgeTier;

import java.time.Instant;

public record BadgeDto(String code, BadgeFamily family, BadgeTier tier,
                       String name, String description, String iconUrl, Instant earnedAt) {
}
