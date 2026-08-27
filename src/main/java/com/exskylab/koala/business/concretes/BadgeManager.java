package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.BadgeService;
import com.exskylab.koala.business.abstracts.ImageService;
import com.exskylab.koala.core.constants.BadgeMessages;
import com.exskylab.koala.core.dtos.badge.request.SaveBadgeDto;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.dataAccess.*;
import com.exskylab.koala.entities.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class BadgeManager implements BadgeService {

    private final Logger logger = LoggerFactory.getLogger(BadgeManager.class);

    private final BadgeDao badgeDao;
    private final UserBadgeDao userBadgeDao;
    private final UserDao userDao;
    private final JobAssignmentDao jobAssignmentDao;
    private final RatingSummaryDao ratingSummaryDao;
    private final ImageService imageService;

    public BadgeManager(BadgeDao badgeDao, UserBadgeDao userBadgeDao, UserDao userDao,
                        JobAssignmentDao jobAssignmentDao, RatingSummaryDao ratingSummaryDao,
                        ImageService imageService) {
        this.badgeDao = badgeDao;
        this.userBadgeDao = userBadgeDao;
        this.userDao = userDao;
        this.jobAssignmentDao = jobAssignmentDao;
        this.ratingSummaryDao = ratingSummaryDao;
        this.imageService = imageService;
    }

    @Override
    @Transactional
    public void evaluate(UUID userId) {
        for (Badge badge : badgeDao.findAllByActiveTrueOrderBySortOrderAsc()) {
            if (userBadgeDao.existsByUserIdAndBadgeId(userId, badge.getId())) {
                continue;
            }
            if (!isSatisfied(badge, userId)) {
                continue;
            }

            UserBadge earned = new UserBadge();
            earned.setUser(userDao.getReferenceById(userId));
            earned.setBadge(badge);
            earned.setEarnedAt(Instant.now());
            userBadgeDao.save(earned);

            logger.info("Badge {} granted to user {}", badge.getCode(), userId);
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
        return badgeDao.findAllByActiveTrueOrderBySortOrderAsc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Badge> getAllForAdministration() {
        return badgeDao.findAllByOrderBySortOrderAsc();
    }

    @Override
    @Transactional
    public Badge createBadge(SaveBadgeDto request) {
        if (badgeDao.existsByCode(request.getCode())) {
            throw new IllegalStateException(BadgeMessages.CODE_ALREADY_EXISTS);
        }

        Badge badge = new Badge();
        badge.setCode(request.getCode());
        apply(badge, request);

        Badge saved = badgeDao.save(badge);
        logger.info("Badge {} created", saved.getCode());
        return saved;
    }

    @Override
    @Transactional
    public Badge updateBadge(UUID badgeId, SaveBadgeDto request) {
        Badge badge = requireBadge(badgeId);
        apply(badge, request);

        Badge saved = badgeDao.save(badge);
        logger.info("Badge {} updated", saved.getCode());
        return saved;
    }

    @Override
    @Transactional
    public Badge setIcon(UUID badgeId, MultipartFile icon) {
        Badge badge = requireBadge(badgeId);
        badge.setIcon(imageService.uploadImage(icon));

        Badge saved = badgeDao.save(badge);
        logger.info("Badge {} icon updated", saved.getCode());
        return saved;
    }

    @Override
    @Transactional
    public Badge setActive(UUID badgeId, boolean active) {
        Badge badge = requireBadge(badgeId);
        badge.setActive(active);

        Badge saved = badgeDao.save(badge);
        logger.info("Badge {} active flag set to {}", saved.getCode(), active);
        return saved;
    }

    private Badge requireBadge(UUID badgeId) {
        return badgeDao.findById(badgeId)
                .orElseThrow(() -> new ResourceNotFoundException(BadgeMessages.NOT_FOUND));
    }

    private void apply(Badge badge, SaveBadgeDto request) {
        badge.setFamily(request.getFamily());
        badge.setTier(request.getTier());
        badge.setName(request.getName());
        badge.setDescription(request.getDescription());
        badge.setSortOrder(request.getSortOrder());
        badge.setRuleType(request.getRuleType());
        badge.setMinCount(request.getMinCount());
        badge.setMinAverageRating(request.getMinAverageRating());
        badge.setMinReviewCount(request.getMinReviewCount());

        if (request.getActive() != null) {
            badge.setActive(request.getActive());
        }

        assertRuleIsUsable(badge);
    }

    private void assertRuleIsUsable(Badge badge) {
        switch (badge.getRuleType()) {
            case COMPLETED_ASSIGNMENTS -> {
                if (badge.getMinCount() == null || badge.getMinCount() < 1) {
                    throw new IllegalArgumentException(BadgeMessages.MIN_COUNT_REQUIRED);
                }
            }
            case AVERAGE_RATING_WITH_MINIMUM_REVIEWS -> {
                if (badge.getMinAverageRating() == null || badge.getMinReviewCount() == null
                        || badge.getMinReviewCount() < 1) {
                    throw new IllegalArgumentException(BadgeMessages.RATING_THRESHOLD_REQUIRED);
                }
            }
            case IDENTITY_VERIFIED -> {
            }
        }
    }

    private boolean isSatisfied(Badge badge, UUID userId) {
        return switch (badge.getRuleType()) {
            case COMPLETED_ASSIGNMENTS -> hasCompletedAtLeast(badge, userId);
            case AVERAGE_RATING_WITH_MINIMUM_REVIEWS -> isRatedAtLeast(badge, userId);
            case IDENTITY_VERIFIED -> userDao.findById(userId)
                    .map(User::isIdentityVerified)
                    .orElse(false);
        };
    }

    private boolean hasCompletedAtLeast(Badge badge, UUID userId) {
        if (badge.getMinCount() == null) {
            logger.warn("Badge {} has no minCount, rule skipped for user {}", badge.getCode(), userId);
            return false;
        }
        return jobAssignmentDao.countByWorkerIdAndStatus(userId, AssignmentStatus.COMPLETED)
                >= badge.getMinCount();
    }

    private boolean isRatedAtLeast(Badge badge, UUID userId) {
        BigDecimal minAverage = badge.getMinAverageRating();
        Integer minReviews = badge.getMinReviewCount();

        if (minAverage == null || minReviews == null) {
            logger.warn("Badge {} has no rating threshold, rule skipped for user {}", badge.getCode(), userId);
            return false;
        }

        return ratingSummaryDao.findByUserId(userId)
                .filter(summary -> summary.getReviewCount() >= minReviews)
                .filter(summary -> summary.getAverageRating() != null)
                .filter(summary -> summary.getAverageRating().compareTo(minAverage) >= 0)
                .isPresent();
    }
}
