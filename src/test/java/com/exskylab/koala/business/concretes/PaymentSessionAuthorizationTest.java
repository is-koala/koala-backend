package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.exceptions.ForbiddenException;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.business.abstracts.BankAccountService;
import com.exskylab.koala.business.abstracts.PaymentService;
import com.exskylab.koala.core.helpers.CommissionCalculator;
import com.exskylab.koala.business.abstracts.BadgeService;
import com.exskylab.koala.core.helpers.AssignmentParticipants;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.core.helpers.PaymentStateMachine;
import com.exskylab.koala.core.properties.AppProperties;
import com.exskylab.koala.core.properties.CommissionProperties;
import com.exskylab.koala.core.mappers.JobAssignmentMapper;
import com.exskylab.koala.core.utilities.payment.iyzico.IyzicoPaymentService;
import com.exskylab.koala.dataAccess.JobAssignmentDao;
import com.exskylab.koala.entities.AssignmentStatus;
import com.exskylab.koala.entities.Company;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.JobEmployerType;
import com.exskylab.koala.entities.PaymentStatus;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.BeforeEach;
import com.exskylab.koala.business.abstracts.DisputeService;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentSessionAuthorizationTest {

    private JobAssignmentDao jobAssignmentDao;
    private SecurityService securityService;
    private CompanyContactService companyContactService;
    private IyzicoPaymentService iyzicoPaymentService;
    private JobAssignmentManager manager;

    private final User employer = user();
    private final User worker = user();
    private final User stranger = user();

    @BeforeEach
    void setUp() {
        jobAssignmentDao = mock(JobAssignmentDao.class);
        securityService = mock(SecurityService.class);
        companyContactService = mock(CompanyContactService.class);
        iyzicoPaymentService = mock(IyzicoPaymentService.class);

        manager = new JobAssignmentManager(
                jobAssignmentDao,
                securityService,
                mock(JobAssignmentMapper.class),
                iyzicoPaymentService,
                companyContactService,
                mock(UserService.class),
                new CommissionCalculator(new CommissionProperties()),
                mock(PaymentService.class),
                new PaymentStateMachine(),
                new AppProperties("http://localhost:8080", "http://localhost:3000"),
                mock(BankAccountService.class),
                new PageableSanitizer(),
                new AssignmentParticipants(companyContactService),
                mock(BadgeService.class),
                mock(com.exskylab.koala.business.abstracts.PayoutService.class),
                new com.exskylab.koala.core.properties.AutoCompletionProperties(), passthroughSettings(),
                mock(com.exskylab.koala.business.abstracts.GalleryService.class));
    }

    @Test
    void aStrangerCannotInitiatePaymentForAnIndividualJob() {
        JobAssignment assignment = confirmedAssignment(individualJob());
        when(jobAssignmentDao.findById(assignment.getId())).thenReturn(Optional.of(assignment));
        when(securityService.getAuthenticatedUser()).thenReturn(stranger);

        assertThrows(ForbiddenException.class,
                () -> manager.initiatePaymentSession(assignment.getId(), "127.0.0.1"));

        verify(iyzicoPaymentService, never()).initiateCheckoutFormPayment(any());
    }

    @Test
    void theWorkerCannotInitiatePaymentForTheirOwnAssignment() {
        JobAssignment assignment = confirmedAssignment(individualJob());
        when(jobAssignmentDao.findById(assignment.getId())).thenReturn(Optional.of(assignment));
        when(securityService.getAuthenticatedUser()).thenReturn(worker);

        assertThrows(ForbiddenException.class,
                () -> manager.initiatePaymentSession(assignment.getId(), "127.0.0.1"));

        verify(iyzicoPaymentService, never()).initiateCheckoutFormPayment(any());
    }

    @Test
    void theEmployerPassesAuthorization() {
        JobAssignment assignment = confirmedAssignment(individualJob());
        when(jobAssignmentDao.findById(assignment.getId())).thenReturn(Optional.of(assignment));
        when(securityService.getAuthenticatedUser()).thenReturn(employer);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> manager.initiatePaymentSession(assignment.getId(), "127.0.0.1"));

        assertFalse(thrown instanceof ForbiddenException,
                "Isveren yetki kontrolunden gecmeliydi; sonrasinda adres/IBAN eksikliginden dusmesi beklenir.");
        assertInstanceOf(ValidationException.class, thrown);
    }

    @Test
    void aUserWhoIsNotAContactCannotInitiatePaymentForACompanyJob() {
        Job job = companyJob();
        JobAssignment assignment = confirmedAssignment(job);
        when(jobAssignmentDao.findById(assignment.getId())).thenReturn(Optional.of(assignment));
        when(securityService.getAuthenticatedUser()).thenReturn(stranger);
        when(companyContactService.isUserAContactOfCompany(job.getCompany().getId(), stranger.getId()))
                .thenReturn(false);

        assertThrows(ForbiddenException.class,
                () -> manager.initiatePaymentSession(assignment.getId(), "127.0.0.1"));

        verify(iyzicoPaymentService, never()).initiateCheckoutFormPayment(any());
    }

    private Job individualJob() {
        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setEmployerType(JobEmployerType.INDIVIDUAL);
        job.setCreator(employer);
        job.setSalary(new java.math.BigDecimal("1000.00"));
        return job;
    }

    private Job companyJob() {
        Company company = new Company();
        company.setId(UUID.randomUUID());

        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setEmployerType(JobEmployerType.COMPANY);
        job.setCompany(company);
        job.setCreator(employer);
        job.setSalary(new java.math.BigDecimal("1000.00"));
        return job;
    }

    private JobAssignment confirmedAssignment(Job job) {
        JobAssignment assignment = new JobAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setJob(job);
        assignment.setWorker(worker);
        assignment.setStatus(AssignmentStatus.CONFIRMED);
        assignment.setPaymentStatus(PaymentStatus.PENDING_COLLECTION);
        return assignment;
    }

    private static User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        return user;
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
