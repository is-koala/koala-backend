package com.exskylab.koala.business.concretes;

import com.exskylab.koala.support.FallbackSettings;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.business.abstracts.BadgeService;
import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.business.abstracts.PayoutService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.dtos.timesheet.request.CheckPointDto;
import com.exskylab.koala.core.helpers.AssignmentParticipants;
import com.exskylab.koala.core.helpers.CancellationPolicy;
import com.exskylab.koala.core.properties.CancellationProperties;
import com.exskylab.koala.dataAccess.*;
import com.exskylab.koala.entities.*;
import org.junit.jupiter.api.BeforeEach;
import com.exskylab.koala.business.abstracts.DisputeService;
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.business.abstracts.UserService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AssignmentOperationsAuthorizationTest {

    private CancellationDao cancellationDao;
    private DisputeDao disputeDaoStub;
    private WorkSessionDao workSessionDao;
    private JobAssignmentDao jobAssignmentDao;
    private JobAssignmentService jobAssignmentService;
    private DisputeService disputeService;
    private SecurityService securityService;
    private PayoutService payoutService;

    private JobAssignmentManager jobAssignmentManager;
    private BadgeService badgeService;
    private UserService userService;
    private CancellationManager cancellationManager;
    private DisputeManager disputeManager;
    private TimesheetManager timesheetManager;

    private final User employer = user();
    private final User worker = user();
    private final User stranger = user();

    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        cancellationDao = mock(CancellationDao.class);
        disputeDaoStub = mock(DisputeDao.class);
        disputeService = mock(DisputeService.class);
        workSessionDao = mock(WorkSessionDao.class);
        jobAssignmentDao = mock(JobAssignmentDao.class);
        securityService = mock(SecurityService.class);
        payoutService = mock(PayoutService.class);
        userService = mock(UserService.class);
        jobAssignmentService = mock(JobAssignmentService.class);
        disputeService = mock(DisputeService.class);

        AssignmentParticipants participants = new AssignmentParticipants(mock(CompanyContactService.class));
        badgeService = mock(BadgeService.class);
        CancellationPolicy policy = new CancellationPolicy(new CancellationProperties(), passthroughSettings());

        eventPublisher = mock(org.springframework.context.ApplicationEventPublisher.class);
        cancellationManager = new CancellationManager(cancellationDao, jobAssignmentService, userService,
                securityService, participants, policy, payoutService, disputeService,
                mock(com.exskylab.koala.business.abstracts.ActivityNotificationService.class),
                mock(com.exskylab.koala.business.abstracts.PaymentService.class),
                eventPublisher);
        disputeManager = new DisputeManager(disputeDaoStub,
                mock(com.exskylab.koala.dataAccess.DisputeEvidenceDao.class),
                mock(com.exskylab.koala.core.utilities.storage.StorageService.class),
                new com.exskylab.koala.core.helpers.UploadValidator(
                        new com.exskylab.koala.core.properties.UploadProperties()),
                new com.exskylab.koala.core.properties.UploadProperties(),
                jobAssignmentService, userService,
                securityService, participants, new StaffPayoutManager(payoutService),
                mock(com.exskylab.koala.business.abstracts.StaffAccountService.class),
                mock(com.exskylab.koala.business.abstracts.ActivityNotificationService.class),
                new com.exskylab.koala.core.helpers.PageableSanitizer(),
                mock(com.exskylab.koala.core.mappers.DisputeMapper.class));
        timesheetManager = new TimesheetManager(workSessionDao, jobAssignmentService, securityService, participants,
                new com.exskylab.koala.core.helpers.TimesheetEventPolicy(
                        new com.exskylab.koala.core.properties.TimesheetProperties()),
                java.time.Clock.systemUTC());
        com.exskylab.koala.core.mappers.JobAssignmentMapper jobAssignmentMapper =
                mock(com.exskylab.koala.core.mappers.JobAssignmentMapper.class);
        when(jobAssignmentMapper.toMyAssignmentDto(any(), any(), any(), any(), any())).thenAnswer(call -> {
            JobAssignment source = call.getArgument(0);
            return new com.exskylab.koala.core.dtos.jobAssignment.response.MyAssignmentDto(source.getId(),
                    new com.exskylab.koala.core.dtos.job.response.JobSummaryDto(), null, source.getStatus(),
                    source.getPaymentStatus(), null, source.getActualCompletionDateTime(), 0, null, null, null,
                    false, null, null, null, source.getConfirmedAt(), null, null, null, null, null, null, null,
                    false);
        });
        jobAssignmentManager = new JobAssignmentManager(jobAssignmentDao, securityService,
                jobAssignmentMapper,
                mock(com.exskylab.koala.core.utilities.payment.iyzico.IyzicoPaymentService.class),
                mock(CompanyContactService.class), mock(com.exskylab.koala.business.abstracts.UserService.class),
                new PricingManager(FallbackSettings.commissionCalculator()),
                mock(com.exskylab.koala.business.abstracts.PaymentService.class),
                new com.exskylab.koala.core.helpers.PaymentStateMachine(),
                new com.exskylab.koala.core.properties.AppProperties("http://localhost:8080", "http://localhost:3000"),
                mock(com.exskylab.koala.business.abstracts.BankAccountService.class),
                new com.exskylab.koala.core.helpers.PageableSanitizer(), participants, badgeService,
                payoutService, new com.exskylab.koala.core.properties.AutoCompletionProperties(),
                new com.exskylab.koala.core.properties.UnpaidExpiryProperties(), passthroughSettings(),
                mock(com.exskylab.koala.business.abstracts.GalleryService.class), mock(com.exskylab.koala.business.abstracts.ActivityNotificationService.class), mock(org.springframework.context.ApplicationEventPublisher.class),
                new com.exskylab.koala.core.monitoring.PaymentMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()),
                cancellationDao, mock(com.exskylab.koala.business.abstracts.JobService.class),
                mock(com.exskylab.koala.dataAccess.ReviewDao.class),
                new com.exskylab.koala.core.mappers.PricingDtoMapper());

        when(cancellationDao.save(any())).thenAnswer(i -> i.getArgument(0));
        when(disputeDaoStub.save(any())).thenAnswer(i -> i.getArgument(0));
        when(workSessionDao.save(any())).thenAnswer(i -> i.getArgument(0));
        when(workSessionDao.findAllByJobAssignmentIdOrderByCheckInAtAsc(any())).thenReturn(List.of());
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

    private JobAssignment assignment(AssignmentStatus status, PaymentStatus paymentStatus, LocalDateTime start) {
        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setCreator(employer);
        job.setStartDateTime(start);

        JobAssignment a = new JobAssignment();
        a.setId(UUID.randomUUID());
        a.setJob(job);
        a.setWorker(worker);
        a.setStatus(status);
        a.setPaymentStatus(paymentStatus);
        a.setTotalCharged(new BigDecimal("1075.00"));

        when(jobAssignmentDao.findWithDetailsForPaymentById(a.getId())).thenReturn(Optional.of(a));
        when(jobAssignmentService.getDetailedById(a.getId())).thenReturn(a);
        when(jobAssignmentService.getDetailedByIdForUpdate(a.getId())).thenReturn(a);
        when(jobAssignmentDao.findById(a.getId())).thenReturn(Optional.of(a));
        when(jobAssignmentDao.save(any())).thenAnswer(i -> i.getArgument(0));
        return a;
    }

    private LocalDateTime inHours(long hours) {
        return LocalDateTime.now(new CancellationProperties().getZoneId()).plusHours(hours);
    }

    @Test
    void aStrangerCannotCancelSomeoneElsesAssignment() {
        JobAssignment a = assignment(AssignmentStatus.CONFIRMED, PaymentStatus.HELD_IN_ESCROW, inHours(48));
        when(securityService.getAuthenticatedUserId()).thenReturn(stranger.getId());

        assertThrows(ResourceNotFoundException.class, () -> cancellationManager.cancelAssignment(a.getId(), "olmadı"));
        verify(cancellationDao, never()).save(any());
        assertEquals(AssignmentStatus.CONFIRMED, a.getStatus());
    }

    @Test
    void aCompletedAssignmentCannotBeCancelled() {
        JobAssignment a = assignment(AssignmentStatus.COMPLETED, PaymentStatus.HELD_IN_ESCROW, inHours(-4));
        when(securityService.getAuthenticatedUserId()).thenReturn(employer.getId());

        assertThrows(ConflictException.class, () -> cancellationManager.cancelAssignment(a.getId(), "vazgeçtim"));
    }

    @Test
    void anEmployerCancellingEarlyGetsAFullRefundAndTheAssignmentClosed() {
        JobAssignment a = assignment(AssignmentStatus.CONFIRMED, PaymentStatus.HELD_IN_ESCROW, inHours(48));
        when(securityService.getAuthenticatedUserId()).thenReturn(employer.getId());
        Refund refund = new Refund();
        refund.setId(UUID.randomUUID());
        when(payoutService.refundEmployer(eq(a.getId()), eq(new BigDecimal("1075.00")), any()))
                .thenReturn(refund);

        Cancellation cancellation = cancellationManager.cancelAssignment(a.getId(), "iş iptal");

        assertEquals(CancellationParty.EMPLOYER, cancellation.getCancelledByParty());
        assertEquals(0, cancellation.getFeeAmount().signum());
        assertSame(refund, cancellation.getRefund());
        assertEquals(AssignmentStatus.CANCELLED, a.getStatus());
        verify(payoutService).refundEmployer(eq(a.getId()), eq(new BigDecimal("1075.00")), any());
        verify(eventPublisher).publishEvent(new com.exskylab.koala.core.events.AssignmentClosedEvent(
                a.getJob().getId(), a.getWorker().getId(), a.getId()));
    }

    @Test
    void aLateEmployerCancellationRefundsOnlyThePartLeftAfterTheFee() {
        JobAssignment a = assignment(AssignmentStatus.CONFIRMED, PaymentStatus.HELD_IN_ESCROW, inHours(2));
        when(securityService.getAuthenticatedUserId()).thenReturn(employer.getId());

        Cancellation cancellation = cancellationManager.cancelAssignment(a.getId(), "geç kaldım");

        assertEquals(new BigDecimal("537.50"), cancellation.getFeeAmount());
        verify(payoutService).refundEmployer(eq(a.getId()), eq(new BigDecimal("537.50")), any());
    }

    @Test
    void whenNoMoneyWasCollectedNoRefundIsAttempted() {
        JobAssignment a = assignment(AssignmentStatus.PENDING_CONFIRMATION, PaymentStatus.PENDING_COLLECTION, inHours(6));
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());

        Cancellation cancellation = cancellationManager.cancelAssignment(a.getId(), "gelemeyeceğim");

        assertEquals(CancellationParty.WORKER, cancellation.getCancelledByParty());
        assertEquals(0, cancellation.getRefundAmount().signum());
        verify(payoutService, never()).refundEmployer(any(), any(), any());
        verify(eventPublisher).publishEvent(new com.exskylab.koala.core.events.AssignmentClosedEvent(
                a.getJob().getId(), a.getWorker().getId(), a.getId()));
    }

    @Test
    void anAssignmentCannotBeCancelledTwice() {
        JobAssignment a = assignment(AssignmentStatus.CONFIRMED, PaymentStatus.HELD_IN_ESCROW, inHours(48));
        when(securityService.getAuthenticatedUserId()).thenReturn(employer.getId());
        when(cancellationDao.existsByJobAssignmentId(a.getId())).thenReturn(true);

        assertThrows(ConflictException.class, () -> cancellationManager.cancelAssignment(a.getId(), "yine"));
    }

    @Test
    void anActiveDisputeFreezesCancellationBeforeAnyStateOrMoneyMoves() {
        JobAssignment a = assignment(AssignmentStatus.CONFIRMED, PaymentStatus.HELD_IN_ESCROW, inHours(48));
        when(securityService.getAuthenticatedUserId()).thenReturn(employer.getId());
        when(disputeService.hasActiveDispute(a.getId())).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> cancellationManager.cancelAssignment(a.getId(), "uyuşmazlık sürerken iptal"));

        assertEquals(AssignmentStatus.CONFIRMED, a.getStatus());
        verify(cancellationDao, never()).save(any());
        verify(payoutService, never()).refundEmployer(any(), any(), any());
        verify(jobAssignmentService).getDetailedByIdForUpdate(a.getId());
    }

    @Test
    void aStrangerCannotOpenADispute() {
        JobAssignment a = assignment(AssignmentStatus.COMPLETED, PaymentStatus.HELD_IN_ESCROW, inHours(-4));
        when(securityService.getAuthenticatedUserId()).thenReturn(stranger.getId());

        assertThrows(ResourceNotFoundException.class, () -> disputeManager.openDispute(a.getId(), "olmadı"));
    }

    @Test
    void aDisputeNeedsMoneyInEscrow() {
        JobAssignment a = assignment(AssignmentStatus.COMPLETED, PaymentStatus.PENDING_COLLECTION, inHours(-4));
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());

        assertThrows(ConflictException.class, () -> disputeManager.openDispute(a.getId(), "para gelmedi"));
    }

    @Test
    void onlyOneDisputeStaysOpenPerAssignment() {
        JobAssignment a = assignment(AssignmentStatus.COMPLETED, PaymentStatus.HELD_IN_ESCROW, inHours(-4));
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());
        when(disputeDaoStub.existsByJobAssignmentIdAndStatusIn(a.getId(), DisputeManager.ACTIVE)).thenReturn(true);

        assertThrows(ConflictException.class, () -> disputeManager.openDispute(a.getId(), "tekrar"));
        verify(jobAssignmentService).getDetailedByIdForUpdate(a.getId());
    }

    @Test
    void resolvingInFavourOfTheWorkerReleasesTheEscrow() {
        JobAssignment a = assignment(AssignmentStatus.COMPLETED, PaymentStatus.HELD_IN_ESCROW, inHours(-4));
        Dispute dispute = openDispute(a);
        when(securityService.getAuthenticatedUser()).thenReturn(user());

        disputeManager.resolveDispute(financeTrustLead(), dispute.getId(),
                DisputeResolution.RELEASE_TO_WORKER, "işçi haklı");

        assertEquals(DisputeStatus.RESOLVED, dispute.getStatus());
        verify(payoutService).releaseToWorker(a.getId(), PayoutReleaseOrigin.DISPUTE);
        verify(payoutService, never()).refundEmployer(any(), any(), any());
    }

    @Test
    void aDismissedDisputeMovesNoMoney() {
        JobAssignment a = assignment(AssignmentStatus.COMPLETED, PaymentStatus.HELD_IN_ESCROW, inHours(-4));
        Dispute dispute = openDispute(a);
        when(securityService.getAuthenticatedUser()).thenReturn(user());

        disputeManager.resolveDispute(null, dispute.getId(), DisputeResolution.DISMISSED, "dayanaksız");

        verify(payoutService, never()).releaseToWorker(any(), any());
        verify(payoutService, never()).refundEmployer(any(), any(), any());
    }

    @Test
    void aResolvedDisputeCannotBeResolvedAgain() {
        JobAssignment a = assignment(AssignmentStatus.COMPLETED, PaymentStatus.HELD_IN_ESCROW, inHours(-4));
        Dispute dispute = openDispute(a);
        dispute.setStatus(DisputeStatus.RESOLVED);
        when(securityService.getAuthenticatedUser()).thenReturn(user());

        assertThrows(ConflictException.class,
                () -> disputeManager.resolveDispute(financeTrustLead(), dispute.getId(),
                        DisputeResolution.REFUND_EMPLOYER, "yine"));
        verify(payoutService, never()).refundEmployer(any(), any(), any());
    }

    private static com.exskylab.koala.core.security.StaffPrincipal financeTrustLead() {
        return new com.exskylab.koala.core.security.StaffPrincipal(
                java.util.UUID.randomUUID(), "trust@iskoala.com", "Güven Lideri",
                java.util.Set.of("TRUST_LEAD"),
                java.util.Set.of(com.exskylab.koala.entities.StaffPermission.DISPUTE_RESOLVE,
                        com.exskylab.koala.entities.StaffPermission.PAYOUT_RELEASE,
                        com.exskylab.koala.entities.StaffPermission.PAYMENT_REFUND),
                false);
    }

    private Dispute openDispute(JobAssignment a) {
        Dispute dispute = new Dispute();
        dispute.setId(UUID.randomUUID());
        dispute.setJobAssignment(a);
        dispute.setStatus(DisputeStatus.OPEN);
        when(disputeDaoStub.findById(dispute.getId())).thenReturn(Optional.of(dispute));
        when(disputeDaoStub.findJobAssignmentIdById(dispute.getId())).thenReturn(Optional.of(a.getId()));
        when(disputeDaoStub.findByIdForUpdate(dispute.getId())).thenReturn(Optional.of(dispute));
        return dispute;
    }

    @Test
    void onlyTheWorkerCanCheckIn() {
        JobAssignment a = assignment(AssignmentStatus.CONFIRMED, PaymentStatus.HELD_IN_ESCROW, inHours(1));
        when(securityService.getAuthenticatedUserId()).thenReturn(employer.getId());

        assertThrows(ResourceNotFoundException.class,
                () -> timesheetManager.checkIn(a.getId(), new CheckPointDto()));
        verify(workSessionDao, never()).save(any());
    }

    @Test
    void checkingInMovesTheAssignmentToInProgress() {
        JobAssignment a = assignment(AssignmentStatus.CONFIRMED, PaymentStatus.HELD_IN_ESCROW, inHours(1));
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());
        when(workSessionDao.findByJobAssignmentIdAndCheckOutAtIsNull(a.getId())).thenReturn(Optional.empty());

        WorkSession session = timesheetManager.checkIn(a.getId(), new CheckPointDto(
                new BigDecimal("41.015137"), new BigDecimal("28.979530"), null, null));

        assertNotNull(session.getCheckInAt());
        assertNull(session.getCheckOutAt());
        assertEquals(AssignmentStatus.IN_PROGRESS, a.getStatus());
    }

    @Test
    void workCannotStartBeforeTheEmployerHasPaid() {
        JobAssignment a = assignment(AssignmentStatus.CONFIRMED, PaymentStatus.PENDING_COLLECTION, inHours(1));
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());
        when(workSessionDao.findByJobAssignmentIdAndCheckOutAtIsNull(a.getId())).thenReturn(Optional.empty());

        assertThrows(ConflictException.class, () -> timesheetManager.checkIn(a.getId(), new CheckPointDto()));
        assertEquals(AssignmentStatus.CONFIRMED, a.getStatus());
    }

    @Test
    void anUnpaidAssignmentCannotBeMarkedComplete() {
        JobAssignment a = assignment(AssignmentStatus.CONFIRMED, PaymentStatus.PENDING_COLLECTION, inHours(-2));
        when(securityService.getAuthenticatedUserId()).thenReturn(employer.getId());

        assertThrows(ConflictException.class, () -> jobAssignmentManager.completeAssignment(a.getId()));
        assertNotEquals(AssignmentStatus.COMPLETED, a.getStatus());
    }

    @Test
    void aSecondCheckInIsRefusedWhileOneIsStillOpen() {
        JobAssignment a = assignment(AssignmentStatus.IN_PROGRESS, PaymentStatus.HELD_IN_ESCROW, inHours(1));
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());
        when(workSessionDao.findByJobAssignmentIdAndCheckOutAtIsNull(a.getId()))
                .thenReturn(Optional.of(new WorkSession()));

        assertThrows(ConflictException.class, () -> timesheetManager.checkIn(a.getId(), new CheckPointDto()));
    }

    @Test
    void checkingOutWithoutCheckingInIsRefused() {
        JobAssignment a = assignment(AssignmentStatus.IN_PROGRESS, PaymentStatus.HELD_IN_ESCROW, inHours(1));
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());
        when(workSessionDao.findByJobAssignmentIdAndCheckOutAtIsNull(a.getId())).thenReturn(Optional.empty());

        assertThrows(ConflictException.class, () -> timesheetManager.checkOut(a.getId(), new CheckPointDto()));
    }

    @Test
    void checkingOutRecordsTheMinutesAndRoundsThemIntoWorkHours() {
        JobAssignment a = assignment(AssignmentStatus.IN_PROGRESS, PaymentStatus.HELD_IN_ESCROW, inHours(1));
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());

        WorkSession open = new WorkSession();
        open.setId(UUID.randomUUID());
        open.setJobAssignment(a);
        open.setCheckInAt(java.time.Instant.now().minus(Duration.ofMinutes(200)));
        when(workSessionDao.findByJobAssignmentIdAndCheckOutAtIsNull(a.getId())).thenReturn(Optional.of(open));
        when(workSessionDao.findAllByJobAssignmentIdOrderByCheckInAtAsc(a.getId())).thenReturn(List.of(open));

        WorkSession closed = timesheetManager.checkOut(a.getId(), new CheckPointDto());

        assertEquals(200, closed.getMinutes());
        assertEquals(3, a.getWorkHours());
    }

    @Test
    void aStrangerCannotReadTheTimesheet() {
        JobAssignment a = assignment(AssignmentStatus.IN_PROGRESS, PaymentStatus.HELD_IN_ESCROW, inHours(1));
        when(securityService.getAuthenticatedUserId()).thenReturn(stranger.getId());

        assertThrows(ResourceNotFoundException.class, () -> timesheetManager.getSessions(a.getId()));
    }

    @Test
    void onlyTheEmployerCanMarkTheWorkComplete() {
        JobAssignment a = assignment(AssignmentStatus.IN_PROGRESS, PaymentStatus.HELD_IN_ESCROW, inHours(-2));
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());

        assertThrows(ResourceNotFoundException.class, () -> jobAssignmentManager.completeAssignment(a.getId()));
        assertEquals(AssignmentStatus.IN_PROGRESS, a.getStatus());
    }

    @Test
    void completingStampsTheTimeAndReEvaluatesTheWorkersBadges() {
        JobAssignment a = assignment(AssignmentStatus.IN_PROGRESS, PaymentStatus.HELD_IN_ESCROW, inHours(-2));
        when(securityService.getAuthenticatedUserId()).thenReturn(employer.getId());

        jobAssignmentManager.completeAssignment(a.getId());

        assertEquals(AssignmentStatus.COMPLETED, a.getStatus());
        assertNotNull(a.getActualCompletionDateTime());
        verify(badgeService).evaluate(worker.getId());
    }

    @Test
    void completingByTheEmployerReleasesTheEscrowThroughTheDisputeGate() {
        JobAssignment a = assignment(AssignmentStatus.IN_PROGRESS, PaymentStatus.HELD_IN_ESCROW, inHours(-2));
        when(securityService.getAuthenticatedUserId()).thenReturn(employer.getId());

        jobAssignmentManager.completeAssignment(a.getId());

        var order = inOrder(jobAssignmentDao, payoutService);
        order.verify(jobAssignmentDao).save(a);
        order.verify(payoutService).releaseToWorkerUnlessDisputed(a.getId(), PayoutReleaseOrigin.EMPLOYER_COMPLETION);
        verify(payoutService, never()).releaseToWorker(any(), any());
    }

    @Test
    void acceptingTheAssignmentStampsConfirmedAtAndAnswersWithTheTimelineDto() {
        JobAssignment a = assignment(AssignmentStatus.PENDING_CONFIRMATION, null, inHours(24));
        when(securityService.getAuthenticatedUser()).thenReturn(worker);
        var answer = new com.exskylab.koala.core.dtos.jobAssignment.request.AnswerAssignmentDto();
        answer.setAccepted(true);

        var dto = jobAssignmentManager.answerToAssignment(a.getId(), answer);

        assertEquals(AssignmentStatus.CONFIRMED, a.getStatus());
        assertNotNull(a.getConfirmedAt());
        assertEquals(a.getId(), dto.id());
    }

    @Test
    void rejectingTheAssignmentLeavesConfirmedAtEmpty() {
        JobAssignment a = assignment(AssignmentStatus.PENDING_CONFIRMATION, null, inHours(24));
        when(securityService.getAuthenticatedUser()).thenReturn(worker);
        var answer = new com.exskylab.koala.core.dtos.jobAssignment.request.AnswerAssignmentDto();
        answer.setAccepted(false);

        var dto = jobAssignmentManager.answerToAssignment(a.getId(), answer);

        assertEquals(AssignmentStatus.REJECTED, a.getStatus());
        assertNull(a.getConfirmedAt());
        assertNull(dto.confirmedAt());
    }

    @Test
    void aCancelledAssignmentCannotBeCompleted() {
        JobAssignment a = assignment(AssignmentStatus.CANCELLED, PaymentStatus.REFUNDED, inHours(-2));
        when(securityService.getAuthenticatedUserId()).thenReturn(employer.getId());

        assertThrows(ConflictException.class, () -> jobAssignmentManager.completeAssignment(a.getId()));
        verify(badgeService, never()).evaluate(any());
    }

    private static com.exskylab.koala.business.abstracts.SettingsService passthroughSettings() {
        com.exskylab.koala.business.abstracts.SettingsService settings =
                org.mockito.Mockito.mock(com.exskylab.koala.business.abstracts.SettingsService.class);
        org.mockito.Mockito.when(settings.intValue(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyInt())).thenAnswer(i -> i.getArgument(1));
        org.mockito.Mockito.when(settings.decimalValue(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenAnswer(i -> i.getArgument(1));
        return settings;
    }
}
