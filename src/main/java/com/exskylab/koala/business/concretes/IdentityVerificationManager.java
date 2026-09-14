package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.ActivityNotificationService;
import com.exskylab.koala.business.abstracts.BadgeService;
import com.exskylab.koala.business.abstracts.IdentityDocumentReviewService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.constants.IdentityMessages;
import com.exskylab.koala.core.constants.ImageMessages;
import com.exskylab.koala.core.constants.UserMessages;
import com.exskylab.koala.core.dtos.user.request.VerifyIdentityDto;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.core.helpers.StorageRollbackCleanup;
import com.exskylab.koala.core.helpers.UploadValidator;
import com.exskylab.koala.core.producers.user.UserProducerService;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.core.utilities.storage.FolderType;
import com.exskylab.koala.core.utilities.storage.StorageService;
import com.exskylab.koala.core.helpers.StaffSearchQuery;
import com.exskylab.koala.dataAccess.IdentityVerificationDao;
import com.exskylab.koala.core.dtos.identity.response.IdentityReviewDto;
import com.exskylab.koala.entities.IdentityReviewStatus;
import com.exskylab.koala.entities.IdentityVerification;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.entities.VerificationDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class IdentityVerificationManager implements IdentityDocumentReviewService {

    private static final Locale TURKISH = Locale.forLanguageTag("tr-TR");
    private static final Set<String> SORTABLE = Set.of("createdAt", "id");

    private final IdentityVerificationDao identityVerificationDao;
    private final UserService userService;
    private final SecurityService securityService;
    private final UploadValidator uploadValidator;
    private final StorageService storageService;
    private final BadgeService badgeService;
    private final ActivityNotificationService activityNotificationService;
    private final UserProducerService userProducerService;
    private final PageableSanitizer pageableSanitizer;

    public IdentityVerificationManager(IdentityVerificationDao identityVerificationDao,
                                       UserService userService,
                                       SecurityService securityService,
                                       UploadValidator uploadValidator,
                                       StorageService storageService,
                                       BadgeService badgeService,
                                       ActivityNotificationService activityNotificationService,
                                       UserProducerService userProducerService,
                                       PageableSanitizer pageableSanitizer) {
        this.identityVerificationDao = identityVerificationDao;
        this.userService = userService;
        this.securityService = securityService;
        this.uploadValidator = uploadValidator;
        this.storageService = storageService;
        this.badgeService = badgeService;
        this.activityNotificationService = activityNotificationService;
        this.userProducerService = userProducerService;
        this.pageableSanitizer = pageableSanitizer;
    }

    @Override
    @Transactional
    public IdentityVerification submit(VerifyIdentityDto identity,
                                       MultipartFile selfie,
                                       MultipartFile front,
                                       MultipartFile back,
                                       MultipartFile together) {
        User current = securityService.getAuthenticatedUser();
        if (current.isIdentityVerified()) {
            throw new ValidationException(UserMessages.USER_ALREADY_IDENTITY_VERIFIED);
        }
        if (identityVerificationDao.findPendingByUserId(current.getId()).isPresent()) {
            throw new ValidationException(IdentityMessages.DOCUMENTS_ALREADY_PENDING);
        }

        IdentityVerification review = new IdentityVerification();
        review.setUser(current);
        review.setVerified(false);
        review.setTcIdentityNumber(identity.getTcIdentityNumber());
        review.setFirstName(identity.getFirstName());
        review.setLastName(identity.getLastName());
        review.setBirthDate(identity.getBirthDate());
        review.setTcDocumentNumber(identity.getTcDocumentNumber());
        review.setSelfieDocument(storePhoto(selfie));
        review.setVerificationDocument(storePhoto(front));
        review.setBackDocument(storePhoto(back));
        review.setTogetherDocument(storePhoto(together));
        return identityVerificationDao.save(review);
    }

    @Override
    @Transactional
    public IdentityVerification approve(StaffPrincipal actor, UUID reviewId) {
        IdentityVerification review = lockedReview(reviewId);
        requireOpen(review);
        requireFourDocuments(review);
        User subject = review.getUser();
        if (userService.existsByTcIdentityNumber(review.getTcIdentityNumber())) {
            throw new ConflictException(UserMessages.TC_IDENTITY_NUMBER_ALREADY_IN_USE);
        }

        review.setVerified(true);
        review.setVerifiedAt(Instant.now());
        review.setReviewedByStaffId(actor == null ? null : actor.staffId());

        subject.setIdentityVerified(true);
        subject.setFirstName(capitalizeFirstLetter(review.getFirstName()));
        subject.setLastName(capitalizeFirstLetter(review.getLastName()));
        subject.setBirthDate(review.getBirthDate());
        subject.setTcIdentityNumber(review.getTcIdentityNumber());
        subject.setTcDocumentNumber(review.getTcDocumentNumber());
        User saved = userService.save(subject);
        badgeService.evaluate(saved.getId());
        activityNotificationService.identityVerified(saved);
        userProducerService.publishUserProfileUpdated(saved);
        return identityVerificationDao.save(review);
    }

    @Override
    @Transactional
    public IdentityVerification reject(StaffPrincipal actor, UUID reviewId, String reason) {
        IdentityVerification review = lockedReview(reviewId);
        requireOpen(review);
        review.setRejectedAt(Instant.now());
        review.setRejectionReason(reason.trim());
        review.setReviewedByStaffId(actor == null ? null : actor.staffId());
        return identityVerificationDao.save(review);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<IdentityVerification> search(IdentityReviewStatus status, String query, Pageable pageable) {
        pageableSanitizer.rejectUnknownSort(pageable, SORTABLE);
        IdentityReviewStatus filter = status == null ? IdentityReviewStatus.PENDING : status;
        return identityVerificationDao.search(filter.name(), StaffSearchQuery.normalize(query), pageable);
    }

    @Override
    @Transactional(readOnly = true)
    public IdentityVerification getById(UUID reviewId) {
        return identityVerificationDao.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException(IdentityMessages.REVIEW_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public IdentityVerification latestForCurrentUser() {
        User current = securityService.getAuthenticatedUser();
        return identityVerificationDao.findTopByUser_IdOrderByCreatedAtDesc(current.getId())
                .orElseThrow(() -> new ResourceNotFoundException(IdentityMessages.DOCUMENTS_NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public VerificationDocument getDocumentForStaffDownload(UUID reviewId, UUID documentId) {
        IdentityVerification review = getById(reviewId);
        VerificationDocument document = matchingDocument(review, documentId);
        if (!isEvidence(document)) {
            throw new ResourceNotFoundException(IdentityMessages.DOCUMENT_NOT_FOUND);
        }
        return document;
    }

    private IdentityVerification lockedReview(UUID reviewId) {
        return identityVerificationDao.findByIdForUpdate(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException(IdentityMessages.REVIEW_NOT_FOUND));
    }

    private static VerificationDocument matchingDocument(IdentityVerification review, UUID documentId) {
        if (belongsTo(review.getSelfieDocument(), documentId)) {
            return review.getSelfieDocument();
        }
        if (belongsTo(review.getVerificationDocument(), documentId)) {
            return review.getVerificationDocument();
        }
        if (belongsTo(review.getBackDocument(), documentId)) {
            return review.getBackDocument();
        }
        if (belongsTo(review.getTogetherDocument(), documentId)) {
            return review.getTogetherDocument();
        }
        return null;
    }

    private static boolean belongsTo(VerificationDocument document, UUID documentId) {
        return document != null && documentId.equals(document.getId());
    }

    private static boolean isEvidence(VerificationDocument document) {
        return document != null && document.getFileUrl() != null && !document.getFileUrl().isBlank();
    }

    private void requireOpen(IdentityVerification review) {
        if (review.isVerified() || review.getRejectedAt() != null) {
            throw new ConflictException(IdentityMessages.REVIEW_ALREADY_DECIDED);
        }
    }

    private void requireFourDocuments(IdentityVerification review) {
        if (!isEvidence(review.getSelfieDocument())
                || !isEvidence(review.getVerificationDocument())
                || !isEvidence(review.getBackDocument())
                || !isEvidence(review.getTogetherDocument())) {
            log.warn("Identity review {} refused approval: four TCKK photo slots are not all present", review.getId());
            throw new ConflictException(IdentityMessages.REVIEW_DOCUMENTS_MISSING);
        }
    }

    private VerificationDocument storePhoto(MultipartFile photo) {
        uploadValidator.validate(photo, FolderType.IMAGE,
                IdentityMessages.DOCUMENTS_REQUIRED, ImageMessages.IMAGE_SIZE_ERROR);
        try {
            String key = storageService.uploadFile(
                    photo.getBytes(),
                    photo.getOriginalFilename(),
                    photo.getContentType(),
                    FolderType.IMAGE);
            StorageRollbackCleanup.deleteOnRollback(storageService, key);
            VerificationDocument document = new VerificationDocument();
            document.setFileName(photo.getOriginalFilename());
            document.setFileType(photo.getContentType());
            document.setFileUrl(key);
            document.setFileSize(photo.getSize());
            return document;
        } catch (ValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("TCKK photo upload failed fileName: {}", photo.getOriginalFilename(), exception);
            throw new ValidationException(ImageMessages.IMAGE_UPLOAD_ERROR);
        }
    }

    private static String capitalizeFirstLetter(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        String trimmed = input.trim();
        return trimmed.substring(0, 1).toUpperCase(TURKISH) + trimmed.substring(1).toLowerCase(TURKISH);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IdentityReviewStatus> findLatestStatusFor(UUID userId) {
        return identityVerificationDao.findTopByUser_IdOrderByCreatedAtDesc(userId)
                .map(IdentityReviewDto::statusOf);
    }
}
