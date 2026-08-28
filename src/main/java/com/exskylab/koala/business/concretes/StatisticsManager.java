package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.business.abstracts.JobService;
import com.exskylab.koala.business.abstracts.ReviewService;
import com.exskylab.koala.business.abstracts.StatisticsService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.dtos.statistics.response.EmployerStatsDto;
import com.exskylab.koala.entities.RatingSummary;
import com.exskylab.koala.entities.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
public class StatisticsManager implements StatisticsService {

    private final JobService jobService;
    private final JobAssignmentService jobAssignmentService;
    private final ReviewService reviewService;
    private final UserService userService;

    public StatisticsManager(JobService jobService, JobAssignmentService jobAssignmentService,
                             ReviewService reviewService, UserService userService) {
        this.jobService = jobService;
        this.jobAssignmentService = jobAssignmentService;
        this.reviewService = reviewService;
        this.userService = userService;
    }

    @Override
    @Transactional(readOnly = true)
    public EmployerStatsDto getEmployerStats(UUID userId) {
        log.debug("Building employer statistics for user {}", userId);

        User user = userService.getById(userId);
        Optional<RatingSummary> summary = reviewService.getSummaryFor(userId);

        return new EmployerStatsDto(
                userId,
                jobService.countJobsCreatedBy(userId),
                jobAssignmentService.countCompletedForEmployer(userId),
                summary.map(RatingSummary::getAverageRating).orElse(BigDecimal.ZERO),
                summary.map(RatingSummary::getReviewCount).orElse(0),
                user.getCreatedAt());
    }
}
