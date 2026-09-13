package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.*;
import com.exskylab.koala.core.dtos.cancellation.response.CancellationPreviewDto;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.AssignmentParticipants;
import com.exskylab.koala.core.helpers.CancellationPolicy;
import com.exskylab.koala.core.properties.CancellationProperties;
import com.exskylab.koala.dataAccess.CancellationDao;
import com.exskylab.koala.entities.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class CancellationPreviewTest {

    private CancellationDao cancellationDao;
    private SecurityService securityService;
    private JobAssignmentService jobAssignmentService;
    private DisputeService disputeService;
    private CancellationManager manager;

    private static final ZoneId POLICY_ZONE = new CancellationProperties().getZoneId();

    private final User employer = user();
    private final User worker = user();

    @BeforeEach
    void setUp() {
        cancellationDao = mock(CancellationDao.class);
        securityService = mock(SecurityService.class);
        jobAssignmentService = mock(JobAssignmentService.class);
        disputeService = mock(DisputeService.class);

        manager = new CancellationManager(cancellationDao, jobAssignmentService, mock(UserService.class),
                securityService, new AssignmentParticipants(mock(CompanyContactService.class)),
                new CancellationPolicy(new CancellationProperties(), passthroughSettings()),
                mock(PayoutService.class), disputeService, mock(ActivityNotificationService.class),
                mock(com.exskylab.koala.business.abstracts.PaymentService.class),
                mock(org.springframework.context.ApplicationEventPublisher.class));
    }

    private static LocalDateTime startsInHours(long hours) {
        return LocalDateTime.now(POLICY_ZONE).plusHours(hours);
    }

    private static User user() {
        User u = new User();
        u.setId(UUID.randomUUID());
        return u;
    }

    private JobAssignment assignment(AssignmentStatus status, LocalDateTime start) {
        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setCreator(employer);
        job.setStartDateTime(start);

        JobAssignment a = new JobAssignment();
        a.setId(UUID.randomUUID());
        a.setJob(job);
        a.setWorker(worker);
        a.setStatus(status);
        a.setPaymentStatus(PaymentStatus.HELD_IN_ESCROW);
        a.setTotalCharged(new BigDecimal("1500.00"));

        when(jobAssignmentService.getDetailedById(a.getId())).thenReturn(a);
        return a;
    }

    @Test
    void aWorkerSeesNoFeeAndAFullRefund() {
        JobAssignment a = assignment(AssignmentStatus.CONFIRMED, startsInHours(2));
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());

        CancellationPreviewDto preview = manager.previewCancellation(a.getId());

        assertEquals("0.0000", preview.feeRate());
        assertEquals("1500.00", preview.refundAmount());
        assertTrue(preview.cancellableNow());
    }

    @Test
    void anEmployerInsideTheFreeWindowSeesNoFee() {
        JobAssignment a = assignment(AssignmentStatus.CONFIRMED, startsInHours(72));
        when(securityService.getAuthenticatedUserId()).thenReturn(employer.getId());

        assertEquals("0.0000", manager.previewCancellation(a.getId()).feeRate());
    }

    @Test
    void anEmployerOutsideTheFreeWindowSeesTheLateFee() {
        JobAssignment a = assignment(AssignmentStatus.CONFIRMED, startsInHours(2));
        when(securityService.getAuthenticatedUserId()).thenReturn(employer.getId());

        CancellationPreviewDto preview = manager.previewCancellation(a.getId());

        assertEquals("0.5000", preview.feeRate());
        assertEquals("750.00", preview.feeAmount());
        assertEquals("750.00", preview.refundAmount());
    }

    @Test
    void onceTheJobHasStartedNothingIsRefunded() {
        JobAssignment a = assignment(AssignmentStatus.IN_PROGRESS, startsInHours(-1));
        when(securityService.getAuthenticatedUserId()).thenReturn(employer.getId());

        CancellationPreviewDto preview = manager.previewCancellation(a.getId());

        assertEquals("0.00", preview.refundAmount());
        assertTrue(preview.noticeMinutes() <= 0);
    }

    @Test
    void anAlreadyCancelledAssignmentPreviewsAsNotCancellable() {
        JobAssignment a = assignment(AssignmentStatus.CONFIRMED, startsInHours(48));
        when(securityService.getAuthenticatedUserId()).thenReturn(employer.getId());
        when(cancellationDao.existsByJobAssignmentId(a.getId())).thenReturn(true);

        assertFalse(manager.previewCancellation(a.getId()).cancellableNow());
    }

    @Test
    void aCompletedAssignmentPreviewsAsNotCancellableInsteadOfThrowing() {
        JobAssignment a = assignment(AssignmentStatus.COMPLETED, startsInHours(-24));
        when(securityService.getAuthenticatedUserId()).thenReturn(employer.getId());

        assertFalse(manager.previewCancellation(a.getId()).cancellableNow());
    }

    @Test
    void anActiveDisputePreviewsAsNotCancellable() {
        JobAssignment a = assignment(AssignmentStatus.CONFIRMED, startsInHours(48));
        when(securityService.getAuthenticatedUserId()).thenReturn(employer.getId());
        when(disputeService.hasActiveDispute(a.getId())).thenReturn(true);

        assertFalse(manager.previewCancellation(a.getId()).cancellableNow());
    }

    @Test
    void previewingLeavesNoTrace() {
        JobAssignment a = assignment(AssignmentStatus.CONFIRMED, startsInHours(48));
        when(securityService.getAuthenticatedUserId()).thenReturn(employer.getId());

        manager.previewCancellation(a.getId());

        verify(cancellationDao, never()).save(any());
        verify(jobAssignmentService, never()).save(any());
        assertEquals(AssignmentStatus.CONFIRMED, a.getStatus());
    }

    @Test
    void someoneWhoIsNotAPartyGetsNotFound() {
        JobAssignment a = assignment(AssignmentStatus.CONFIRMED, startsInHours(48));
        when(securityService.getAuthenticatedUserId()).thenReturn(UUID.randomUUID());

        assertThrows(ResourceNotFoundException.class, () -> manager.previewCancellation(a.getId()));
    }

    private static SettingsService passthroughSettings() {
        SettingsService settings = mock(SettingsService.class);
        when(settings.intValue(anyString(), anyInt())).thenAnswer(i -> i.getArgument(1));
        when(settings.decimalValue(anyString(), any())).thenAnswer(i -> i.getArgument(1));
        return settings;
    }
}
