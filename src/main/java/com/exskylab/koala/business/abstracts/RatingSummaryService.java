package com.exskylab.koala.business.abstracts;

import com.exskylab.koala.core.helpers.RatingFigures;
import com.exskylab.koala.entities.RatingSummary;
import com.exskylab.koala.entities.User;

import java.util.Optional;
import java.util.UUID;

public interface RatingSummaryService {

    Optional<RatingSummary> findFor(UUID userId);

    RatingFigures figuresOf(UUID userId);

    void record(User subject, int rating);
}
