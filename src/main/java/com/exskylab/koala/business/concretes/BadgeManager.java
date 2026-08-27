package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.BadgeService;
import com.exskylab.koala.core.properties.BadgeProperties;
import com.exskylab.koala.dataAccess.*;
import com.exskylab.koala.entities.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class BadgeManager implements BadgeService {

    private final Logger logger = LoggerFactory.getLogger(BadgeManager.class);

    private final BadgeDao badgeDao;
    private final UserBadgeDao userBadgeDao;
    private final UserDao userDao;
    private final JobAssignmentDao jobAssignmentDao;
    private final RatingSummaryDao ratingSummaryDao;
    private final BadgeProperties badgeProperties;

    public BadgeManager(BadgeDao badgeDao, UserBadgeDao userBadgeDao, UserDao userDao,
                        JobAssignmentDao jobAssignmentDao, RatingSummaryDao ratingSummaryDao,
                        BadgeProperties badgeProperties) {
        this.badgeDao = badgeDao;
        this.userBadgeDao = userBadgeDao;
        this.userDao = userDao;
        this.jobAssignmentDao = jobAssignmentDao;
        this.ratingSummaryDao = ratingSummaryDao;
        this.badgeProperties = badgeProperties;
    }

    @Override
    @Transactional
    public void evaluate(UUID userId) {
        for (BadgeCriterion criterion : BadgeCriterion.values()) {
            Optional<Badge> badge = badgeDao.findByCode(criterion.name());
            if (badge.isEmpty()) {
                continue;
            }
            if (userBadgeDao.existsByUserIdAndBadgeId(userId, badge.get().getId())) {
                continue;
            }
            if (!isSatisfied(criterion, userId)) {
                continue;
            }

            UserBadge earned = new UserBadge();
            earned.setUser(userDao.getReferenceById(userId));
            earned.setBadge(badge.get());
            earned.setEarnedAt(Instant.now());
            userBadgeDao.save(earned);

            logger.info("Badge {} granted to user {}", criterion.name(), userId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserBadge> getBadgesOf(UUID userId) {
        return userBadgeDao.findAllByUserIdOrderByBadgeSortOrderAsc(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Badge> getCatalog() {
        return badgeDao.findAllByOrderBySortOrderAsc();
    }

    private boolean isSatisfied(BadgeCriterion criterion, UUID userId) {
        BadgeProperties.Threshold threshold = badgeProperties.forCode(criterion.name());

        return switch (criterion.getRuleType()) {
            case COMPLETED_ASSIGNMENTS -> hasCompletedAtLeast(userId, threshold.getMinCount());
            case AVERAGE_RATING_WITH_MINIMUM_REVIEWS -> isRatedAtLeast(userId,
                    threshold.getMinAverageRating(), threshold.getMinReviewCount());
            case IDENTITY_VERIFIED -> userDao.findById(userId)
                    .map(User::isIdentityVerified)
                    .orElse(false);
        };
    }

    private boolean hasCompletedAtLeast(UUID userId, Integer minCount) {
        if (minCount == null) {
            logger.warn("No minCount configured, badge rule skipped for user {}", userId);
            return false;
        }
        return jobAssignmentDao.countByWorkerIdAndStatus(userId, AssignmentStatus.COMPLETED) >= minCount;
    }

    private boolean isRatedAtLeast(UUID userId, BigDecimal minAverage, Integer minReviews) {
        if (minAverage == null || minReviews == null) {
            logger.warn("No rating threshold configured, badge rule skipped for user {}", userId);
            return false;
        }

        return ratingSummaryDao.findByUserId(userId)
                .filter(summary -> summary.getReviewCount() >= minReviews)
                .filter(summary -> summary.getAverageRating() != null)
                .filter(summary -> summary.getAverageRating().compareTo(minAverage) >= 0)
                .isPresent();
    }
}
