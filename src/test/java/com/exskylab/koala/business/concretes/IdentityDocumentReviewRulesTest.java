package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.ActivityNotificationService;
import com.exskylab.koala.business.abstracts.BadgeService;
import com.exskylab.koala.business.abstracts.IdentityDocumentReviewService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.helpers.PageableSanitizer;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        when(storage.uploadFile(any(), any(), any(), any())).thenReturn("front-key", "back-key");

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
    void submittingFrontAndBackCreatesAPendingReviewWithoutStampingIdentity() {
        IdentityVerification pending = reviews.submit(declared(), photo("front"), photo("back"));

        assertFalse(pending.isVerified());
        assertFalse(current.isIdentityVerified());
        assertEquals(TCKN, pending.getTcIdentityNumber());
        assertNotNull(pending.getVerificationDocument());
        assertNotNull(pending.getBackDocument());
        assertEquals("front-key", pending.getVerificationDocument().getFileUrl());
        assertEquals("back-key", pending.getBackDocument().getFileUrl());
        verify(userService, never()).save(any());
        verify(badgeService, never()).evaluate(any());
    }

    @Test
    void aSecondPendingSubmissionIsRefused() {
        when(identityVerificationDao.findPendingByUserId(current.getId()))
                .thenReturn(Optional.of(new IdentityVerification()));

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> reviews.submit(declared(), photo("front"), photo("back")));

        assertEquals("IDENTITY_DOCUMENTS_ALREADY_PENDING", thrown.getErrorCode());
        verify(identityVerificationDao, never()).save(any());
    }

    @Test
    void anAlreadyVerifiedAccountCannotSubmitPhotos() {
        current.setIdentityVerified(true);

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> reviews.submit(declared(), photo("front"), photo("back")));

        assertEquals("USER_ALREADY_IDENTITY_VERIFIED", thrown.getErrorCode());
    }

    @Test
    void staffApprovalStampsIdentityWithoutChipEvidence() {
        IdentityVerification pending = pendingRow();
        when(identityVerificationDao.findById(pending.getId())).thenReturn(Optional.of(pending));

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
        IdentityVerification pending = pendingRow();
        when(identityVerificationDao.findById(pending.getId())).thenReturn(Optional.of(pending));
        when(userService.existsByTcIdentityNumber(TCKN)).thenReturn(true);

        ConflictException thrown = assertThrows(ConflictException.class,
                () -> reviews.approve(staff, pending.getId()));

        assertEquals("TC_IDENTITY_NUMBER_ALREADY_IN_USE", thrown.getErrorCode());
        assertFalse(current.isIdentityVerified());
        verify(userService, never()).save(any());
    }

    @Test
    void staffRejectionKeepsIdentityUnverifiedAndRecordsTheReason() {
        IdentityVerification pending = pendingRow();
        when(identityVerificationDao.findById(pending.getId())).thenReturn(Optional.of(pending));

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
        when(identityVerificationDao.findById(missing)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> reviews.approve(staff, missing));
        assertThrows(ResourceNotFoundException.class,
                () -> reviews.reject(staff, missing, "okunaksız"));
    }

    @Test
    void anAlreadyDecidedReviewCannotBeApprovedAgain() {
        IdentityVerification pending = pendingRow();
        pending.setVerified(true);
        when(identityVerificationDao.findById(pending.getId())).thenReturn(Optional.of(pending));

        ConflictException thrown = assertThrows(ConflictException.class,
                () -> reviews.approve(staff, pending.getId()));

        assertEquals("IDENTITY_REVIEW_ALREADY_DECIDED", thrown.getErrorCode());
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
