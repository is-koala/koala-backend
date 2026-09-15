package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.BankAccountService;
import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.business.abstracts.PaymentService;
import com.exskylab.koala.business.abstracts.PayoutService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.business.abstracts.BadgeService;
import com.exskylab.koala.core.helpers.AssignmentParticipants;
import com.exskylab.koala.core.helpers.MessagingAvailability;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.core.helpers.PaymentStateMachine;
import com.exskylab.koala.core.mappers.JobAssignmentMapper;
import com.exskylab.koala.core.properties.AppProperties;
import com.exskylab.koala.core.properties.AutoCompletionProperties;
import com.exskylab.koala.core.properties.MessagingProperties;
import com.exskylab.koala.core.utilities.payment.iyzico.IyzicoPaymentService;
import com.exskylab.koala.dataAccess.JobAssignmentDao;
import com.exskylab.koala.entities.AssignmentStatus;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.MessagingAvailabilityReason;
import com.exskylab.koala.entities.PaymentStatus;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.support.FallbackSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LatestAssignmentLookupTest {

    private JobAssignmentDao jobAssignmentDao;
    private JobAssignmentManager manager;
    private final MessagingAvailability availability = new MessagingAvailability(new MessagingProperties());

    private final Job job = job();
    private final User worker = user();

    @BeforeEach
    void setUp() {
        jobAssignmentDao = mock(JobAssignmentDao.class);
        CompanyContactService contacts = mock(CompanyContactService.class);

        manager = new JobAssignmentManager(jobAssignmentDao, mock(SecurityService.class),
                mock(JobAssignmentMapper.class), mock(IyzicoPaymentService.class), contacts,
                mock(UserService.class), new PricingManager(FallbackSettings.commissionCalculator()),
                mock(PaymentService.class), new PaymentStateMachine(),
                new AppProperties("http://localhost:8080", "http://localhost:3000"),
                mock(BankAccountService.class), new PageableSanitizer(),
                new AssignmentParticipants(contacts), mock(BadgeService.class),
                mock(PayoutService.class), new AutoCompletionProperties(),
                new com.exskylab.koala.core.properties.UnpaidExpiryProperties(),
                mock(com.exskylab.koala.business.abstracts.SettingsService.class),
                mock(com.exskylab.koala.business.abstracts.GalleryService.class),
                mock(com.exskylab.koala.business.abstracts.ActivityNotificationService.class),
                mock(org.springframework.context.ApplicationEventPublisher.class),
                new com.exskylab.koala.core.monitoring.PaymentMetrics(
                        new io.micrometer.core.instrument.simple.SimpleMeterRegistry()),
                mock(com.exskylab.koala.dataAccess.CancellationDao.class),
                mock(com.exskylab.koala.business.abstracts.JobService.class),
                mock(com.exskylab.koala.dataAccess.ReviewDao.class),
                mock(com.exskylab.koala.business.abstracts.TransportService.class),
                new com.exskylab.koala.core.helpers.JobEditors(contacts));
    }

    private static Job job() {
        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setEndDateTime(LocalDateTime.now().plusDays(2));
        return job;
    }

    private static User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        return user;
    }

    private JobAssignment assignment(AssignmentStatus status, PaymentStatus paymentStatus, Instant createdAt) {
        JobAssignment assignment = new JobAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setJob(job);
        assignment.setWorker(worker);
        assignment.setStatus(status);
        assignment.setPaymentStatus(paymentStatus);
        assignment.setCreatedAt(createdAt);
        return assignment;
    }

    @Test
    void aRejectedOfferFollowedByAPaidAssignmentLeavesMessagingAvailable() {
        JobAssignment rejected = assignment(AssignmentStatus.REJECTED, null, Instant.now().minusSeconds(3600));
        JobAssignment paid = assignment(AssignmentStatus.CONFIRMED, PaymentStatus.HELD_IN_ESCROW, Instant.now());
        when(jobAssignmentDao.findAllByJobIdAndWorkerId(job.getId(), worker.getId()))
                .thenReturn(List.of(paid, rejected));

        JobAssignment latest = manager.findLatestByJobAndWorker(job.getId(), worker.getId()).orElseThrow();

        assertSame(paid, latest);
        assertEquals(MessagingAvailabilityReason.AVAILABLE, availability.reasonOf(latest));
    }

    @Test
    void aPaidAssignmentFollowedByARejectedOfferClosesMessaging() {
        JobAssignment paid = assignment(AssignmentStatus.CONFIRMED, PaymentStatus.HELD_IN_ESCROW,
                Instant.now().minusSeconds(3600));
        JobAssignment rejected = assignment(AssignmentStatus.REJECTED, null, Instant.now());
        when(jobAssignmentDao.findAllByJobIdAndWorkerId(job.getId(), worker.getId()))
                .thenReturn(List.of(paid, rejected));

        JobAssignment latest = manager.findLatestByJobAndWorker(job.getId(), worker.getId()).orElseThrow();

        assertSame(rejected, latest);
        assertEquals(MessagingAvailabilityReason.CLOSED, availability.reasonOf(latest));
    }

    @Test
    void theBatchLookupPicksTheSameLatestRowPerPairAsTheSingleLookup() {
        JobAssignment rejected = assignment(AssignmentStatus.REJECTED, null, Instant.now().minusSeconds(3600));
        JobAssignment paid = assignment(AssignmentStatus.CONFIRMED, PaymentStatus.HELD_IN_ESCROW, Instant.now());
        when(jobAssignmentDao.findAllByJobIdInAndWorkerIdIn(any(), any())).thenReturn(List.of(rejected, paid));
        when(jobAssignmentDao.findAllByJobIdAndWorkerId(job.getId(), worker.getId()))
                .thenReturn(List.of(rejected, paid));

        List<JobAssignment> batch = manager.findLatestByJobsAndWorkers(Set.of(job.getId()), Set.of(worker.getId()));

        assertEquals(1, batch.size(), "aynı (ilan, işçi) çifti için tek satır kalmalı");
        assertSame(paid, batch.get(0));
        assertSame(manager.findLatestByJobAndWorker(job.getId(), worker.getId()).orElseThrow(), batch.get(0));
    }

    @Test
    void aRowWithoutACreationStampNeverBeatsAStampedOne() {
        JobAssignment unstamped = assignment(AssignmentStatus.CANCELLED, PaymentStatus.REFUNDED, null);
        JobAssignment stamped = assignment(AssignmentStatus.CONFIRMED, PaymentStatus.HELD_IN_ESCROW,
                Instant.now().minusSeconds(86_400));
        when(jobAssignmentDao.findAllByJobIdAndWorkerId(job.getId(), worker.getId()))
                .thenReturn(List.of(unstamped, stamped));

        assertSame(stamped, manager.findLatestByJobAndWorker(job.getId(), worker.getId()).orElseThrow());
    }

    @Test
    void anEmptyIdSetAsksTheDatabaseNothing() {
        assertTrue(manager.findLatestByJobsAndWorkers(Set.of(), Set.of(worker.getId())).isEmpty());
        assertTrue(manager.findLatestByJobsAndWorkers(Set.of(job.getId()), Set.of()).isEmpty());
        verify(jobAssignmentDao, never()).findAllByJobIdInAndWorkerIdIn(any(), any());
    }
}
