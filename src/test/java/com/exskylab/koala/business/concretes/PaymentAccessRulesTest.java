package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.AssignmentParticipants;
import com.exskylab.koala.core.helpers.PaymentStateMachine;
import com.exskylab.koala.core.monitoring.PaymentMetrics;
import com.exskylab.koala.core.properties.PaymentVerificationProperties;
import com.exskylab.koala.core.utilities.payment.iyzico.IyzicoPaymentService;
import com.exskylab.koala.dataAccess.JobAssignmentDao;
import com.exskylab.koala.dataAccess.PaymentDao;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.Payment;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.context.ApplicationEventPublisher;
import org.junit.jupiter.api.Test;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentAccessRulesTest {

    private PaymentDao paymentDao;
    private JobAssignmentDao jobAssignmentDao;
    private SecurityService securityService;
    private PaymentManager paymentManager;

    private final User worker = user();
    private final User employer = user();
    private final User stranger = user();

    private JobAssignment assignment;

    @BeforeEach
    void setUp() {
        paymentDao = mock(PaymentDao.class);
        jobAssignmentDao = mock(JobAssignmentDao.class);
        securityService = mock(SecurityService.class);

        CompanyContactService companyContactService = mock(CompanyContactService.class);
        AssignmentParticipants participants = new AssignmentParticipants(companyContactService);

        paymentManager = new PaymentManager(paymentDao, mock(IyzicoPaymentService.class),
                mock(PaymentStateMachine.class), securityService, jobAssignmentDao, participants,
                new PaymentMetrics(new SimpleMeterRegistry()), new PaymentVerificationProperties(),
                mock(ApplicationEventPublisher.class));

        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setCreator(employer);

        assignment = new JobAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setWorker(worker);
        assignment.setJob(job);

        when(jobAssignmentDao.findById(assignment.getId())).thenReturn(Optional.of(assignment));
        when(paymentDao.findAllByJobAssignmentIdOrderByCreatedAtDesc(assignment.getId()))
                .thenReturn(List.of(new Payment()));
    }

    @Test
    void theWorkerSeesTheAttempts() {
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());

        assertEquals(1, paymentManager.getAttemptsForAssignment(assignment.getId()).size());
    }

    @Test
    void theEmployerSeesTheAttempts() {
        when(securityService.getAuthenticatedUserId()).thenReturn(employer.getId());

        assertEquals(1, paymentManager.getAttemptsForAssignment(assignment.getId()).size());
    }

    @Test
    void aStrangerCannotSeeTheAttempts() {
        when(securityService.getAuthenticatedUserId()).thenReturn(stranger.getId());

        assertThrows(ResourceNotFoundException.class,
                () -> paymentManager.getAttemptsForAssignment(assignment.getId()));

        verify(paymentDao, never()).findAllByJobAssignmentIdOrderByCreatedAtDesc(any());
    }

    @Test
    void anUnknownAssignmentIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(securityService.getAuthenticatedUserId()).thenReturn(worker.getId());
        when(jobAssignmentDao.findById(unknown)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> paymentManager.getAttemptsForAssignment(unknown));
    }

    private static User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        return user;
    }
}
