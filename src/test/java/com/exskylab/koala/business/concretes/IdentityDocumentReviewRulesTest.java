package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.ActivityNotificationService;
import com.exskylab.koala.business.abstracts.BadgeService;
import com.exskylab.koala.business.abstracts.IdentityDocumentReviewService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.core.dtos.identity.response.IdentityReviewDto;
import com.exskylab.koala.core.dtos.user.request.VerifyIdentityDto;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.helpers.UploadValidator;
import com.exskylab.koala.core.producers.user.UserProducerService;
import com.exskylab.koala.core.security.StaffPrincipal;
import com.exskylab.koala.core.utilities.storage.StorageService;
import com.exskylab.koala.dataAccess.IdentityVerificationDao;
import com.exskylab.koala.entities.IdentityVerification;
import com.exskylab.koala.entities.StaffPermission;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.entities.VerificationDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IdentityDocumentReviewRulesTest {

    private static final String TCKN = "10000000146";

    private IdentityVerificationDao identityVerificationDao;
    private UserService userService;
    private BadgeService badgeService;
    private ActivityNotificationService notifications;
    private UserProducerService producer;
    private IdentityDocumentReviewService reviews;

    private User current;
    private StaffPrincipal staff;

    @BeforeEach
    void setUp() {
        identityVerificationDao = mock(IdentityVerificationDao.class);
        userService = mock(UserService.class);
        badgeService = mock(BadgeService.class);
        notifications = mock(ActivityNotificationService.class);
        producer = mock(UserProducerService.class);

        current = new User();
        current.setId(UUID.randomUUID());

        SecurityService security = mock(SecurityService.class);
        when(security.getAuthenticatedUser()).thenReturn(current);

        StorageService storage = mock(StorageService.class);
        when(storage.uploadFile(any(), any(), any(), any()))
                .thenReturn("selfie-key", "front-key", "back-key", "together-key");

        reviews = new IdentityVerificationManager(
                identityVerificationDao, userService, security, mock(UploadValidator.class), storage,
                badgeService, notifications, producer, new PageableSanitizer());

        when(identityVerificationDao.findPendingByUserId(current.getId())).thenReturn(Optional.empty());
        when(identityVerificationDao.save(any())).thenAnswer(invocation -> {
            IdentityVerification row = invocation.getArgument(0);
            if (row.getId() == null) {
                row.setId(UUID.randomUUID());
            }
            return row;
        });
        when(userService.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        staff = new StaffPrincipal(UUID.randomUUID(), "trust@iskoala.com", "Trust Lideri",
                Set.of("TRUST_LEAD"), Set.of(StaffPermission.IDENTITY_REVIEW), false);
    }

    private static VerifyIdentityDto declared() {
        VerifyIdentityDto dto = new VerifyIdentityDto();
        dto.setFirstName("yusuf");
        dto.setLastName("acmaci");
        dto.setBirthDate(LocalDate.of(1998, 4, 12));
        dto.setTcIdentityNumber(TCKN);
        dto.setTcDocumentNumber("A01A00000");
        return dto;
    }

    private static MockMultipartFile photo(String name) {
        return new MockMultipartFile(name, name + ".jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    @Test
    void submittingFourPhotosCreatesAPendingReviewWithoutStampingIdentity() {
        IdentityVerification pending = submitFour();

        assertFalse(pending.isVerified());
        assertFalse(current.isIdentityVerified());
        assertEquals(TCKN, pending.getTcIdentityNumber());
        assertNotNull(pending.getSelfieDocument());
        assertNotNull(pending.getVerificationDocument());
        assertNotNull(pending.getBackDocument());
        assertNotNull(pending.getTogetherDocument());
        assertEquals("selfie-key", pending.getSelfieDocument().getFileUrl());
        assertEquals("front-key", pending.getVerificationDocument().getFileUrl());
        assertEquals("back-key", pending.getBackDocument().getFileUrl());
        assertEquals("together-key", pending.getTogetherDocument().getFileUrl());
        verify(userService, never()).save(any());
        verify(badgeService, never()).evaluate(any());
    }

    @Test
    void theReviewDtoNamesTheFourPhotoSlots() {
        IdentityVerification pending = pendingRowWithPhotos();

        IdentityReviewDto dto = IdentityReviewDto.from(pending);

        assertEquals(pending.getSelfieDocument().getId(), dto.getSelfieDocumentId());
        assertEquals(pending.getVerificationDocument().getId(), dto.getFrontDocumentId());
        assertEquals(pending.getBackDocument().getId(), dto.getBackDocumentId());
        assertEquals(pending.getTogetherDocument().getId(), dto.getTogetherDocumentId());
    }

    @Test
    void aSecondPendingSubmissionIsRefused() {
        when(identityVerificationDao.findPendingByUserId(current.getId()))
                .thenReturn(Optional.of(new IdentityVerification()));

        ValidationException thrown = assertThrows(ValidationException.class, this::submitFour);

        assertEquals("IDENTITY_DOCUMENTS_ALREADY_PENDING", thrown.getErrorCode());
        verify(identityVerificationDao, never()).save(any());
    }

    @Test
    void anAlreadyVerifiedAccountCannotSubmitPhotos() {
        current.setIdentityVerified(true);

        ValidationException thrown = assertThrows(ValidationException.class, this::submitFour);

        assertEquals("USER_ALREADY_IDENTITY_VERIFIED", thrown.getErrorCode());
    }

    @Test
    void staffApprovalStampsIdentityWithoutChipEvidence() {
        IdentityVerification pending = pendingRowWithPhotos();
        stored(pending);

        IdentityVerification approved = reviews.approve(staff, pending.getId());

        assertTrue(approved.isVerified());
        assertTrue(current.isIdentityVerified());
        assertEquals(TCKN, current.getTcIdentityNumber());
        assertEquals("Yusuf", current.getFirstName());
        assertEquals("Acmaci", current.getLastName());
        assertEquals(LocalDate.of(1998, 4, 12), current.getBirthDate());
        assertEquals("A01A00000", current.getTcDocumentNumber());
        assertEquals(staff.staffId(), approved.getReviewedByStaffId());
        verify(badgeService).evaluate(current.getId());
        verify(notifications).identityVerified(current);
        verify(producer).publishUserProfileUpdated(current);
    }

    @Test
    void approvalRefusesATcIdentityNumberAlreadyClaimed() {
        IdentityVerification pending = pendingRowWithPhotos();
        stored(pending);
        when(userService.existsByTcIdentityNumber(TCKN)).thenReturn(true);

        ConflictException thrown = assertThrows(ConflictException.class,
                () -> reviews.approve(staff, pending.getId()));

        assertEquals("TC_IDENTITY_NUMBER_ALREADY_IN_USE", thrown.getErrorCode());
        assertFalse(current.isIdentityVerified());
        verify(userService, never()).save(any());
    }

    @Test
    void staffRejectionKeepsIdentityUnverifiedAndRecordsTheReason() {
        IdentityVerification pending = pendingRowWithPhotos();
        stored(pending);

        IdentityVerification rejected = reviews.reject(staff, pending.getId(), "Fotoğraflar okunaksız");

        assertFalse(rejected.isVerified());
        assertFalse(current.isIdentityVerified());
        assertEquals("Fotoğraflar okunaksız", rejected.getRejectionReason());
        assertNotNull(rejected.getRejectedAt());
        assertEquals(staff.staffId(), rejected.getReviewedByStaffId());
        verify(badgeService, never()).evaluate(any());
    }

    @Test
    void aMissingReviewCannotBeDecided() {
        UUID missing = UUID.randomUUID();
        when(identityVerificationDao.findByIdForUpdate(missing)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> reviews.approve(staff, missing));
        assertThrows(ResourceNotFoundException.class,
                () -> reviews.reject(staff, missing, "okunaksız"));
    }

    @Test
    void anAlreadyDecidedReviewCannotBeApprovedAgain() {
        IdentityVerification pending = pendingRowWithPhotos();
        pending.setVerified(true);
        stored(pending);

        ConflictException thrown = assertThrows(ConflictException.class,
                () -> reviews.approve(staff, pending.getId()));

        assertEquals("IDENTITY_REVIEW_ALREADY_DECIDED", thrown.getErrorCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"selfie", "front", "back", "together"})
    void approvalRefusesAPendingReviewMissingAnyOfTheFourDocuments(String missingSlot) {
        IdentityVerification pending = pendingRowWithPhotos();
        clearSlot(pending, missingSlot);
        stored(pending);

        ConflictException thrown = assertThrows(ConflictException.class,
                () -> reviews.approve(staff, pending.getId()));

        assertEquals("IDENTITY_REVIEW_DOCUMENTS_MISSING", thrown.getErrorCode());
        assertFalse(pending.isVerified());
        assertNull(pending.getRejectedAt());
        assertFalse(current.isIdentityVerified());
        verify(userService, never()).save(any());
        verify(identityVerificationDao, never()).save(any());
        verify(badgeService, never()).evaluate(any());
    }

    @Test
    void aLegacyTwoPhotoReviewCannotBeApproved() {
        IdentityVerification legacy = legacyTwoPhotoRow();
        stored(legacy);

        ConflictException thrown = assertThrows(ConflictException.class,
                () -> reviews.approve(staff, legacy.getId()));

        assertEquals("IDENTITY_REVIEW_DOCUMENTS_MISSING", thrown.getErrorCode());
        assertFalse(legacy.isVerified());
        assertFalse(current.isIdentityVerified());
        verify(userService, never()).save(any());
        verify(identityVerificationDao, never()).save(any());
    }

    @Test
    void aLegacyTwoPhotoReviewCanStillBeRejected() {
        IdentityVerification legacy = legacyTwoPhotoRow();
        stored(legacy);

        IdentityVerification rejected = reviews.reject(staff, legacy.getId(), "Yüz ve birlikte fotoğrafı eksik");

        assertNotNull(rejected.getRejectedAt());
        assertEquals("Yüz ve birlikte fotoğrafı eksik", rejected.getRejectionReason());
        assertFalse(current.isIdentityVerified());
    }

    @Test
    void aDocumentWhoseStorageKeyWasClearedDoesNotCountAsEvidence() {
        IdentityVerification pending = pendingRowWithPhotos();
        pending.getTogetherDocument().setFileUrl(" ");
        stored(pending);

        ConflictException thrown = assertThrows(ConflictException.class,
                () -> reviews.approve(staff, pending.getId()));

        assertEquals("IDENTITY_REVIEW_DOCUMENTS_MISSING", thrown.getErrorCode());
        assertFalse(current.isIdentityVerified());
    }

    @Test
    void approvalDecidesOnTheRowLockedForUpdateNotOnAnUnlockedRead() {
        IdentityVerification pending = pendingRowWithPhotos();
        when(identityVerificationDao.findById(pending.getId())).thenReturn(Optional.of(pending));
        when(identityVerificationDao.findByIdForUpdate(pending.getId())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> reviews.approve(staff, pending.getId()));
        assertThrows(ResourceNotFoundException.class, () -> reviews.reject(staff, pending.getId(), "okunaksız"));

        assertFalse(current.isIdentityVerified());
        verify(identityVerificationDao, never()).findById(any());
    }

    @Test
    void anApprovalThatLandsAfterARejectionFailsClosed() {
        IdentityVerification decided = pendingRowWithPhotos();
        decided.setRejectedAt(Instant.now());
        decided.setRejectionReason("Önce reddedildi");
        stored(decided);

        ConflictException thrown = assertThrows(ConflictException.class,
                () -> reviews.approve(staff, decided.getId()));

        assertEquals("IDENTITY_REVIEW_ALREADY_DECIDED", thrown.getErrorCode());
        assertFalse(decided.isVerified());
        assertFalse(current.isIdentityVerified());
        verify(userService, never()).save(any());
    }

    @Test
    void aRejectionThatLandsAfterAnApprovalFailsClosed() {
        IdentityVerification decided = pendingRowWithPhotos();
        decided.setVerified(true);
        stored(decided);

        ConflictException thrown = assertThrows(ConflictException.class,
                () -> reviews.reject(staff, decided.getId(), "geç kalan ret"));

        assertEquals("IDENTITY_REVIEW_ALREADY_DECIDED", thrown.getErrorCode());
        assertTrue(decided.isVerified());
        assertNull(decided.getRejectedAt());
        verify(identityVerificationDao, never()).save(any());
    }

    @Test
    void theCurrentUserCanReadTheirLatestReviewIncludingARejection() {
        IdentityVerification rejected = pendingRow();
        rejected.setRejectionReason("Fotoğraflar okunaksız");
        when(identityVerificationDao.findTopByUser_IdOrderByCreatedAtDesc(current.getId()))
                .thenReturn(Optional.of(rejected));

        IdentityVerification latest = reviews.latestForCurrentUser();

        assertEquals(rejected.getId(), latest.getId());
        assertEquals("Fotoğraflar okunaksız", latest.getRejectionReason());
    }

    @Test
    void aUserWithoutDocumentsCannotReadAReview() {
        when(identityVerificationDao.findTopByUser_IdOrderByCreatedAtDesc(current.getId()))
                .thenReturn(Optional.empty());

        ResourceNotFoundException thrown = assertThrows(ResourceNotFoundException.class,
                reviews::latestForCurrentUser);

        assertEquals("IDENTITY_DOCUMENTS_NOT_FOUND", thrown.getErrorCode());
    }

    @Test
    void staffCanDownloadAllFourPhotosOfAReview() {
        IdentityVerification pending = pendingRowWithPhotos();
        when(identityVerificationDao.findById(pending.getId())).thenReturn(Optional.of(pending));

        assertEquals("images/selfie-key.jpg",
                reviews.getDocumentForStaffDownload(pending.getId(), pending.getSelfieDocument().getId())
                        .getFileUrl());
        assertEquals("images/front-key.jpg",
                reviews.getDocumentForStaffDownload(pending.getId(), pending.getVerificationDocument().getId())
                        .getFileUrl());
        assertEquals("images/back-key.jpg",
                reviews.getDocumentForStaffDownload(pending.getId(), pending.getBackDocument().getId())
                        .getFileUrl());
        assertEquals("images/together-key.jpg",
                reviews.getDocumentForStaffDownload(pending.getId(), pending.getTogetherDocument().getId())
                        .getFileUrl());
    }

    @Test
    void aDocumentThatDoesNotBelongToTheReviewIsConcealed() {
        IdentityVerification pending = pendingRowWithPhotos();
        when(identityVerificationDao.findById(pending.getId())).thenReturn(Optional.of(pending));

        ResourceNotFoundException thrown = assertThrows(ResourceNotFoundException.class,
                () -> reviews.getDocumentForStaffDownload(pending.getId(), UUID.randomUUID()));

        assertEquals("IDENTITY_DOCUMENT_NOT_FOUND", thrown.getErrorCode());
    }

    @Test
    void aMissingReviewCannotBeDownloaded() {
        UUID missing = UUID.randomUUID();
        when(identityVerificationDao.findById(missing)).thenReturn(Optional.empty());

        ResourceNotFoundException thrown = assertThrows(ResourceNotFoundException.class,
                () -> reviews.getDocumentForStaffDownload(missing, UUID.randomUUID()));

        assertEquals("IDENTITY_REVIEW_NOT_FOUND", thrown.getErrorCode());
    }

    @Test
    void aPhotoWhoseStorageKeyWasClearedCannotBeDownloaded() {
        IdentityVerification pending = pendingRowWithPhotos();
        pending.getVerificationDocument().setFileUrl(" ");
        when(identityVerificationDao.findById(pending.getId())).thenReturn(Optional.of(pending));

        ResourceNotFoundException thrown = assertThrows(ResourceNotFoundException.class,
                () -> reviews.getDocumentForStaffDownload(
                        pending.getId(), pending.getVerificationDocument().getId()));

        assertEquals("IDENTITY_DOCUMENT_NOT_FOUND", thrown.getErrorCode());
    }

    private IdentityVerification submitFour() {
        return reviews.submit(declared(), photo("selfie"), photo("front"), photo("back"), photo("together"));
    }

    private IdentityVerification pendingRowWithPhotos() {
        IdentityVerification pending = pendingRow();
        pending.setSelfieDocument(photoDocument("selfie.jpg", "images/selfie-key.jpg"));
        pending.setVerificationDocument(photoDocument("front.jpg", "images/front-key.jpg"));
        pending.setBackDocument(photoDocument("back.jpg", "images/back-key.jpg"));
        pending.setTogetherDocument(photoDocument("together.jpg", "images/together-key.jpg"));
        return pending;
    }

    private void stored(IdentityVerification row) {
        when(identityVerificationDao.findById(row.getId())).thenReturn(Optional.of(row));
        when(identityVerificationDao.findByIdForUpdate(row.getId())).thenReturn(Optional.of(row));
    }

    private IdentityVerification legacyTwoPhotoRow() {
        IdentityVerification legacy = pendingRow();
        legacy.setVerificationDocument(photoDocument("front.jpg", "images/front-key.jpg"));
        legacy.setBackDocument(photoDocument("back.jpg", "images/back-key.jpg"));
        return legacy;
    }

    private static void clearSlot(IdentityVerification review, String slot) {
        switch (slot) {
            case "selfie" -> review.setSelfieDocument(null);
            case "front" -> review.setVerificationDocument(null);
            case "back" -> review.setBackDocument(null);
            case "together" -> review.setTogetherDocument(null);
            default -> throw new IllegalArgumentException(slot);
        }
    }

    private static VerificationDocument photoDocument(String fileName, String key) {
        VerificationDocument document = new VerificationDocument();
        document.setId(UUID.randomUUID());
        document.setFileName(fileName);
        document.setFileType("image/jpeg");
        document.setFileUrl(key);
        document.setFileSize(3L);
        return document;
    }

    private IdentityVerification pendingRow() {
        IdentityVerification pending = new IdentityVerification();
        pending.setId(UUID.randomUUID());
        pending.setUser(current);
        pending.setTcIdentityNumber(TCKN);
        pending.setFirstName("yusuf");
        pending.setLastName("acmaci");
        pending.setBirthDate(LocalDate.of(1998, 4, 12));
        pending.setTcDocumentNumber("A01A00000");
        return pending;
    }
}
