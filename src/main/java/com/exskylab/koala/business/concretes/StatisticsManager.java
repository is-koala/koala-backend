package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.business.abstracts.JobService;
import com.exskylab.koala.business.abstracts.RatingSummaryService;
import com.exskylab.koala.business.abstracts.StatisticsService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.dtos.statistics.response.EmployerStatsDto;
import com.exskylab.koala.core.helpers.RatingFigures;
import com.exskylab.koala.entities.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
public class StatisticsManager implements StatisticsService {

    private final JobService jobService;
    private final JobAssignmentService jobAssignmentService;
    private final RatingSummaryService ratingSummaryService;
    private final UserService userService;

    public StatisticsManager(JobService jobService, JobAssignmentService jobAssignmentService,
                             RatingSummaryService ratingSummaryService, UserService userService) {
        this.jobService = jobService;
        this.jobAssignmentService = jobAssignmentService;
        this.ratingSummaryService = ratingSummaryService;
        this.userService = userService;
    }

    @Override
    @Transactional(readOnly = true)
    public EmployerStatsDto getEmployerStats(UUID userId) {
        log.debug("Building employer statistics for user {}", userId);

        User user = userService.getById(userId);
        RatingFigures rating = ratingSummaryService.figuresOf(userId);

        return new EmployerStatsDto(
                userId,
                jobService.countJobsCreatedBy(userId),
                jobAssignmentService.countCompletedForEmployer(userId),
                rating.averageRating(),
                rating.reviewCount(),
                user.getCreatedAt());
    }
}
