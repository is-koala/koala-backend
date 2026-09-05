package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.business.abstracts.JobService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.dtos.jobApplication.request.AnswerApplicationDto;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.core.mappers.JobApplicationMapper;
import com.exskylab.koala.dataAccess.JobApplicationDao;
import com.exskylab.koala.entities.ApplicationStatus;
import com.exskylab.koala.entities.Company;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.JobApplication;
import com.exskylab.koala.entities.JobEmployerType;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobApplicationAuthorizationTest {

    private JobApplicationDao jobApplicationDao;
    private SecurityService securityService;
    private CompanyContactService companyContactService;
    private JobAssignmentService jobAssignmentService;
    private JobApplicationManager manager;

    private final User jobOwner = user();
    private final User applicant = user();
    private final User stranger = user();

    @BeforeEach
    void setUp() {
        jobApplicationDao = mock(JobApplicationDao.class);
        securityService = mock(SecurityService.class);
        companyContactService = mock(CompanyContactService.class);
        jobAssignmentService = mock(JobAssignmentService.class);

        manager = new JobApplicationManager(
                jobApplicationDao,
                mock(JobService.class),
                securityService,
                companyContactService,
                jobAssignmentService,
                mock(JobApplicationMapper.class),
                new PageableSanitizer(),
                mock(com.exskylab.koala.business.abstracts.GalleryService.class),
                mock(com.exskylab.koala.business.abstracts.ActivityNotificationService.class),
                mock(com.exskylab.koala.business.abstracts.BankAccountService.class),
                mock(com.exskylab.koala.business.abstracts.PricingService.class));

        when(jobApplicationDao.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void aStrangerCannotAcceptAnApplicationOnSomeoneElsesJob() {
        JobApplication application = individualJobApplication();
        when(jobApplicationDao.findById(application.getId())).thenReturn(Optional.of(application));
        when(securityService.getAuthenticatedUser()).thenReturn(stranger);

        assertThrows(RuntimeException.class,
                () -> manager.respondToApplication(application.getId(), accept()));

        verify(jobAssignmentService, never()).save(any());
    }

    @Test
    void aStrangerCannotRejectAnApplicationOnSomeoneElsesJob() {
        JobApplication application = individualJobApplication();
        when(jobApplicationDao.findById(application.getId())).thenReturn(Optional.of(application));
        when(securityService.getAuthenticatedUser()).thenReturn(stranger);

        assertThrows(RuntimeException.class,
                () -> manager.respondToApplication(application.getId(), reject()));
    }

    @Test
    void theApplicantCannotAcceptTheirOwnApplication() {
        JobApplication application = individualJobApplication();
        when(jobApplicationDao.findById(application.getId())).thenReturn(Optional.of(application));
        when(securityService.getAuthenticatedUser()).thenReturn(applicant);

        assertThrows(RuntimeException.class,
                () -> manager.respondToApplication(application.getId(), accept()));
    }

    @Test
    void theJobOwnerCanRespond() {
        JobApplication application = individualJobApplication();
        when(jobApplicationDao.findById(application.getId())).thenReturn(Optional.of(application));
        when(securityService.getAuthenticatedUser()).thenReturn(jobOwner);

        assertDoesNotThrow(() -> manager.respondToApplication(application.getId(), reject()));
    }

    @Test
    void aUserWhoIsNotAContactCannotRespondToACompanyJobApplication() {
        JobApplication application = companyJobApplication();
        UUID companyId = application.getJob().getCompany().getId();
        when(jobApplicationDao.findById(application.getId())).thenReturn(Optional.of(application));
        when(securityService.getAuthenticatedUser()).thenReturn(stranger);
        when(companyContactService.isUserAContactOfCompany(companyId, stranger.getId())).thenReturn(false);

        assertThrows(RuntimeException.class,
                () -> manager.respondToApplication(application.getId(), accept()));

        verify(jobAssignmentService, never()).save(any());
    }

    @Test
    void aCompanyContactCanRespond() {
        JobApplication application = companyJobApplication();
        UUID companyId = application.getJob().getCompany().getId();
        when(jobApplicationDao.findById(application.getId())).thenReturn(Optional.of(application));
        when(securityService.getAuthenticatedUser()).thenReturn(jobOwner);
        when(companyContactService.isUserAContactOfCompany(companyId, jobOwner.getId())).thenReturn(true);

        assertDoesNotThrow(() -> manager.respondToApplication(application.getId(), reject()));
    }

    private JobApplication individualJobApplication() {
        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setEmployerType(JobEmployerType.INDIVIDUAL);
        job.setCreator(jobOwner);
        return applicationFor(job);
    }

    private JobApplication companyJobApplication() {
        Company company = new Company();
        company.setId(UUID.randomUUID());

        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setEmployerType(JobEmployerType.COMPANY);
        job.setCompany(company);
        job.setCreator(jobOwner);
        return applicationFor(job);
    }

    private JobApplication applicationFor(Job job) {
        JobApplication application = new JobApplication();
        application.setId(UUID.randomUUID());
        application.setJob(job);
        application.setUser(applicant);
        application.setStatus(ApplicationStatus.APPLIED);
        return application;
    }

    private AnswerApplicationDto accept() {
        return new AnswerApplicationDto("ACCEPTED", null);
    }

    private AnswerApplicationDto reject() {
        return new AnswerApplicationDto("REJECTED", "uygun degil");
    }

    private static User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        return user;
    }
}
