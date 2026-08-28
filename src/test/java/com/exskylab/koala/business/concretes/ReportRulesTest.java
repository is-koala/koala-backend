package com.exskylab.koala.business.concretes;

import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.dataAccess.JobDao;
import com.exskylab.koala.dataAccess.MessageDao;
import com.exskylab.koala.dataAccess.ReportDao;
import com.exskylab.koala.dataAccess.UserDao;
import com.exskylab.koala.entities.*;
import org.junit.jupiter.api.BeforeEach;
import com.exskylab.koala.business.abstracts.JobService;
import com.exskylab.koala.business.abstracts.ImageService;
import com.exskylab.koala.business.abstracts.MessagingService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.constants.ReportMessages;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReportRulesTest {

    private ReportDao reportDao;
    private SecurityService securityService;
    private ReportManager manager;

    private final UUID reporterId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reportDao = mock(ReportDao.class);
        securityService = mock(SecurityService.class);
        UserService userService = mock(UserService.class);
        JobService jobService = mock(JobService.class);
        MessagingService messagingService = mock(MessagingService.class);

        manager = new ReportManager(reportDao, userService, jobService, messagingService, securityService,
                mock(ImageService.class), new com.exskylab.koala.core.helpers.PageableSanitizer());

        when(reportDao.save(any())).thenAnswer(i -> i.getArgument(0));
        when(securityService.getAuthenticatedUserId()).thenReturn(reporterId);
        when(userService.getReference(any())).thenAnswer(i -> {
            User u = new User();
            u.setId(i.getArgument(0));
            return u;
        });
        when(userService.getById(any())).thenAnswer(i -> {
            User u = new User();
            u.setId(i.getArgument(0));
            return u;
        });
    }

    @Test
    void nobodyCanReportThemselves() {
        assertThrows(ValidationException.class,
                () -> manager.createReport(reporterId, null, null, null, ReportReason.HARASSMENT, "ben"));
    }

    @Test
    void aNewReportStartsOpen() {
        Report report = manager.createReport(UUID.randomUUID(), null, null, null,
                ReportReason.OFF_PLATFORM_PAYMENT, "elden ödeme teklif etti");

        assertEquals(ReportStatus.OPEN, report.getStatus());
        assertEquals(reporterId, report.getReporter().getId());
    }

    @Test
    void anAlreadyReviewedReportCannotBeReviewedTwice() {
        Report report = new Report();
        report.setId(UUID.randomUUID());
        report.setStatus(ReportStatus.ACTIONED);
        when(reportDao.findById(report.getId())).thenReturn(Optional.of(report));
        when(securityService.getAuthenticatedUser()).thenReturn(new User());

        assertThrows(ConflictException.class,
                () -> manager.review(report.getId(), ReportStatus.DISMISSED, "yine"));
    }

    @Test
    void reviewingStampsTheAdminAndTheTime() {
        Report report = new Report();
        report.setId(UUID.randomUUID());
        report.setStatus(ReportStatus.OPEN);
        when(reportDao.findById(report.getId())).thenReturn(Optional.of(report));

        User admin = new User();
        admin.setId(UUID.randomUUID());
        when(securityService.getAuthenticatedUser()).thenReturn(admin);

        Report reviewed = manager.review(report.getId(), ReportStatus.ACTIONED, "hesap askıya alındı");

        assertEquals(ReportStatus.ACTIONED, reviewed.getStatus());
        assertEquals(admin.getId(), reviewed.getReviewedBy().getId());
        assertNotNull(reviewed.getReviewedAt());
    }

    @Test
    void aReportAboutAMissingUserIsRejected() {
        UUID ghost = UUID.randomUUID();
        UserService userService = mock(UserService.class);
        when(userService.getReference(any())).thenReturn(new User());
        when(userService.getById(ghost)).thenThrow(new ResourceNotFoundException(ReportMessages.NOT_FOUND));

        ReportManager withGhost = new ReportManager(reportDao, userService, mock(JobService.class),
                mock(MessagingService.class), securityService, mock(ImageService.class),
                new com.exskylab.koala.core.helpers.PageableSanitizer());

        assertThrows(ResourceNotFoundException.class,
                () -> withGhost.createReport(ghost, null, null, null, ReportReason.FRAUD, "yok"));
    }
}
