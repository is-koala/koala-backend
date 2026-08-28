package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.business.abstracts.BadgeService;
import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.business.abstracts.ReviewService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.ReviewMessages;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.dataAccess.RatingSummaryDao;
import com.exskylab.koala.dataAccess.ReviewDao;
import com.exskylab.koala.entities.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.business.abstracts.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class ReviewManager implements ReviewService {

    private static final Set<String> SORTABLE = Set.of("createdAt", "rating");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final ReviewDao reviewDao;
    private final RatingSummaryDao ratingSummaryDao;
    private final JobAssignmentService jobAssignmentService;
    private final UserService userService;
    private final SecurityService securityService;
    private final CompanyContactService companyContactService;
    private final PageableSanitizer pageableSanitizer;
    private final BadgeService badgeService;

    public ReviewManager(ReviewDao reviewDao, RatingSummaryDao ratingSummaryDao,
                         JobAssignmentService jobAssignmentService, UserService userService,
                         SecurityService securityService, CompanyContactService companyContactService,
                         PageableSanitizer pageableSanitizer, BadgeService badgeService) {
        this.reviewDao = reviewDao;
        this.ratingSummaryDao = ratingSummaryDao;
        this.jobAssignmentService = jobAssignmentService;
        this.userService = userService;
        this.securityService = securityService;
        this.companyContactService = companyContactService;
        this.pageableSanitizer = pageableSanitizer;
        this.badgeService = badgeService;
    }

    @Override
    @Transactional
    public Review leaveReview(UUID jobAssignmentId, int rating, String comment) {
        if (rating < 1 || rating > 5) {
            throw new ValidationException(ReviewMessages.RATING_RANGE);
        }

        JobAssignment assignment = jobAssignmentService.getDetailedById(jobAssignmentId);

        if (assignment.getStatus() != AssignmentStatus.COMPLETED) {
            throw new ConflictException(ReviewMessages.WORK_NOT_COMPLETED);
        }

        UUID authorId = securityService.getAuthenticatedUserId();
        User subject = resolveSubject(assignment, authorId);

        if (reviewDao.existsByJobAssignmentIdAndAuthorId(jobAssignmentId, authorId)) {
            throw new ConflictException(ReviewMessages.ALREADY_REVIEWED);
        }

        Review review = new Review();
        review.setJobAssignment(assignment);
        review.setAuthor(userService.getReference(authorId));
        review.setSubject(subject);
        review.setRating(rating);
        review.setComment(comment);

        Review saved = reviewDao.save(review);
        applyToSummary(subject, rating);
        badgeService.evaluate(subject.getId());

        log.info("Review {} left on assignment {} about user {}", saved.getId(), jobAssignmentId, subject.getId());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Review> getReviewsAbout(UUID userId, Pageable requested) {
        pageableSanitizer.rejectUnknownSort(requested, SORTABLE);
        return reviewDao.findAllBySubjectIdOrderByCreatedAtDesc(userId,
                pageableSanitizer.sanitize(requested, SORTABLE, DEFAULT_SORT));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RatingSummary> getSummaryFor(UUID userId) {
        return ratingSummaryDao.findByUserId(userId);
    }

    private User resolveSubject(JobAssignment assignment, UUID authorId) {
        User worker = assignment.getWorker();
        Job job = assignment.getJob();

        if (worker.getId().equals(authorId)) {
            return job.getCreator();
        }

        boolean isEmployerSide = job.getCreator().getId().equals(authorId)
                || (job.getCompany() != null
                    && companyContactService.isUserAContactOfCompany(job.getCompany().getId(), authorId));

        if (isEmployerSide) {
            return worker;
        }

        throw new ResourceNotFoundException(ReviewMessages.NOT_A_PARTICIPANT);
    }

    private void applyToSummary(User subject, int rating) {
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
