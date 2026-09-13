package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.ActivityNotificationService;
import com.exskylab.koala.business.abstracts.BankAccountService;
import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.business.abstracts.GalleryService;
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.business.abstracts.JobService;
import com.exskylab.koala.business.abstracts.PricingService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.dtos.jobApplication.request.AnswerApplicationDto;
import com.exskylab.koala.core.dtos.jobApplication.request.CreateJobApplicationDto;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.helpers.JobAvailability;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.core.mappers.JobApplicationMapper;
import com.exskylab.koala.core.properties.JobSearchProperties;
import com.exskylab.koala.dataAccess.JobApplicationDao;
import com.exskylab.koala.entities.Address;
import com.exskylab.koala.entities.ApplicationStatus;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.JobApplication;
import com.exskylab.koala.entities.JobEmployerType;
import com.exskylab.koala.entities.JobStatus;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApplicationWithdrawalTest {

    private static final Instant POLICY_NOW = Instant.parse("2026-09-09T10:00:00Z");
    private static final JobSearchProperties POLICY_PROPERTIES = new JobSearchProperties();
    private static final ZoneId POLICY_ZONE = POLICY_PROPERTIES.zone();

    private JobApplicationDao jobApplicationDao;
    private SecurityService securityService;
    private ActivityNotificationService activityNotificationService;
    private JobAssignmentService jobAssignmentService;
    private JobService jobService;
    private JobApplicationManager manager;

    private final User employer = user();
    private User applicant;
    private final User stranger = user();
    private Job job;

    @BeforeEach
    void setUp() {
        jobApplicationDao = mock(JobApplicationDao.class);
        securityService = mock(SecurityService.class);
        activityNotificationService = mock(ActivityNotificationService.class);
        jobAssignmentService = mock(JobAssignmentService.class);
        jobService = mock(JobService.class);

        applicant = user();
        applicant.setIdentityVerified(true);
        applicant.setAddress(new Address());

        BankAccountService bankAccountService = mock(BankAccountService.class);
        when(bankAccountService.findPrimaryIban(applicant.getId()))
                .thenReturn(Optional.of("TR330006100519786457841326"));

        job = new Job();
        job.setId(UUID.randomUUID());
        job.setTitle("Barista aranıyor");
        job.setCreator(employer);
        job.setEmployerType(JobEmployerType.INDIVIDUAL);
        job.setStatus(JobStatus.OPEN);
        job.setEndDateTime(LocalDateTime.ofInstant(POLICY_NOW, POLICY_ZONE).plusDays(1));
        when(jobService.getById(job.getId())).thenReturn(job);

        when(securityService.getAuthenticatedUser()).thenReturn(applicant);
        when(securityService.getAuthenticatedUserId()).thenReturn(applicant.getId());

        manager = new JobApplicationManager(jobApplicationDao, jobService, securityService,
                mock(CompanyContactService.class), jobAssignmentService,
                mock(JobApplicationMapper.class), new PageableSanitizer(),
                mock(GalleryService.class), activityNotificationService,
                bankAccountService, mock(PricingService.class),
                new JobAvailability(Clock.fixed(POLICY_NOW, POLICY_ZONE), POLICY_PROPERTIES));

        when(jobApplicationDao.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void theOwnerWithdrawsAnAppliedApplication() {
        JobApplication application = application(ApplicationStatus.APPLIED);

        assertDoesNotThrow(() -> manager.withdrawApplication(application.getId()));

        assertEquals(ApplicationStatus.WITHDRAWN, application.getStatus());
        verify(jobApplicationDao).save(application);
    }

    @Test
    void someoneElsesApplicationIsNotFoundRatherThanForbidden() {
        JobApplication application = application(ApplicationStatus.APPLIED);
        when(securityService.getAuthenticatedUserId()).thenReturn(stranger.getId());

        ResourceNotFoundException thrown = assertThrows(ResourceNotFoundException.class,
                () -> manager.withdrawApplication(application.getId()));

        assertEquals(HttpStatus.NOT_FOUND, thrown.getHttpStatus());
        assertEquals("JOB_APPLICATION_NOT_FOUND", thrown.getErrorCode());
        assertEquals(ApplicationStatus.APPLIED, application.getStatus());
        verify(jobApplicationDao, never()).save(any());
    }

    @Test
    void anUnknownApplicationIsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(jobApplicationDao.findById(unknown)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> manager.withdrawApplication(unknown));
    }

    @Test
    void anAcceptedApplicationCannotBeWithdrawn() {
        JobApplication application = application(ApplicationStatus.ACCEPTED);

        ConflictException thrown = assertThrows(ConflictException.class,
                () -> manager.withdrawApplication(application.getId()));

        assertEquals(HttpStatus.CONFLICT, thrown.getHttpStatus());
        assertEquals(ApplicationStatus.ACCEPTED, application.getStatus());
        verify(jobApplicationDao, never()).save(any());
    }

    @Test
    void aRejectedApplicationCannotBeWithdrawn() {
        JobApplication application = application(ApplicationStatus.REJECTED);

        ConflictException thrown = assertThrows(ConflictException.class,
                () -> manager.withdrawApplication(application.getId()));

        assertEquals(HttpStatus.CONFLICT, thrown.getHttpStatus());
    }

    @Test
    void withdrawingTwiceIsAConflict() {
        JobApplication application = application(ApplicationStatus.APPLIED);
        manager.withdrawApplication(application.getId());

        ConflictException thrown = assertThrows(ConflictException.class,
                () -> manager.withdrawApplication(application.getId()));

        assertEquals(HttpStatus.CONFLICT, thrown.getHttpStatus());
    }

    @Test
    void withdrawingChangesTheStatusAndDoesNotDeleteTheRecord() {
        JobApplication application = application(ApplicationStatus.APPLIED);

        manager.withdrawApplication(application.getId());

        assertFalse(application.isDeleted(), "Geri çekme kaydı silmez, durumu değiştirir.");
        verify(jobApplicationDao, never()).delete(any());
        verify(jobApplicationDao, never()).deleteById(any());
    }

    @Test
    void theEmployerIsNotifiedOfTheWithdrawal() {
        JobApplication application = application(ApplicationStatus.APPLIED);

        manager.withdrawApplication(application.getId());

        verify(activityNotificationService).applicationWithdrawn(application);
    }

    @Test
    void aWithdrawnApplicationCannotBeAccepted() {
        JobApplication application = application(ApplicationStatus.WITHDRAWN);
        when(securityService.getAuthenticatedUser()).thenReturn(employer);

        ConflictException thrown = assertThrows(ConflictException.class,
                () -> manager.respondToApplication(application.getId(),
                        new AnswerApplicationDto("ACCEPTED", null)));

        assertEquals(HttpStatus.CONFLICT, thrown.getHttpStatus());
        verify(jobAssignmentService, never()).save(any());
    }

    @Test
    void aWithdrawnApplicationCannotBeRejected() {
        JobApplication application = application(ApplicationStatus.WITHDRAWN);
        when(securityService.getAuthenticatedUser()).thenReturn(employer);

        ConflictException thrown = assertThrows(ConflictException.class,
                () -> manager.respondToApplication(application.getId(),
                        new AnswerApplicationDto("REJECTED", "gerek kalmadı")));

        assertEquals(HttpStatus.CONFLICT, thrown.getHttpStatus());
    }

    @Test
    void aWithdrawnApplicationDoesNotBlockANewApplicationToTheSameJob() {
        when(jobApplicationDao.existsByJobIdAndUserIdAndStatusNot(
                job.getId(), applicant.getId(), ApplicationStatus.WITHDRAWN)).thenReturn(false);

        assertDoesNotThrow(() -> manager.applyToJob(job.getId(), new CreateJobApplicationDto()));

        verify(jobApplicationDao).save(any(JobApplication.class));
        verify(jobApplicationDao).existsByJobIdAndUserIdAndStatusNot(
                job.getId(), applicant.getId(), ApplicationStatus.WITHDRAWN);
    }

    @Test
    void aLiveApplicationStillBlocksASecondApplicationToTheSameJob() {
        when(jobApplicationDao.existsByJobIdAndUserIdAndStatusNot(
                job.getId(), applicant.getId(), ApplicationStatus.WITHDRAWN)).thenReturn(true);

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> manager.applyToJob(job.getId(), new CreateJobApplicationDto()));

        assertEquals("JOB_APPLICATION_USER_ALREADY_APPLIED_TO_JOB", thrown.getErrorCode());
        verify(jobApplicationDao, never()).save(any());
    }

    private JobApplication application(ApplicationStatus status) {
        JobApplication application = new JobApplication();
        application.setId(UUID.randomUUID());
        application.setJob(job);
        application.setUser(applicant);
        application.setStatus(status);
        when(jobApplicationDao.findById(application.getId())).thenReturn(Optional.of(application));
        return application;
    }

    private static User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        return user;
    }
}
