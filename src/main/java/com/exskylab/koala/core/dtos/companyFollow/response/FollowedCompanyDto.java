package com.exskylab.koala.core.dtos.companyFollow.response;

import java.time.Instant;
import java.util.UUID;

public record FollowedCompanyDto(UUID companyId, String name, String logoUrl, String description,
                                 long followerCount, Instant followedAt) {
}
