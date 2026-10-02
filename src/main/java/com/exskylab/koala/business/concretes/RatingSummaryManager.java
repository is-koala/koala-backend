package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.RatingSummaryService;
import com.exskylab.koala.core.helpers.RatingFigures;
import com.exskylab.koala.dataAccess.RatingSummaryDao;
import com.exskylab.koala.entities.RatingSummary;
import com.exskylab.koala.entities.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

@Service
public class RatingSummaryManager implements RatingSummaryService {

    private final RatingSummaryDao ratingSummaryDao;

    public RatingSummaryManager(RatingSummaryDao ratingSummaryDao) {
        this.ratingSummaryDao = ratingSummaryDao;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RatingSummary> findFor(UUID userId) {
        return ratingSummaryDao.findByUserId(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public RatingFigures figuresOf(UUID userId) {
        return ratingSummaryDao.findByUserId(userId)
                .map(summary -> new RatingFigures(
                        summary.getAverageRating() == null ? BigDecimal.ZERO : summary.getAverageRating(),
                        summary.getReviewCount()))
                .orElse(RatingFigures.UNRATED);
    }

    @Override
    @Transactional
    public void record(User subject, int rating) {
        RatingSummary summary = ratingSummaryDao.findByUserId(subject.getId())
                .orElseGet(() -> {
                    RatingSummary created = new RatingSummary();
                    created.setUser(subject);
                    created.setReviewCount(0);
                    created.setRatingSum(0);
                    return created;
                });

        summary.setReviewCount(summary.getReviewCount() + 1);
        summary.setRatingSum(summary.getRatingSum() + rating);
        summary.setAverageRating(BigDecimal.valueOf(summary.getRatingSum())
                .divide(BigDecimal.valueOf(summary.getReviewCount()), 2, RoundingMode.HALF_UP));

        ratingSummaryDao.save(summary);
    }
}
