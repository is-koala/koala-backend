package com.exskylab.koala.business.concretes;

import com.exskylab.koala.support.FallbackSettings;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.business.abstracts.*;
import com.exskylab.koala.core.helpers.*;
import com.exskylab.koala.core.mappers.JobAssignmentMapper;
import com.exskylab.koala.core.properties.AppProperties;
import com.exskylab.koala.core.properties.AutoCompletionProperties;
import com.exskylab.koala.core.properties.CommissionProperties;
import com.exskylab.koala.core.schedulers.AssignmentAutoCompletionScheduler;
import com.exskylab.koala.core.utilities.payment.iyzico.IyzicoPaymentService;
import com.exskylab.koala.dataAccess.JobAssignmentDao;
import com.exskylab.koala.entities.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AutoCompletionRulesTest {

    private JobAssignmentDao jobAssignmentDao;
    private DisputeService disputeService;
    private PayoutService payoutService;
    private BadgeService badgeService;
    private AutoCompletionProperties properties;
    private JobAssignmentManager manager;

    private final User employer = user();
    private final User worker = user();

    @BeforeEach
    void setUp() {
        jobAssignmentDao = mock(JobAssignmentDao.class);
        disputeService = mock(DisputeService.class);
        payoutService = mock(PayoutService.class);
        badgeService = mock(BadgeService.class);
        properties = new AutoCompletionProperties();

        CompanyContactService contacts = mock(CompanyContactService.class);

        manager = new JobAssignmentManager(jobAssignmentDao, mock(SecurityService.class),
                mock(JobAssignmentMapper.class), mock(IyzicoPaymentService.class), contacts,
                mock(UserService.class), new PricingManager(FallbackSettings.commissionCalculator()),
                mock(PaymentService.class), new PaymentStateMachine(),
                new AppProperties("http://localhost:8080", "http://localhost:3000"),
                mock(BankAccountService.class), new PageableSanitizer(),
                new AssignmentParticipants(contacts), badgeService,
                payoutService, properties, new com.exskylab.koala.core.properties.UnpaidExpiryProperties(), passthroughSettings(), mock(com.exskylab.koala.business.abstracts.GalleryService.class),
                mock(com.exskylab.koala.business.abstracts.ActivityNotificationService.class), mock(org.springframework.context.ApplicationEventPublisher.class));

        when(jobAssignmentDao.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static User user() {
        User u = new User();
        u.setId(UUID.randomUUID());
        return u;
    }

    private JobAssignment assignment(AssignmentStatus status, PaymentStatus paymentStatus) {
        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setCreator(employer);
        job.setEndDateTime(LocalDateTime.now().minusDays(10));

        JobAssignment a = new JobAssignment();
        a.setId(UUID.randomUUID());
        a.setJob(job);
        a.setWorker(worker);
        a.setStatus(status);
        a.setPaymentStatus(paymentStatus);

        when(jobAssignmentDao.findWithDetailsForPaymentById(a.getId())).thenReturn(Optional.of(a));
        return a;
    }

    @Test
    void anOverdueAssignmentIsCompletedAndTheEscrowIsReleased() {
        JobAssignment a = assignment(AssignmentStatus.IN_PROGRESS, PaymentStatus.HELD_IN_ESCROW);

        assertTrue(manager.autoCompleteAssignment(a.getId()));

        assertEquals(AssignmentStatus.COMPLETED, a.getStatus());
        assertNotNull(a.getActualCompletionDateTime());
        verify(payoutService).releaseToWorker(a.getId());
        verify(badgeService).evaluate(worker.getId());
    }

    @Test
    void anOpenDisputeStopsAutoCompletionAtTheScheduler() {
        JobAssignmentService service = mock(JobAssignmentService.class);
        UUID disputed = UUID.randomUUID();
        when(service.findAutoCompletableAssignmentIds()).thenReturn(List.of(disputed));
        when(disputeService.hasActiveDispute(disputed)).thenReturn(true);

        new AssignmentAutoCompletionScheduler(service, disputeService, properties).completeOverdueAssignments();

        verify(service, never()).autoCompleteAssignment(any());
    }

    @Test
    void anAssignmentWithoutMoneyInEscrowIsNotAutoCompleted() {
        JobAssignment a = assignment(AssignmentStatus.CONFIRMED, PaymentStatus.PENDING_COLLECTION);

        assertFalse(manager.autoCompleteAssignment(a.getId()));
        verify(payoutService, never()).releaseToWorker(any());
    }

    @Test
    void anAlreadyCompletedAssignmentIsSkippedSoASecondRunIsHarmless() {
        JobAssignment a = assignment(AssignmentStatus.COMPLETED, PaymentStatus.RELEASED_TO_WORKER);

        assertFalse(manager.autoCompleteAssignment(a.getId()));
        verify(payoutService, never()).releaseToWorker(any());
    }

    @Test
    void aCancelledAssignmentIsNeverAutoCompleted() {
        JobAssignment a = assignment(AssignmentStatus.CANCELLED, PaymentStatus.HELD_IN_ESCROW);

        assertFalse(manager.autoCompleteAssignment(a.getId()));
    }

    @Test
    void theGracePeriodIsMeasuredFromTheJobsEndInItsOwnZone() {
        properties.setGraceDays(3);
        manager.findAutoCompletableAssignmentIds();

        verify(jobAssignmentDao).findAutoCompletableIds(
                eq(List.of(AssignmentStatus.CONFIRMED, AssignmentStatus.IN_PROGRESS)),
                eq(PaymentStatus.HELD_IN_ESCROW),
                argThat(endedBefore -> {
                    LocalDateTime expected = LocalDateTime.now(properties.getZoneId()).minusDays(3);
                    return Math.abs(java.time.Duration.between(endedBefore, expected).toSeconds()) < 5;
                }),
                any());
    }

    @Test
    void oneFailingAssignmentDoesNotStopTheRest() {
        JobAssignmentService failing = mock(JobAssignmentService.class);
        UUID bad = UUID.randomUUID();
        UUID good = UUID.randomUUID();

        when(failing.findAutoCompletableAssignmentIds()).thenReturn(List.of(bad, good));
        when(failing.autoCompleteAssignment(bad)).thenThrow(new ConflictException("boom"));
        when(failing.autoCompleteAssignment(good)).thenReturn(true);

        new AssignmentAutoCompletionScheduler(failing, disputeService, properties).completeOverdueAssignments();

        verify(failing).autoCompleteAssignment(good);
    }

    @Test
    void theSchedulerDoesNothingWhenTheFeatureIsOff() {
        JobAssignmentService service = mock(JobAssignmentService.class);
        properties.setEnabled(false);

        new AssignmentAutoCompletionScheduler(service, disputeService, properties).completeOverdueAssignments();

        verify(service, never()).findAutoCompletableAssignmentIds();
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
