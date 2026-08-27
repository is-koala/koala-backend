package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.entities.Badge;
import com.exskylab.koala.entities.UserBadge;

import java.util.List;
import java.util.UUID;

public interface BadgeService {

    void evaluate(UUID userId);

    List<UserBadge> getBadgesOf(UUID userId);

    List<Badge> getCatalog();
}
