package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.*;
import com.exskylab.koala.core.dtos.jobApplication.request.AnswerApplicationDto;
import com.exskylab.koala.core.dtos.jobApplication.request.CreateJobApplicationDto;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.helpers.JobAvailability;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.core.mappers.JobApplicationMapper;
import com.exskylab.koala.core.properties.JobSearchProperties;
import com.exskylab.koala.dataAccess.JobApplicationDao;
import com.exskylab.koala.entities.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApplicationDecisionRulesTest {

    private static final Instant NOW = Instant.parse("2026-09-09T10:00:00Z");

    private JobApplicationDao jobApplicationDao;
    private JobAssignmentService jobAssignmentService;
    private ActivityNotificationService activityNotificationService;
    private SecurityService securityService;
    private JobApplicationManager manager;

    private final User employer = user();
    private User applicant;
    private Job job;

    @BeforeEach
    void setUp() {
        jobApplicationDao = mock(JobApplicationDao.class);
        jobAssignmentService = mock(JobAssignmentService.class);
        activityNotificationService = mock(ActivityNotificationService.class);
        securityService = mock(SecurityService.class);

        applicant = user();
        applicant.setIdentityVerified(true);
        applicant.setAddress(new Address());

        BankAccountService bankAccountService = mock(BankAccountService.class);
        when(bankAccountService.findPrimaryIban(applicant.getId()))
                .thenReturn(Optional.of("TR330006100519786457841326"));

        job = new Job();
        job.setId(UUID.randomUUID());
        job.setCreator(employer);
        job.setEmployerType(JobEmployerType.INDIVIDUAL);
        job.setStatus(JobStatus.OPEN);
        job.setEndDateTime(LocalDateTime.of(2026, 9, 10, 13, 0));

        JobService jobService = mock(JobService.class);
        when(jobService.getById(job.getId())).thenReturn(job);

        manager = new JobApplicationManager(jobApplicationDao, jobService, securityService,
                mock(CompanyContactService.class), jobAssignmentService, mock(JobApplicationMapper.class),
                new PageableSanitizer(), mock(GalleryService.class), activityNotificationService,
                bankAccountService, mock(PricingService.class),
                new JobAvailability(Clock.fixed(NOW, ZoneOffset.UTC), new JobSearchProperties()));

        when(jobApplicationDao.save(any())).thenAnswer(i -> i.getArgument(0));
        when(jobAssignmentService.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static User user() {
        User u = new User();
        u.setId(UUID.randomUUID());
        return u;
    }

    private void apply() {
        when(securityService.getAuthenticatedUser()).thenReturn(applicant);
        manager.applyToJob(job.getId(), new CreateJobApplicationDto());
    }

    private JobApplication pendingApplication() {
        JobApplication application = new JobApplication();
        application.setId(UUID.randomUUID());
        application.setJob(job);
        application.setUser(applicant);
        application.setStatus(ApplicationStatus.APPLIED);
        when(jobApplicationDao.findById(application.getId())).thenReturn(Optional.of(application));
        return application;
    }

    private void respond(JobApplication application, String status) {
        when(securityService.getAuthenticatedUser()).thenReturn(employer);
        manager.respondToApplication(application.getId(), new AnswerApplicationDto(status, null));
    }

    @Test
    void aClosedJobTakesNoNewApplications() {
        job.setStatus(JobStatus.CLOSED);

        ConflictException thrown = assertThrows(ConflictException.class, this::apply);

        assertEquals("JOB_APPLICATION_JOB_NOT_OPEN", thrown.getErrorCode());
        verify(jobApplicationDao, never()).save(any());
    }

    @Test
    void aCancelledJobTakesNoNewApplications() {
        job.setStatus(JobStatus.CANCELLED);

        ConflictException thrown = assertThrows(ConflictException.class, this::apply);

        assertEquals("JOB_APPLICATION_JOB_NOT_OPEN", thrown.getErrorCode());
    }

    @Test
    void anOpenJobStillTakesApplications() {
        assertDoesNotThrow(this::apply);
        verify(jobApplicationDao).save(any(JobApplication.class));
    }

    @Test
    void anExpiredOpenJobTakesNoNewApplicationAndChangesNothing() {
        job.setEndDateTime(LocalDateTime.of(2026, 9, 9, 12, 59, 59));

        ConflictException thrown = assertThrows(ConflictException.class, this::apply);

        assertEquals("JOB_APPLICATION_JOB_NOT_OPEN", thrown.getErrorCode());
        verify(jobApplicationDao, never()).save(any());
    }

    @Test
    void closingAJobDoesNotFreezeTheApplicationsItAlreadyHas() {
        JobApplication application = pendingApplication();
        job.setStatus(JobStatus.CLOSED);

        assertDoesNotThrow(() -> respond(application, "ACCEPTED"));

        assertEquals(ApplicationStatus.ACCEPTED, application.getStatus());
        verify(jobAssignmentService).save(any(JobAssignment.class));
    }

    @Test
    void anApplicationOnACancelledJobCannotBeAccepted() {
        JobApplication application = pendingApplication();
        job.setStatus(JobStatus.CANCELLED);

        ConflictException thrown = assertThrows(ConflictException.class,
                () -> respond(application, "ACCEPTED"));

        assertEquals("JOB_APPLICATION_JOB_CANCELLED", thrown.getErrorCode());
        verify(jobAssignmentService, never()).save(any());
    }

    @Test
    void anApplicationOnACancelledJobCannotBeRejectedEither() {
        JobApplication application = pendingApplication();
        job.setStatus(JobStatus.CANCELLED);

        assertThrows(ConflictException.class, () -> respond(application, "REJECTED"));

        assertEquals(ApplicationStatus.APPLIED, application.getStatus());
    }

    @Test
    void theAcceptedWorkerIsToldOnce() {
        JobApplication application = pendingApplication();

        respond(application, "ACCEPTED");

        verify(activityNotificationService).assignmentOffered(any(JobAssignment.class));
        verify(activityNotificationService, never()).applicationAnswered(any());
    }

    @Test
    void theRejectedApplicantIsStillTold() {
        JobApplication application = pendingApplication();

        respond(application, "REJECTED");

        verify(activityNotificationService).applicationAnswered(application);
        verify(activityNotificationService, never()).assignmentOffered(any());
    }
}
