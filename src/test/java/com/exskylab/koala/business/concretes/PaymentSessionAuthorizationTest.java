package com.exskylab.koala.business.concretes;

import com.exskylab.koala.support.FallbackSettings;
import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.exceptions.ForbiddenException;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.exceptions.ExternalServiceException;
import com.exskylab.koala.core.constants.PaymentMessages;
import com.exskylab.koala.business.abstracts.BankAccountService;
import com.exskylab.koala.business.abstracts.PaymentService;
import com.exskylab.koala.business.abstracts.BadgeService;
import com.exskylab.koala.core.helpers.AssignmentParticipants;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.core.helpers.PaymentStateMachine;
import com.exskylab.koala.core.properties.AppProperties;
import com.exskylab.koala.core.mappers.JobAssignmentMapper;
import com.exskylab.koala.core.monitoring.PaymentMetrics;
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
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
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
    private PaymentService paymentService;
    private UserService userService;
    private BankAccountService bankAccountService;
    private SimpleMeterRegistry meterRegistry;
    private JobAssignmentManager manager;

    private final User employer = verifiedUser();
    private final User worker = user();
    private final User stranger = user();

    @BeforeEach
    void setUp() {
        jobAssignmentDao = mock(JobAssignmentDao.class);
        securityService = mock(SecurityService.class);
        companyContactService = mock(CompanyContactService.class);
        iyzicoPaymentService = mock(IyzicoPaymentService.class);
        paymentService = mock(PaymentService.class);
        userService = mock(UserService.class);
        bankAccountService = mock(BankAccountService.class);
        meterRegistry = new SimpleMeterRegistry();

        com.exskylab.koala.entities.Payment reserved = new com.exskylab.koala.entities.Payment();
        reserved.setId(UUID.randomUUID());
        reserved.setConversationId("reserved-conversation");
        when(paymentService.startAttempt(any(), any())).thenReturn(reserved);

        manager = new JobAssignmentManager(
                jobAssignmentDao,
                securityService,
                mock(JobAssignmentMapper.class),
                iyzicoPaymentService,
                companyContactService,
                userService,
                new PricingManager(FallbackSettings.commissionCalculator()),
                paymentService,
                new PaymentStateMachine(),
                new AppProperties("http://localhost:8080", "http://localhost:3000"),
                bankAccountService,
                new PageableSanitizer(),
                new AssignmentParticipants(companyContactService),
                mock(BadgeService.class),
                mock(com.exskylab.koala.business.abstracts.PayoutService.class),
                new com.exskylab.koala.core.properties.AutoCompletionProperties(),
                new com.exskylab.koala.core.properties.UnpaidExpiryProperties(), passthroughSettings(),
                mock(com.exskylab.koala.business.abstracts.GalleryService.class), mock(com.exskylab.koala.business.abstracts.ActivityNotificationService.class), mock(org.springframework.context.ApplicationEventPublisher.class),
                new PaymentMetrics(meterRegistry),
                mock(com.exskylab.koala.dataAccess.CancellationDao.class),
                mock(com.exskylab.koala.business.abstracts.JobService.class),
                mock(com.exskylab.koala.dataAccess.ReviewDao.class),
                mock(com.exskylab.koala.business.abstracts.TransportService.class),
                new com.exskylab.koala.core.helpers.JobEditors(companyContactService));
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
        verify(paymentService, never()).startAttempt(any(), any());
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

    private static User verifiedUser() {
        User u = user();
        u.setIdentityVerified(true);
        u.setTcIdentityNumber("10000000146");
        return u;
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
        assignment.setPricingVersion("2026-09-v1");
        assignment.setCurrency("TRY");
        assignment.setWorkerPayout(new java.math.BigDecimal("1000.00"));
        assignment.setCommissionAmount(new java.math.BigDecimal("75.00"));
        assignment.setCommissionRate(new java.math.BigDecimal("0.075"));
        assignment.setTotalCharged(new java.math.BigDecimal("1075.00"));
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

    @Test
    void anUnverifiedIndividualEmployerCannotStartThePayment() {
        employer.setIdentityVerified(false);
        employer.setTcIdentityNumber(null);
        JobAssignment assignment = confirmedAssignment(individualJob());
        when(jobAssignmentDao.findById(assignment.getId())).thenReturn(Optional.of(assignment));
        when(securityService.getAuthenticatedUser()).thenReturn(employer);

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> manager.initiatePaymentSession(assignment.getId(), "127.0.0.1"));

        assertEquals("JOB_ASSIGNMENT_EMPLOYER_IDENTITY_NOT_VERIFIED", thrown.getErrorCode());
        verify(iyzicoPaymentService, never()).createSubMerchant(any());
        verify(iyzicoPaymentService, never()).initiateCheckoutFormPayment(any());
    }

    @Test
    void aCompanyJobIsNotBlockedByTheIndividualIdentityGate() {
        Job job = companyJob();
        JobAssignment assignment = confirmedAssignment(job);
        when(jobAssignmentDao.findById(assignment.getId())).thenReturn(Optional.of(assignment));
        when(securityService.getAuthenticatedUser()).thenReturn(employer);
        when(companyContactService.isUserAContactOfCompany(job.getCompany().getId(), employer.getId()))
                .thenReturn(true);

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> manager.initiatePaymentSession(assignment.getId(), "127.0.0.1"));

        assertNotEquals("JOB_ASSIGNMENT_EMPLOYER_IDENTITY_NOT_VERIFIED",
                thrown instanceof com.exskylab.koala.core.exceptions.KoalaException k ? k.getErrorCode() : null);
    }

    @Test
    void aPendingAttemptStopsBeforeAnotherProviderSessionOrSubmerchantIsCreated() {
        JobAssignment assignment = confirmedAssignment(individualJob());
        when(jobAssignmentDao.findById(assignment.getId())).thenReturn(Optional.of(assignment));
        when(securityService.getAuthenticatedUser()).thenReturn(employer);
        when(paymentService.hasPendingAttemptForAssignment(assignment.getId())).thenReturn(true);

        var thrown = assertThrows(com.exskylab.koala.core.exceptions.ConflictException.class,
                () -> manager.initiatePaymentSession(assignment.getId(), "127.0.0.1"));

        assertEquals("PAYMENT_ATTEMPT_IN_PROGRESS", thrown.getErrorCode());
        verify(iyzicoPaymentService, never()).createSubMerchant(any());
        verify(iyzicoPaymentService, never()).initiateCheckoutFormPayment(any());
    }

    @Test
    void aProviderInitiationFailureIncrementsTheBoundedFailureMetric() {
        Job job = individualJob();
        job.setAddress(address());
        employer.setAddress(address());
        employer.setFirstName("Koala");
        employer.setLastName("Employer");
        employer.setEmail("employer@example.test");
        employer.setPhoneNumber("+905551112233");
        worker.setIyzicoSubmerchantKey("submerchant-key");
        JobAssignment assignment = confirmedAssignment(job);
        assignment.setPricingVersion("2026-09-v1");
        assignment.setCurrency("TRY");
        assignment.setWorkerPayout(new java.math.BigDecimal("1000.00"));
        assignment.setCommissionAmount(new java.math.BigDecimal("75.00"));
        assignment.setCommissionRate(new java.math.BigDecimal("0.075"));
        assignment.setTotalCharged(new java.math.BigDecimal("1075.00"));
        com.exskylab.koala.entities.Payment payment = new com.exskylab.koala.entities.Payment();
        payment.setId(UUID.randomUUID());
        payment.setConversationId("conversation-1");
        when(jobAssignmentDao.findById(assignment.getId())).thenReturn(Optional.of(assignment));
        when(securityService.getAuthenticatedUser()).thenReturn(employer);
        when(paymentService.startAttempt(any(), any())).thenReturn(payment);
        when(iyzicoPaymentService.initiateCheckoutFormPayment(any()))
                .thenThrow(new IllegalStateException("provider unavailable"));

        assertThrows(IllegalStateException.class,
                () -> manager.initiatePaymentSession(assignment.getId(), "127.0.0.1"));

        assertEquals(1.0, meterRegistry.get("koala.payment.operations")
                .tag("operation", "initiation")
                .tag("outcome", "provider_error")
                .counter()
                .count());
        verify(paymentService).markInitiationUncertain(payment.getId());
    }

    @Test
    void theAttemptIsReservedBeforeAnyExternalSubmerchantOrCheckoutCall() {
        Job job = individualJob();
        job.setAddress(address());
        employer.setAddress(address());
        employer.setFirstName("Koala");
        employer.setLastName("Employer");
        employer.setEmail("employer@example.test");
        employer.setPhoneNumber("+905551112233");
        worker.setIyzicoSubmerchantKey(null);
        worker.setIdentityVerified(true);
        worker.setTcIdentityNumber("10000000146");
        worker.setFirstName("Koala");
        worker.setLastName("Worker");
        worker.setEmail("worker@example.test");
        worker.setPhoneNumber("+905554445566");
        worker.setAddress(address());
        JobAssignment assignment = confirmedAssignment(job);
        when(jobAssignmentDao.findById(assignment.getId())).thenReturn(Optional.of(assignment));
        when(securityService.getAuthenticatedUser()).thenReturn(employer);
        com.exskylab.koala.entities.BankAccount workerAccount = new com.exskylab.koala.entities.BankAccount();
        workerAccount.setIban("TR000000000000000000000001");
        workerAccount.setVerificationStatus(com.exskylab.koala.entities.BankAccountVerificationStatus.VERIFIED);
        when(bankAccountService.findPrimaryAccount(worker.getId())).thenReturn(Optional.of(workerAccount));
        var merchant = new com.exskylab.koala.core.utilities.payment.iyzico.dtos.SubMerchantCreateResponseDto();
        merchant.setSubMerchantKey("submerchant-key");
        when(iyzicoPaymentService.createSubMerchant(any())).thenReturn(merchant);
        when(iyzicoPaymentService.initiateCheckoutFormPayment(any()))
                .thenThrow(new IllegalStateException("provider unavailable"));

        assertThrows(IllegalStateException.class,
                () -> manager.initiatePaymentSession(assignment.getId(), "127.0.0.1"));

        var order = org.mockito.Mockito.inOrder(paymentService, iyzicoPaymentService);
        order.verify(paymentService).startAttempt(any(), any());
        order.verify(iyzicoPaymentService).createSubMerchant(any());
        order.verify(paymentService).attachProviderSubmerchantKey(any(), org.mockito.ArgumentMatchers.eq("submerchant-key"));
        order.verify(iyzicoPaymentService).initiateCheckoutFormPayment(any());
    }

    @Test
    void anUnverifiedWorkerIbanStillCreatesTheSubmerchant() {
        Job job = individualJob();
        job.setAddress(address());
        employer.setAddress(address());
        employer.setFirstName("Koala");
        employer.setLastName("Employer");
        employer.setEmail("employer@example.test");
        employer.setPhoneNumber("+905551112233");
        worker.setIyzicoSubmerchantKey(null);
        worker.setIdentityVerified(true);
        worker.setTcIdentityNumber("10000000146");
        worker.setFirstName("Koala");
        worker.setLastName("Worker");
        worker.setEmail("worker@example.test");
        worker.setPhoneNumber("+905554445566");
        worker.setAddress(address());
        JobAssignment assignment = confirmedAssignment(job);
        when(jobAssignmentDao.findById(assignment.getId())).thenReturn(Optional.of(assignment));
        when(securityService.getAuthenticatedUser()).thenReturn(employer);
        com.exskylab.koala.entities.BankAccount workerAccount = new com.exskylab.koala.entities.BankAccount();
        workerAccount.setIban("TR000000000000000000000001");
        workerAccount.setVerificationStatus(com.exskylab.koala.entities.BankAccountVerificationStatus.UNVERIFIED);
        when(bankAccountService.findPrimaryAccount(worker.getId())).thenReturn(Optional.of(workerAccount));
        var merchant = new com.exskylab.koala.core.utilities.payment.iyzico.dtos.SubMerchantCreateResponseDto();
        merchant.setSubMerchantKey("submerchant-key");
        when(iyzicoPaymentService.createSubMerchant(any())).thenReturn(merchant);
        when(iyzicoPaymentService.initiateCheckoutFormPayment(any()))
                .thenThrow(new IllegalStateException("provider unavailable"));

        assertThrows(IllegalStateException.class,
                () -> manager.initiatePaymentSession(assignment.getId(), "127.0.0.1"));

        verify(iyzicoPaymentService).createSubMerchant(any());
    }

    @Test
    void anExplicitProviderRejectionFailsTheReservedAttemptInsteadOfLeavingItAmbiguous() {
        Job job = individualJob();
        job.setAddress(address());
        employer.setAddress(address());
        employer.setFirstName("Koala");
        employer.setLastName("Employer");
        employer.setEmail("employer@example.test");
        employer.setPhoneNumber("+905551112233");
        worker.setIyzicoSubmerchantKey("submerchant-key");
        JobAssignment assignment = confirmedAssignment(job);
        com.exskylab.koala.entities.Payment payment = new com.exskylab.koala.entities.Payment();
        payment.setId(UUID.randomUUID());
        payment.setConversationId("conversation-1");
        when(jobAssignmentDao.findById(assignment.getId())).thenReturn(Optional.of(assignment));
        when(securityService.getAuthenticatedUser()).thenReturn(employer);
        when(paymentService.startAttempt(any(), any())).thenReturn(payment);
        when(iyzicoPaymentService.initiateCheckoutFormPayment(any()))
                .thenThrow(new ExternalServiceException(PaymentMessages.PROVIDER_REJECTED));

        assertThrows(ExternalServiceException.class,
                () -> manager.initiatePaymentSession(assignment.getId(), "127.0.0.1"));

        verify(paymentService).failInitiation(payment.getId());
        verify(paymentService, never()).markInitiationUncertain(payment.getId());
    }

    private static com.exskylab.koala.entities.Address address() {
        com.exskylab.koala.entities.Country country = new com.exskylab.koala.entities.Country();
        country.setName("Türkiye");
        com.exskylab.koala.entities.City city = new com.exskylab.koala.entities.City();
        city.setName("İstanbul");
        com.exskylab.koala.entities.Address address = new com.exskylab.koala.entities.Address();
        address.setCountry(country);
        address.setCity(city);
        address.setThoroughfare("Koala Caddesi");
        address.setBuildingNumber("1");
        address.setPostalCode("34000");
        return address;
    }
}
