package com.exskylab.koala.business.concretes;

import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.business.abstracts.ActivityNotificationService;
import com.exskylab.koala.business.abstracts.BadgeService;
import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.dataAccess.JobAssignmentDao;
import com.exskylab.koala.dataAccess.RatingSummaryDao;
import com.exskylab.koala.dataAccess.ReviewDao;
import com.exskylab.koala.dataAccess.UserDao;
import com.exskylab.koala.entities.*;
import org.junit.jupiter.api.BeforeEach;
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.business.abstracts.UserService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReviewRulesTest {

    private ReviewDao reviewDao;
    private RatingSummaryDao ratingSummaryDao;
    private JobAssignmentService jobAssignmentService;
    private SecurityService securityService;
    private ActivityNotificationService notifications;
    private ReviewManager manager;

    private final User employer = user();
    private final User worker = user();
    private final User stranger = user();
    private JobAssignment assignment;

    @BeforeEach
    void setUp() {
        reviewDao = mock(ReviewDao.class);
        ratingSummaryDao = mock(RatingSummaryDao.class);
        jobAssignmentService = mock(JobAssignmentService.class);
        securityService = mock(SecurityService.class);
        UserService userService = mock(UserService.class);
        notifications = mock(ActivityNotificationService.class);

        manager = new ReviewManager(reviewDao, ratingSummaryDao, jobAssignmentService, userService,
                securityService, mock(CompanyContactService.class), new PageableSanitizer(),
                mock(BadgeService.class), notifications);

        when(reviewDao.save(any())).thenAnswer(i -> i.getArgument(0));
        when(ratingSummaryDao.save(any())).thenAnswer(i -> i.getArgument(0));
        when(ratingSummaryDao.findByUserId(any())).thenReturn(Optional.empty());
        when(userService.getReference(any())).thenAnswer(i -> {
            User u = new User();
            u.setId(i.getArgument(0));
            return u;
        });
    }

    private static User user() {
        User u = new User();
        u.setId(UUID.randomUUID());
        return u;
    }

    private JobAssignment assignment(AssignmentStatus status) {
        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setCreator(employer);

        JobAssignment a = new JobAssignment();
        a.setId(UUID.randomUUID());
        a.setJob(job);
        a.setWorker(worker);
        a.setStatus(status);

        when(jobAssignmentService.getDetailedById(a.getId())).thenReturn(a);
        return a;
    }

    @Test
    void aReviewCannotBeLeftBeforeTheWorkIsCompleted() {
        assignment = assignment(AssignmentStatus.CONFIRMED);
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());

        assertThrows(ConflictException.class,
                () -> manager.leaveReview(assignment.getId(), 5, "harika", false));
        verify(reviewDao, never()).save(any());
    }

    @Test
    void theWorkerReviewsTheEmployerAndViceVersa() {
        assignment = assignment(AssignmentStatus.COMPLETED);

        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());
        assertEquals(employer.getId(), manager.leaveReview(assignment.getId(), 5, null, false).getSubject().getId());

        when(securityService.getAuthenticatedUserId()).thenReturn(employer.getId());
        assertEquals(worker.getId(), manager.leaveReview(assignment.getId(), 4, null, false).getSubject().getId());
    }

    @Test
    void aStrangerCannotReviewSomeoneElsesJob() {
        assignment = assignment(AssignmentStatus.COMPLETED);
        when(securityService.getAuthenticatedUserId()).thenReturn(stranger.getId());

        assertThrows(ResourceNotFoundException.class,
                () -> manager.leaveReview(assignment.getId(), 5, null, false));
    }

    @Test
    void thesameAuthorCannotReviewTheSameJobTwice() {
        assignment = assignment(AssignmentStatus.COMPLETED);
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());
        when(reviewDao.existsByJobAssignmentIdAndAuthorId(assignment.getId(), worker.getId())).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> manager.leaveReview(assignment.getId(), 5, null, false));
    }

    @Test
    void ratingsOutsideOneToFiveAreRejected() {
        assignment = assignment(AssignmentStatus.COMPLETED);
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());

        assertThrows(ValidationException.class, () -> manager.leaveReview(assignment.getId(), 0, null, false));
        assertThrows(ValidationException.class, () -> manager.leaveReview(assignment.getId(), 6, null, false));
        verify(reviewDao, never()).save(any());
    }

    @Test
    void theSummaryAccumulatesInsteadOfRecomputing() {
        assignment = assignment(AssignmentStatus.COMPLETED);
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());

        RatingSummary existing = new RatingSummary();
        existing.setUser(employer);
        existing.setReviewCount(3);
        existing.setRatingSum(12);
        when(ratingSummaryDao.findByUserId(employer.getId())).thenReturn(Optional.of(existing));

        manager.leaveReview(assignment.getId(), 5, null, false);

        assertEquals(4, existing.getReviewCount());
        assertEquals(17, existing.getRatingSum());
        assertEquals(new BigDecimal("4.25"), existing.getAverageRating());
    }

    @Test
    void theFirstReviewCreatesTheSummary() {
        assignment = assignment(AssignmentStatus.COMPLETED);
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());

        manager.leaveReview(assignment.getId(), 4, null, false);

        verify(ratingSummaryDao).save(argThat(s ->
                s.getReviewCount() == 1 && s.getRatingSum() == 4
                        && s.getAverageRating().compareTo(new BigDecimal("4.00")) == 0));
    }

    @Test
    void theReviewedPersonIsToldTheyWereRated() {
        assignment = assignment(AssignmentStatus.COMPLETED);
        when(securityService.getAuthenticatedUserId()).thenReturn(employer.getId());

        manager.leaveReview(assignment.getId(), 5, "Harika", false);

        verify(notifications).reviewReceived(any(Review.class));
    }

    @Test
    void aRefusedReviewNotifiesNobody() {
        assignment = assignment(AssignmentStatus.IN_PROGRESS);
        when(securityService.getAuthenticatedUserId()).thenReturn(employer.getId());

        assertThrows(ConflictException.class, () -> manager.leaveReview(assignment.getId(), 5, null, false));

        verifyNoInteractions(notifications);
    }

    @Test
    void aReviewIsNamedUnlessTheAuthorAsksOtherwise() {
        assignment = assignment(AssignmentStatus.COMPLETED);
        when(securityService.getAuthenticatedUserId()).thenReturn(employer.getId());

        assertFalse(manager.leaveReview(assignment.getId(), 5, null, false).isAnonymous());
    }

    @Test
    void anAnonymousReviewIsStoredAsAnonymousButKeepsItsAuthor() {
        assignment = assignment(AssignmentStatus.COMPLETED);
        when(securityService.getAuthenticatedUserId()).thenReturn(employer.getId());

        Review review = manager.leaveReview(assignment.getId(), 5, "iyiydi", true);

        assertTrue(review.isAnonymous());
        assertEquals(employer.getId(), review.getAuthor().getId());
    }

    @Test
    void anAnonymousReviewStillCountsTowardsTheRating() {
        assignment = assignment(AssignmentStatus.COMPLETED);
        when(securityService.getAuthenticatedUserId()).thenReturn(employer.getId());

        manager.leaveReview(assignment.getId(), 4, null, true);

        verify(ratingSummaryDao).save(argThat(summary -> {
            assertEquals(1, summary.getReviewCount());
            assertEquals(4, summary.getRatingSum());
            return true;
        }));
    }

    @Test
    void aSecondReviewIsRefusedEvenWhenItIsAnonymous() {
        assignment = assignment(AssignmentStatus.COMPLETED);
        when(securityService.getAuthenticatedUserId()).thenReturn(employer.getId());
        when(reviewDao.existsByJobAssignmentIdAndAuthorId(assignment.getId(), employer.getId()))
                .thenReturn(true);

        assertThrows(ConflictException.class, () -> manager.leaveReview(assignment.getId(), 5, null, true));
    }
}
