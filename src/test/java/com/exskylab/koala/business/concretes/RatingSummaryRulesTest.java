package com.exskylab.koala.business.concretes;

import com.exskylab.koala.core.helpers.RatingFigures;
import com.exskylab.koala.dataAccess.RatingSummaryDao;
import com.exskylab.koala.entities.RatingSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RatingSummaryRulesTest {

    private final UUID userId = UUID.randomUUID();
    private RatingSummaryDao ratingSummaryDao;
    private RatingSummaryManager manager;

    @BeforeEach
    void setUp() {
        ratingSummaryDao = mock(RatingSummaryDao.class);
        manager = new RatingSummaryManager(ratingSummaryDao);
    }

    @Test
    void aUserNobodyReviewedHasZeroFiguresNotNull() {
        when(ratingSummaryDao.findByUserId(userId)).thenReturn(Optional.empty());

        RatingFigures figures = manager.figuresOf(userId);

        assertEquals(BigDecimal.ZERO, figures.averageRating());
        assertEquals(0, figures.reviewCount());
    }

    @Test
    void aSummaryIsReadAsItIsStored() {
        when(ratingSummaryDao.findByUserId(userId)).thenReturn(Optional.of(summary(3, new BigDecimal("4.33"))));

        RatingFigures figures = manager.figuresOf(userId);

        assertEquals(new BigDecimal("4.33"), figures.averageRating());
        assertEquals(3, figures.reviewCount());
    }

    @Test
    void aSummaryWithoutAnAverageIsUnratedRatherThanACountWithAZeroStarAverage() {
        when(ratingSummaryDao.findByUserId(userId)).thenReturn(Optional.of(summary(2, null)));

        RatingFigures figures = manager.figuresOf(userId);

        assertEquals(RatingFigures.UNRATED, figures);
        assertEquals(BigDecimal.ZERO, figures.averageRating());
        assertEquals(0, figures.reviewCount());
    }

    @Test
    void theRawSummaryKeepsItsOwnShapeForTheRatingSummaryEndpoint() {
        when(ratingSummaryDao.findByUserId(userId)).thenReturn(Optional.of(summary(2, null)));

        Optional<RatingSummary> raw = manager.findFor(userId);

        assertTrue(raw.isPresent());
        assertEquals(2, raw.get().getReviewCount());
        assertNull(raw.get().getAverageRating());
    }

    private static RatingSummary summary(int reviewCount, BigDecimal averageRating) {
        RatingSummary summary = new RatingSummary();
        summary.setReviewCount(reviewCount);
        summary.setRatingSum(reviewCount * 4L);
        summary.setAverageRating(averageRating);
        return summary;
    }
}
