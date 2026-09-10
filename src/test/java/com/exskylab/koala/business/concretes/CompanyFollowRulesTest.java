package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.business.abstracts.JobService;
import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.business.abstracts.ReviewService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.dtos.statistics.response.EmployerStatsDto;
import com.exskylab.koala.core.helpers.MediaUrlResolver;
import com.exskylab.koala.core.helpers.StorageUrlResolver;
import com.exskylab.koala.core.properties.CompanyFollowProperties;
import com.exskylab.koala.dataAccess.CompanyFollowDao;
import com.exskylab.koala.entities.Company;
import com.exskylab.koala.entities.CompanyFollow;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.RatingSummary;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;

class CompanyFollowRulesTest {

    private CompanyFollowDao companyFollowDao;
    private NotificationService notificationService;
    private CompanyFollowProperties properties;
    private CompanyFollowManager manager;

    private final Company company = company();
    private final UUID followerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        companyFollowDao = mock(CompanyFollowDao.class);
        notificationService = mock(NotificationService.class);
        properties = new CompanyFollowProperties();

        UserService userService = mock(UserService.class);
        when(userService.getReference(any())).thenAnswer(call -> {
            User reference = new User();
            reference.setId(call.getArgument(0));
            return reference;
        });

        manager = new CompanyFollowManager(companyFollowDao, userService, notificationService,
                new MediaUrlResolver(mock(StorageUrlResolver.class)), properties,
                new com.exskylab.koala.core.helpers.PageableSanitizer());
    }

    @Test
    void followingTwiceCreatesOneRecord() {
        when(companyFollowDao.existsByCompanyIdAndFollowerId(company.getId(), followerId)).thenReturn(true);

        manager.follow(company, followerId);

        verify(companyFollowDao, never()).save(any());
    }

    @Test
    void aFirstFollowIsRecorded() {
        manager.follow(company, followerId);

        var captor = org.mockito.ArgumentCaptor.forClass(CompanyFollow.class);
        verify(companyFollowDao).save(captor.capture());
        assertEquals(company.getId(), captor.getValue().getCompany().getId());
        assertEquals(followerId, captor.getValue().getFollower().getId());
    }

    @Test
    void unfollowingSomethingYouDoNotFollowIsSilent() {
        when(companyFollowDao.findByCompanyIdAndFollowerId(any(), any())).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> manager.unfollow(company.getId(), followerId));
        verify(companyFollowDao, never()).delete(any());
    }

    @Test
    void anIndividualJobNotifiesNobody() {
        manager.notifyFollowers(job(null, UUID.randomUUID()));

        verify(companyFollowDao, never()).findAllByCompanyId(any());
    }

    @Test
    void theJobCreatorIsNotNotifiedAboutTheirOwnJob() {
        UUID creatorId = UUID.randomUUID();
        when(companyFollowDao.findAllByCompanyId(company.getId()))
                .thenReturn(List.of(follow(creatorId), follow(UUID.randomUUID())));

        manager.notifyFollowers(job(company, creatorId));

        verify(notificationService).preparePushRecipients(argThat(ids ->
                ids.size() == 1 && !ids.contains(creatorId)));
        verify(notificationService, times(1)).sendPushToUser(any(), any(), anyBoolean());
    }

    @Test
    void oneFailedDeliveryDoesNotStopTheRest() {
        when(companyFollowDao.findAllByCompanyId(company.getId()))
                .thenReturn(List.of(follow(UUID.randomUUID()), follow(UUID.randomUUID())));
        doThrow(new IllegalStateException("push down")).doNothing()
                .when(notificationService).sendPushToUser(any(), any(), anyBoolean());

        assertDoesNotThrow(() -> manager.notifyFollowers(job(company, UUID.randomUUID())));
        verify(notificationService, times(2)).sendPushToUser(any(), any(), anyBoolean());
    }

    @Test
    void theFeatureCanBeSilencedEntirely() {
        properties.setEnabled(false);

        manager.notifyFollowers(job(company, UUID.randomUUID()));

        verifyNoInteractions(notificationService);
    }

    @Test
    void employerStatisticsFallBackToZeroWithoutReviews() {
        JobService jobService = mock(JobService.class);
        JobAssignmentService jobAssignmentService = mock(JobAssignmentService.class);
        ReviewService reviewService = mock(ReviewService.class);
        UserService userService = mock(UserService.class);

        UUID employerId = UUID.randomUUID();
        User employer = new User();
        employer.setId(employerId);
        employer.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));

        when(userService.getById(employerId)).thenReturn(employer);
        when(jobService.countJobsCreatedBy(employerId)).thenReturn(7L);
        when(jobAssignmentService.countCompletedForEmployer(employerId)).thenReturn(4L);
        when(reviewService.getSummaryFor(employerId)).thenReturn(Optional.empty());

        EmployerStatsDto stats = new StatisticsManager(jobService, jobAssignmentService,
                reviewService, userService).getEmployerStats(employerId);

        assertEquals(7L, stats.jobsPosted());
        assertEquals(4L, stats.completedJobs());
        assertEquals(BigDecimal.ZERO, stats.averageRating());
        assertEquals(0, stats.reviewCount());
        assertEquals(employer.getCreatedAt(), stats.memberSince());
    }

    @Test
    void employerStatisticsReadTheRatingSummaryWhenThereIsOne() {
        JobService jobService = mock(JobService.class);
        JobAssignmentService jobAssignmentService = mock(JobAssignmentService.class);
        ReviewService reviewService = mock(ReviewService.class);
        UserService userService = mock(UserService.class);

        UUID employerId = UUID.randomUUID();
        when(userService.getById(employerId)).thenReturn(new User());

        RatingSummary summary = new RatingSummary();
        summary.setAverageRating(new BigDecimal("4.75"));
        summary.setReviewCount(12);
        when(reviewService.getSummaryFor(employerId)).thenReturn(Optional.of(summary));

        EmployerStatsDto stats = new StatisticsManager(jobService, jobAssignmentService,
                reviewService, userService).getEmployerStats(employerId);

        assertEquals(new BigDecimal("4.75"), stats.averageRating());
        assertEquals(12, stats.reviewCount());
    }

    private CompanyFollow follow(UUID userId) {
        User user = new User();
        user.setId(userId);

        CompanyFollow entry = new CompanyFollow();
        entry.setId(UUID.randomUUID());
        entry.setCompany(company);
        entry.setFollower(user);
        return entry;
    }

    private static Company company() {
        Company company = new Company();
        company.setId(UUID.randomUUID());
        company.setName("Koala AŞ");
        return company;
    }

    private static Job job(Company company, UUID creatorId) {
        User creator = new User();
        creator.setId(creatorId);

        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setTitle("Barista");
        job.setCompany(company);
        job.setCreator(creator);
        return job;
    }
}
