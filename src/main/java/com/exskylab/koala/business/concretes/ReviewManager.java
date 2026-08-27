package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.business.abstracts.ReviewService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.constants.ReviewMessages;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.dataAccess.JobAssignmentDao;
import com.exskylab.koala.dataAccess.RatingSummaryDao;
import com.exskylab.koala.dataAccess.ReviewDao;
import com.exskylab.koala.dataAccess.UserDao;
import com.exskylab.koala.entities.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ReviewManager implements ReviewService {

    private static final Set<String> SORTABLE = Set.of("createdAt", "rating");
    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.DESC, "createdAt");

    private final Logger logger = LoggerFactory.getLogger(ReviewManager.class);

    private final ReviewDao reviewDao;
    private final RatingSummaryDao ratingSummaryDao;
    private final JobAssignmentDao jobAssignmentDao;
    private final UserDao userDao;
    private final SecurityService securityService;
    private final CompanyContactService companyContactService;
    private final PageableSanitizer pageableSanitizer;

    public ReviewManager(ReviewDao reviewDao, RatingSummaryDao ratingSummaryDao,
                         JobAssignmentDao jobAssignmentDao, UserDao userDao,
                         SecurityService securityService, CompanyContactService companyContactService,
                         PageableSanitizer pageableSanitizer) {
        this.reviewDao = reviewDao;
        this.ratingSummaryDao = ratingSummaryDao;
        this.jobAssignmentDao = jobAssignmentDao;
        this.userDao = userDao;
        this.securityService = securityService;
        this.companyContactService = companyContactService;
        this.pageableSanitizer = pageableSanitizer;
    }

    @Override
    @Transactional
    public Review leaveReview(UUID jobAssignmentId, int rating, String comment) {
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException(ReviewMessages.RATING_RANGE);
        }

        JobAssignment assignment = jobAssignmentDao.findWithDetailsForPaymentById(jobAssignmentId)
                .orElseThrow(() -> new ResourceNotFoundException(ReviewMessages.ASSIGNMENT_NOT_FOUND));

        if (assignment.getStatus() != AssignmentStatus.COMPLETED) {
            throw new IllegalStateException(ReviewMessages.WORK_NOT_COMPLETED);
        }

        UUID authorId = securityService.getAuthenticatedUserId();
        User subject = resolveSubject(assignment, authorId);

        if (reviewDao.existsByJobAssignmentIdAndAuthorId(jobAssignmentId, authorId)) {
            throw new IllegalStateException(ReviewMessages.ALREADY_REVIEWED);
        }

        Review review = new Review();
        review.setJobAssignment(assignment);
        review.setAuthor(userDao.getReferenceById(authorId));
        review.setSubject(subject);
        review.setRating(rating);
        review.setComment(comment);

        Review saved = reviewDao.save(review);
        applyToSummary(subject, rating);

        logger.info("Review {} left on assignment {} about user {}", saved.getId(), jobAssignmentId, subject.getId());
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
