package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.entities.RatingSummary;
import com.exskylab.koala.entities.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface ReviewService {

    Review leaveReview(UUID jobAssignmentId, int rating, String comment);

    Page<Review> getReviewsAbout(UUID userId, Pageable pageable);

    Optional<RatingSummary> getSummaryFor(UUID userId);
}
