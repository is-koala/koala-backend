package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.dtos.badge.request.SaveBadgeDto;
import com.exskylab.koala.entities.Badge;
import com.exskylab.koala.entities.UserBadge;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface BadgeService {

    void evaluate(UUID userId);

    List<UserBadge> getBadgesOf(UUID userId);

    List<Badge> getCatalog();

    List<Badge> getAllForAdministration();

    Badge createBadge(SaveBadgeDto request);

    Badge updateBadge(UUID badgeId, SaveBadgeDto request);

    Badge setIcon(UUID badgeId, MultipartFile icon);

    Badge setActive(UUID badgeId, boolean active);
}
