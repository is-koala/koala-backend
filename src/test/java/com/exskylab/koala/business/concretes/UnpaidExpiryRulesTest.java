package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.ActivityNotificationService;
import com.exskylab.koala.business.abstracts.CancellationService;
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.business.abstracts.PayoutService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.helpers.AssignmentParticipants;
import com.exskylab.koala.core.helpers.CancellationPolicy;
import com.exskylab.koala.core.properties.CancellationProperties;
import com.exskylab.koala.core.properties.UnpaidExpiryProperties;
import com.exskylab.koala.core.schedulers.UnpaidAssignmentExpiryScheduler;
import com.exskylab.koala.dataAccess.CancellationDao;
import com.exskylab.koala.entities.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UnpaidExpiryRulesTest {

    private CancellationDao cancellationDao;
    private JobAssignmentService jobAssignmentService;
    private ActivityNotificationService notifications;
    private CancellationManager manager;

    private final User employer = user();
    private final User worker = user();
    private final User system = user();

    @BeforeEach
    void setUp() {
        cancellationDao = mock(CancellationDao.class);
        jobAssignmentService = mock(JobAssignmentService.class);
        notifications = mock(ActivityNotificationService.class);

        SecurityService security = mock(SecurityService.class);
        when(security.getSystemUser()).thenReturn(system);

        manager = new CancellationManager(cancellationDao, jobAssignmentService, mock(UserService.class),
                security, new AssignmentParticipants(mock(com.exskylab.koala.business.abstracts.CompanyContactService.class)),
                new CancellationPolicy(new CancellationProperties(), passthroughSettings()),
                mock(PayoutService.class), notifications);

        when(cancellationDao.save(any())).thenAnswer(i -> i.getArgument(0));
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
        job.setTitle("Garson");
        job.setStartDateTime(LocalDateTime.now().plusHours(2));

        JobAssignment a = new JobAssignment();
        a.setId(UUID.randomUUID());
        a.setJob(job);
        a.setWorker(worker);
        a.setStatus(status);
        a.setPaymentStatus(paymentStatus);

        when(jobAssignmentService.getDetailedById(a.getId())).thenReturn(a);
        return a;
    }

    @Test
    void anUnpaidAssignmentIsCancelledWhenTheJobIsAboutToStart() {
        JobAssignment a = assignment(AssignmentStatus.CONFIRMED, PaymentStatus.PENDING_COLLECTION);

        assertTrue(manager.expireUnpaidAssignment(a.getId()));
        assertEquals(AssignmentStatus.CANCELLED, a.getStatus());
        verify(cancellationDao).save(any(Cancellation.class));
    }

    @Test
    void aFailedPaymentAttemptAlsoExpires() {
        JobAssignment a = assignment(AssignmentStatus.CONFIRMED, PaymentStatus.FAILED);

        assertTrue(manager.expireUnpaidAssignment(a.getId()));
        assertEquals(AssignmentStatus.CANCELLED, a.getStatus());
    }

    @Test
    void anAssignmentWithMoneyInEscrowIsLeftAlone() {
        JobAssignment a = assignment(AssignmentStatus.CONFIRMED, PaymentStatus.HELD_IN_ESCROW);

        assertFalse(manager.expireUnpaidAssignment(a.getId()));
        assertEquals(AssignmentStatus.CONFIRMED, a.getStatus());
        verify(cancellationDao, never()).save(any());
    }

    @Test
    void anOfferTheWorkerHasNotAcceptedYetIsLeftAlone() {
        JobAssignment a = assignment(AssignmentStatus.PENDING_CONFIRMATION, PaymentStatus.PENDING_COLLECTION);

        assertFalse(manager.expireUnpaidAssignment(a.getId()));
        assertEquals(AssignmentStatus.PENDING_CONFIRMATION, a.getStatus());
    }

    @Test
    void theExpiryIsRecordedAgainstTheEmployerWithNoMoneyMoving() {
        JobAssignment a = assignment(AssignmentStatus.CONFIRMED, PaymentStatus.PENDING_COLLECTION);

        manager.expireUnpaidAssignment(a.getId());

        verify(cancellationDao).save(argThat(cancellation -> {
            assertEquals(CancellationParty.EMPLOYER, cancellation.getCancelledByParty());
            assertEquals(system.getId(), cancellation.getCancelledBy().getId());
            assertEquals(0, cancellation.getFeeAmount().signum());
            assertEquals(0, cancellation.getRefundAmount().signum());
            return true;
        }));
    }

    @Test
    void bothSidesAreToldTheAssignmentExpired() {
        JobAssignment a = assignment(AssignmentStatus.CONFIRMED, PaymentStatus.PENDING_COLLECTION);

        manager.expireUnpaidAssignment(a.getId());

        verify(notifications).assignmentExpired(any(Cancellation.class));
    }

    @Test
    void aSecondRunOverTheSameAssignmentDoesNothing() {
        JobAssignment a = assignment(AssignmentStatus.CONFIRMED, PaymentStatus.PENDING_COLLECTION);
        when(cancellationDao.existsByJobAssignmentId(a.getId())).thenReturn(true);

        assertFalse(manager.expireUnpaidAssignment(a.getId()));
        verify(cancellationDao, never()).save(any());
    }

    @Test
    void oneFailingAssignmentDoesNotStopTheBatch() {
        UUID failing = UUID.randomUUID();
        UUID healthy = UUID.randomUUID();

        CancellationService failingManager = mock(CancellationService.class);
        when(failingManager.expireUnpaidAssignment(failing)).thenThrow(new IllegalStateException("boom"));
        when(failingManager.expireUnpaidAssignment(healthy)).thenReturn(true);

        JobAssignmentService assignments = mock(JobAssignmentService.class);
        when(assignments.findUnpaidExpiringAssignmentIds()).thenReturn(List.of(failing, healthy));

        new UnpaidAssignmentExpiryScheduler(assignments, failingManager, new UnpaidExpiryProperties())
                .expireUnpaidAssignments();

        verify(failingManager).expireUnpaidAssignment(healthy);
    }

    @Test
    void nothingRunsWhileTheFeatureIsOff() {
        JobAssignmentService assignments = mock(JobAssignmentService.class);
        CancellationService cancellations = mock(CancellationService.class);

        UnpaidExpiryProperties off = new UnpaidExpiryProperties();
        off.setEnabled(false);

        new UnpaidAssignmentExpiryScheduler(assignments, cancellations, off).expireUnpaidAssignments();

        verifyNoInteractions(assignments, cancellations);
    }

    private static com.exskylab.koala.business.abstracts.SettingsService passthroughSettings() {
        com.exskylab.koala.business.abstracts.SettingsService settings =
                mock(com.exskylab.koala.business.abstracts.SettingsService.class);
        when(settings.intValue(anyString(), anyInt())).thenAnswer(i -> i.getArgument(1));
        when(settings.decimalValue(anyString(), any())).thenAnswer(i -> i.getArgument(1));
        return settings;
    }
}
