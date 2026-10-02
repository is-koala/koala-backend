package com.exskylab.koala.business.concretes;

import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.dataAccess.*;
import com.exskylab.koala.core.dtos.badge.request.SaveBadgeDto;
import com.exskylab.koala.entities.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BadgeRulesTest {

    private BadgeDao badgeDao;
    private UserBadgeDao userBadgeDao;
    private JobAssignmentDao jobAssignmentDao;
    private RatingSummaryDao ratingSummaryDao;
    private BadgeManager manager;

    private final UUID userId = UUID.randomUUID();
    private Badge silver;
    private Badge gold;

    @BeforeEach
    void setUp() {
        badgeDao = mock(BadgeDao.class);
        userBadgeDao = mock(UserBadgeDao.class);
        jobAssignmentDao = mock(JobAssignmentDao.class);
        ratingSummaryDao = mock(RatingSummaryDao.class);
        UserDao userDao = mock(UserDao.class);

        manager = new BadgeManager(badgeDao, userBadgeDao, userDao, jobAssignmentDao,
                new RatingSummaryManager(ratingSummaryDao), mock(com.exskylab.koala.business.abstracts.ImageService.class));

        silver = badge("GEZGIN_SILVER", BadgeTier.SILVER, 5);
        gold = badge("GEZGIN_GOLD", BadgeTier.GOLD, 25);
        catalogue(silver, gold);

        when(userBadgeDao.save(any())).thenAnswer(i -> i.getArgument(0));
        when(badgeDao.save(any())).thenAnswer(i -> i.getArgument(0));
        when(userDao.getReferenceById(any())).thenAnswer(i -> {
            User u = new User();
            u.setId(i.getArgument(0));
            return u;
        });
    }

    private Badge badge(String code, BadgeTier tier, Integer minCount) {
        Badge badge = new Badge();
        badge.setId(UUID.randomUUID());
        badge.setCode(code);
        badge.setFamily(BadgeFamily.GEZGIN);
        badge.setTier(tier);
        badge.setName(code);
        badge.setActive(true);
        badge.setRuleType(BadgeRuleType.COMPLETED_ASSIGNMENTS);
        badge.setMinCount(minCount);
        when(badgeDao.findByCode(code)).thenReturn(Optional.of(badge));
        return badge;
    }

    private void catalogue(Badge... badges) {
        when(badgeDao.findAllByActiveTrueOrderBySortOrderAsc()).thenReturn(java.util.List.of(badges));
    }

    private void completedJobs(long count) {
        when(jobAssignmentDao.countByWorkerIdAndStatus(userId, AssignmentStatus.COMPLETED)).thenReturn(count);
    }

    @Test
    void aWorkerBelowTheThresholdEarnsNothing() {
        completedJobs(4);

        manager.evaluate(userId);

        verify(userBadgeDao, never()).save(any());
    }

    @Test
    void reachingTheSilverThresholdGrantsSilverOnly() {
        completedJobs(5);

        manager.evaluate(userId);

        verify(userBadgeDao).save(argThat(earned -> earned.getBadge().getCode().equals("GEZGIN_SILVER")));
        verify(userBadgeDao, never()).save(argThat(earned -> earned.getBadge().getCode().equals("GEZGIN_GOLD")));
    }

    @Test
    void reachingTheGoldThresholdGrantsBothTiers() {
        completedJobs(30);

        manager.evaluate(userId);

        verify(userBadgeDao, times(2)).save(any());
    }

    @Test
    void aBadgeIsNeverGrantedTwice() {
        completedJobs(30);
        when(userBadgeDao.existsByUserIdAndBadgeId(userId, silver.getId())).thenReturn(true);

        manager.evaluate(userId);

        verify(userBadgeDao, times(1)).save(any());
        verify(userBadgeDao).save(argThat(earned -> earned.getBadge().getCode().equals("GEZGIN_GOLD")));
    }

    @Test
    void anEarnedBadgeIsNotTakenBackWhenTheCountLaterDrops() {
        completedJobs(30);
        when(userBadgeDao.existsByUserIdAndBadgeId(userId, silver.getId())).thenReturn(true);
        when(userBadgeDao.existsByUserIdAndBadgeId(userId, gold.getId())).thenReturn(true);

        completedJobs(0);
        manager.evaluate(userId);

        verify(userBadgeDao, never()).save(any());
        verify(userBadgeDao, never()).delete(any());
        verify(userBadgeDao, never()).deleteById(any());
    }

    @Test
    void aBadgeWithoutAThresholdGrantsNothingRatherThanEverything() {
        silver.setMinCount(null);
        gold.setMinCount(null);
        completedJobs(1000);

        manager.evaluate(userId);

        verify(userBadgeDao, never()).save(any());
    }

    @Test
    void aDeactivatedBadgeIsNoLongerGrantedButHoldersKeepIt() {
        gold.setActive(false);
        catalogue(silver);
        completedJobs(1000);

        manager.evaluate(userId);

        verify(userBadgeDao, times(1)).save(any());
        verify(userBadgeDao, never()).delete(any());
    }

    @Test
    void aRatingRuleNeedsBothTheAverageAndTheSampleSize() {
        silver.setRuleType(BadgeRuleType.AVERAGE_RATING_WITH_MINIMUM_REVIEWS);
        silver.setMinAverageRating(new BigDecimal("4.50"));
        silver.setMinReviewCount(10);
        catalogue(silver);

        RatingSummary tooFewReviews = new RatingSummary();
        tooFewReviews.setReviewCount(1);
        tooFewReviews.setAverageRating(new BigDecimal("5.00"));
        when(ratingSummaryDao.findByUserId(userId)).thenReturn(Optional.of(tooFewReviews));

        manager.evaluate(userId);
        verify(userBadgeDao, never()).save(any());

        RatingSummary enough = new RatingSummary();
        enough.setReviewCount(12);
        enough.setAverageRating(new BigDecimal("4.60"));
        when(ratingSummaryDao.findByUserId(userId)).thenReturn(Optional.of(enough));

        manager.evaluate(userId);
        verify(userBadgeDao, times(1)).save(any());
    }

    @Test
    void aNewBadgeNeedsNoCodeChangeOnlyARow() {
        Badge invented = badge("PET_WHISPERER", BadgeTier.BRONZE, 3);
        invented.setFamily(BadgeFamily.OTHER);
        catalogue(invented);
        completedJobs(3);

        manager.evaluate(userId);

        verify(userBadgeDao).save(argThat(earned -> earned.getBadge().getCode().equals("PET_WHISPERER")));
    }

    @Test
    void aDuplicateCodeIsRejected() {
        when(badgeDao.existsByCode("GEZGIN_GOLD")).thenReturn(true);

        SaveBadgeDto request = new SaveBadgeDto();
        request.setCode("GEZGIN_GOLD");
        request.setFamily(BadgeFamily.GEZGIN);
        request.setTier(BadgeTier.GOLD);
        request.setName("Altin");
        request.setSortOrder(0);
        request.setRuleType(BadgeRuleType.COMPLETED_ASSIGNMENTS);
        request.setMinCount(10);

        assertThrows(ConflictException.class, () -> manager.createBadge(request));
    }
}
