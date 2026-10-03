package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.entities.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ReviewService {

    Review leaveReview(UUID jobAssignmentId, int rating, String comment, boolean anonymous);

    Page<Review> getReviewsAbout(UUID userId, Pageable pageable);

    List<Review> getReviewsForStaff(UUID jobAssignmentId);
}
