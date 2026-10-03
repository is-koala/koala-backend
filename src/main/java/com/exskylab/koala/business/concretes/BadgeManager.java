package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.business.abstracts.BadgeService;
import com.exskylab.koala.business.abstracts.ImageService;
import com.exskylab.koala.business.abstracts.RatingSummaryService;
import com.exskylab.koala.core.constants.BadgeMessages;
import com.exskylab.koala.core.dtos.badge.request.SaveBadgeDto;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.dataAccess.*;
import com.exskylab.koala.entities.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class BadgeManager implements BadgeService {

    private final BadgeDao badgeDao;
    private final UserBadgeDao userBadgeDao;
    private final UserDao userDao;
    private final JobAssignmentDao jobAssignmentDao;
    private final RatingSummaryService ratingSummaryService;
    private final ImageService imageService;

    public BadgeManager(BadgeDao badgeDao, UserBadgeDao userBadgeDao, UserDao userDao,
                        JobAssignmentDao jobAssignmentDao, RatingSummaryService ratingSummaryService,
                        ImageService imageService) {
        this.badgeDao = badgeDao;
        this.userBadgeDao = userBadgeDao;
        this.userDao = userDao;
        this.jobAssignmentDao = jobAssignmentDao;
        this.ratingSummaryService = ratingSummaryService;
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

            log.info("Badge {} granted to user {}", badge.getCode(), userId);
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
            throw new ConflictException(BadgeMessages.CODE_ALREADY_EXISTS);
        }

        Badge badge = new Badge();
        badge.setCode(request.getCode());
        apply(badge, request);

        Badge saved = badgeDao.save(badge);
        log.info("Badge {} created", saved.getCode());
        return saved;
    }

    @Override
    @Transactional
    public Badge updateBadge(UUID badgeId, SaveBadgeDto request) {
        Badge badge = requireBadge(badgeId);
        apply(badge, request);

        Badge saved = badgeDao.save(badge);
        log.info("Badge {} updated", saved.getCode());
        return saved;
    }

    @Override
    @Transactional
    public Badge setIcon(UUID badgeId, MultipartFile icon) {
        Badge badge = requireBadge(badgeId);
        badge.setIcon(imageService.uploadImage(icon));

        Badge saved = badgeDao.save(badge);
        log.info("Badge {} icon updated", saved.getCode());
        return saved;
    }

    @Override
    @Transactional
    public Badge setActive(UUID badgeId, boolean active) {
        Badge badge = requireBadge(badgeId);
        badge.setActive(active);

        Badge saved = badgeDao.save(badge);
        log.info("Badge {} active flag set to {}", saved.getCode(), active);
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
            log.warn("Badge {} has no minCount, rule skipped for user {}", badge.getCode(), userId);
            return false;
        }
        return jobAssignmentDao.countByWorkerIdAndStatus(userId, AssignmentStatus.COMPLETED)
                >= badge.getMinCount();
    }

    private boolean isRatedAtLeast(Badge badge, UUID userId) {
        BigDecimal minAverage = badge.getMinAverageRating();
        Integer minReviews = badge.getMinReviewCount();

        if (minAverage == null || minReviews == null) {
            log.warn("Badge {} has no rating threshold, rule skipped for user {}", badge.getCode(), userId);
            return false;
        }

        return ratingSummaryService.findFor(userId)
                .filter(summary -> summary.getReviewCount() >= minReviews)
                .filter(summary -> summary.getAverageRating() != null)
                .filter(summary -> summary.getAverageRating().compareTo(minAverage) >= 0)
                .isPresent();
    }
}
