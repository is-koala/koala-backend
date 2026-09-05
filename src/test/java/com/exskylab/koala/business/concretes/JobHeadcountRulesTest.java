package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.ActivityNotificationService;
import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.business.abstracts.GalleryService;
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.business.abstracts.JobService;
import com.exskylab.koala.business.abstracts.PricingService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.dtos.jobApplication.request.AnswerApplicationDto;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.core.mappers.JobApplicationMapper;
import com.exskylab.koala.dataAccess.JobApplicationDao;
import com.exskylab.koala.entities.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class JobHeadcountRulesTest {

    private JobApplicationDao jobApplicationDao;
    private JobAssignmentService jobAssignmentService;
    private JobApplicationManager manager;
    private PricingService pricingService;

    private final User employer = user();

    @BeforeEach
    void setUp() {
        jobApplicationDao = mock(JobApplicationDao.class);
        jobAssignmentService = mock(JobAssignmentService.class);
        pricingService = mock(PricingService.class);

        SecurityService security = mock(SecurityService.class);
        when(security.getAuthenticatedUser()).thenReturn(employer);

        manager = new JobApplicationManager(jobApplicationDao, mock(JobService.class), security,
                mock(CompanyContactService.class), jobAssignmentService, mock(JobApplicationMapper.class),
                new PageableSanitizer(), mock(GalleryService.class), mock(ActivityNotificationService.class),
                mock(com.exskylab.koala.business.abstracts.BankAccountService.class), pricingService);
    }

    private static User user() {
        User u = new User();
        u.setId(UUID.randomUUID());
        return u;
    }

    private JobApplication applicationFor(Job job) {
        JobApplication application = new JobApplication();
        application.setId(UUID.randomUUID());
        application.setJob(job);
        application.setUser(user());
        application.setStatus(ApplicationStatus.APPLIED);

        when(jobApplicationDao.findById(application.getId())).thenReturn(Optional.of(application));
        return application;
    }

    private Job job(Integer headcount, LocalDateTime start) {
        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setCreator(employer);
        job.setEmployerType(JobEmployerType.INDIVIDUAL);
        job.setStatus(JobStatus.OPEN);
        job.setHeadcount(headcount);
        job.setStartDateTime(start);
        job.setEndDateTime(start.plusHours(8));
        job.setSalary(new BigDecimal("1000.00"));
        return job;
    }

    private static AnswerApplicationDto accept() {
        AnswerApplicationDto dto = new AnswerApplicationDto();
        dto.setStatus("ACCEPTED");
        return dto;
    }

    @Test
    void aJobWithoutAHeadcountStillHires() {
        Job job = job(null, LocalDateTime.now().plusDays(1));
        JobApplication application = applicationFor(job);

        assertDoesNotThrow(() -> manager.respondToApplication(application.getId(), accept()));
        assertEquals(ApplicationStatus.ACCEPTED, application.getStatus());
    }

    @Test
    void hiringBeyondTheHeadcountIsAllowed() {
        Job job = job(1, LocalDateTime.now().plusDays(1));

        assertDoesNotThrow(() -> manager.respondToApplication(applicationFor(job).getId(), accept()));
        assertDoesNotThrow(() -> manager.respondToApplication(applicationFor(job).getId(), accept()));

        verify(jobAssignmentService, times(2)).save(any(JobAssignment.class));
    }

    @Test
    void reachingTheHeadcountDoesNotCloseTheJob() {
        Job job = job(1, LocalDateTime.now().plusDays(1));

        manager.respondToApplication(applicationFor(job).getId(), accept());

        assertEquals(JobStatus.OPEN, job.getStatus());
    }

    @Test
    void aWorkerCanStillBeHiredAfterTheJobHasStarted() {
        Job job = job(3, LocalDateTime.now().minusHours(4));

        assertDoesNotThrow(() -> manager.respondToApplication(applicationFor(job).getId(), accept()));

        verify(jobAssignmentService).save(any(JobAssignment.class));
    }

    @Test
    void acceptingAnApplicationCopiesThePublishedPriceToTheAssignment() {
        Job job = job(1, LocalDateTime.now().plusDays(1));
        JobApplication application = applicationFor(job);

        manager.respondToApplication(application.getId(), accept());

        var assignmentCaptor = org.mockito.ArgumentCaptor.forClass(JobAssignment.class);
        verify(jobAssignmentService).save(assignmentCaptor.capture());
        verify(pricingService).copy(job, assignmentCaptor.getValue());
    }
}
