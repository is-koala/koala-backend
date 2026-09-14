package com.exskylab.koala.core.dtos.user.response;

import com.exskylab.koala.core.dtos.badge.response.BadgeDto;
import com.exskylab.koala.entities.IdentityReviewStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminUserDetailDto(UUID id, String firstName, String lastName, String username,
                                 String profilePictureUrl, String email, String phoneNumber,
                                 Boolean emailVerified, Boolean phoneVerified, Boolean identityVerified,
                                 Boolean isEmployer, Instant createdAt,
                                 List<AdminUserCompanyDto> companies, List<BadgeDto> badges,
                                 AdminUserCountsDto counts, IdentityReviewStatus identityReviewStatus,
                                 UUID pendingDeletionRequestId) {
}
