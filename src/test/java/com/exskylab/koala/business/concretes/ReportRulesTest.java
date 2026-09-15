package com.exskylab.koala.business.concretes;

import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.StaffAccountService;
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
import com.exskylab.koala.core.security.StaffPrincipal;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ReportRulesTest {

    private ReportDao reportDao;
    private SecurityService securityService;
    private StaffAccountService staffAccountService;
    private MessagingService messagingService;
    private ReportManager manager;

    private final UUID reporterId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        reportDao = mock(ReportDao.class);
        securityService = mock(SecurityService.class);
        staffAccountService = mock(StaffAccountService.class);
        UserService userService = mock(UserService.class);
        JobService jobService = mock(JobService.class);
        messagingService = mock(MessagingService.class);

        manager = new ReportManager(reportDao, userService, jobService, messagingService, securityService,
                mock(ImageService.class), mock(com.exskylab.koala.core.mappers.ReportMapper.class),
                staffAccountService, mock(com.exskylab.koala.business.abstracts.JobAssignmentService.class),
                new com.exskylab.koala.core.helpers.PageableSanitizer());

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
        when(staffAccountService.getReference(any())).thenAnswer(i -> {
            StaffMember member = new StaffMember();
            member.setId(i.getArgument(0));
            return member;
        });
    }

    @Test
    void nobodyCanReportThemselves() {
        assertThrows(ValidationException.class,
                () -> manager.createReport(reporterId, null, null, null, ReportReason.HARASSMENT, "ben"));
    }

    @Test
    void aSenderCannotReportTheirOwnMessage() {
        UUID messageId = UUID.randomUUID();
        Message message = new Message();
        message.setId(messageId);
        User sender = new User();
        sender.setId(reporterId);
        message.setSender(sender);
        when(messagingService.getMessageForReport(messageId, reporterId)).thenReturn(message);

        assertThrows(ValidationException.class,
                () -> manager.createReport(null, null, messageId, null, ReportReason.HARASSMENT, "ben"));
        verify(reportDao, never()).save(any());
    }

    @Test
    void anUnauthorizedMessageTargetNeverCreatesAReport() {
        UUID messageId = UUID.randomUUID();
        when(messagingService.getMessageForReport(messageId, reporterId))
                .thenThrow(new ResourceNotFoundException("{messaging.message.not.found}"));

        assertThrows(ResourceNotFoundException.class,
                () -> manager.createReport(null, null, messageId, null, ReportReason.SPAM, "yok"));
        verify(reportDao, never()).save(any());
    }

    @Test
    void aDifferentRetryPayloadNeverCreatesAnotherMessageReport() {
        UUID messageId = UUID.randomUUID();
        Message message = new Message();
        message.setId(messageId);
        User sender = new User();
        sender.setId(UUID.randomUUID());
        message.setSender(sender);
        Report existing = new Report();
        existing.setReason(ReportReason.SPAM);
        existing.setDescription("ilk şikâyet");
        when(messagingService.getMessageForReport(messageId, reporterId)).thenReturn(message);
        when(reportDao.findByReporterIdAndMessageId(reporterId, messageId)).thenReturn(Optional.of(existing));

        assertThrows(ConflictException.class,
                () -> manager.createReport(null, null, messageId, null,
                        ReportReason.FRAUD, "başka şikâyet"));
        verify(reportDao, never()).save(any());
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
        when(reportDao.findByIdForUpdate(report.getId())).thenReturn(Optional.of(report));
        when(securityService.getAuthenticatedUser()).thenReturn(new User());

        assertThrows(ConflictException.class,
                () -> manager.review(null, report.getId(), ReportStatus.DISMISSED, "yine"));
    }

    @Test
    void reviewingStampsTheAdminAndTheTime() {
        Report report = new Report();
        report.setId(UUID.randomUUID());
        report.setStatus(ReportStatus.OPEN);
        report.setReporter(reporter());
        when(reportDao.findByIdForUpdate(report.getId())).thenReturn(Optional.of(report));

        User admin = new User();
        admin.setId(UUID.randomUUID());
        when(securityService.getAuthenticatedUser()).thenReturn(admin);

        manager.review(null, report.getId(), ReportStatus.ACTIONED, "hesap askıya alındı");

        assertEquals(ReportStatus.ACTIONED, report.getStatus());
        assertEquals(admin.getId(), report.getReviewedBy().getId());
        assertNotNull(report.getReviewedAt());
    }

    @Test
    void aReportAboutAMissingUserIsRejected() {
        UUID ghost = UUID.randomUUID();
        UserService userService = mock(UserService.class);
        when(userService.getReference(any())).thenReturn(new User());
        when(userService.getById(ghost)).thenThrow(new ResourceNotFoundException(ReportMessages.NOT_FOUND));

        ReportManager withGhost = new ReportManager(reportDao, userService, mock(JobService.class),
                mock(MessagingService.class), securityService, mock(ImageService.class),
                mock(com.exskylab.koala.core.mappers.ReportMapper.class),
                staffAccountService, mock(com.exskylab.koala.business.abstracts.JobAssignmentService.class),
                new com.exskylab.koala.core.helpers.PageableSanitizer());

        assertThrows(ResourceNotFoundException.class,
                () -> withGhost.createReport(ghost, null, null, null, ReportReason.FRAUD, "yok"));
    }

    @Test
    void aStaffModeratorCanDecideWithoutBeingAMarketplaceUser() {
        Report report = new Report();
        report.setId(UUID.randomUUID());
        report.setStatus(ReportStatus.OPEN);
        report.setReporter(reporter());
        when(reportDao.findByIdForUpdate(report.getId())).thenReturn(Optional.of(report));
        when(securityService.getAuthenticatedUser())
                .thenThrow(new ResourceNotFoundException("{user.not.authenticated}"));

        StaffPrincipal moderator = new StaffPrincipal(UUID.randomUUID(), "moderator@iskoala.com",
                "Moderator", Set.of("MODERATOR"), Set.of(), false);

        manager.review(moderator, report.getId(), ReportStatus.ACTIONED, "Icerik kaldirildi.");

        assertEquals(ReportStatus.ACTIONED, report.getStatus());
        assertNull(report.getReviewedBy(),
                "Personel kimliginin pazaryeri kullanicisi yok; eski kopru alani bos kalir.");
        assertEquals(moderator.staffId(), report.getReviewedByStaff().getId(),
                "Personel karari staff_members yabanci anahtariyla damgalanmali.");
    }

    @Test
    void aBridgeAdminDecisionStillStampsTheMarketplaceUser() {
        Report report = new Report();
        report.setId(UUID.randomUUID());
        report.setStatus(ReportStatus.OPEN);
        report.setReporter(reporter());
        when(reportDao.findByIdForUpdate(report.getId())).thenReturn(Optional.of(report));

        User admin = new User();
        admin.setId(UUID.randomUUID());
        when(securityService.getAuthenticatedUser()).thenReturn(admin);

        manager.review(null, report.getId(), ReportStatus.ACTIONED, "Hesap askiya alindi.");

        assertEquals(admin.getId(), report.getReviewedBy().getId());
    }

    private static User reporter() {
        User reporter = new User();
        reporter.setId(UUID.randomUUID());
        return reporter;
    }
}
