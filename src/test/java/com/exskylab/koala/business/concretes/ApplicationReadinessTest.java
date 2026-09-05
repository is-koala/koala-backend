package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.*;
import com.exskylab.koala.core.dtos.jobApplication.request.CreateJobApplicationDto;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.core.mappers.JobApplicationMapper;
import com.exskylab.koala.dataAccess.JobApplicationDao;
import com.exskylab.koala.entities.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ApplicationReadinessTest {

    private JobApplicationDao jobApplicationDao;
    private BankAccountService bankAccountService;
    private JobApplicationManager manager;

    private final User employer = user();
    private User applicant;
    private Job job;

    @BeforeEach
    void setUp() {
        jobApplicationDao = mock(JobApplicationDao.class);
        bankAccountService = mock(BankAccountService.class);

        applicant = user();
        applicant.setIdentityVerified(true);
        applicant.setAddress(new Address());
        when(bankAccountService.findPrimaryIban(applicant.getId()))
                .thenReturn(Optional.of("TR330006100519786457841326"));

        SecurityService security = mock(SecurityService.class);
        when(security.getAuthenticatedUser()).thenReturn(applicant);

        job = new Job();
        job.setId(UUID.randomUUID());
        job.setCreator(employer);
        job.setEmployerType(JobEmployerType.INDIVIDUAL);

        JobService jobService = mock(JobService.class);
        when(jobService.getById(job.getId())).thenReturn(job);

        manager = new JobApplicationManager(jobApplicationDao, jobService, security,
                mock(CompanyContactService.class), mock(JobAssignmentService.class),
                mock(JobApplicationMapper.class), new PageableSanitizer(),
                mock(GalleryService.class), mock(ActivityNotificationService.class),
                bankAccountService, mock(PricingService.class));

        when(jobApplicationDao.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static User user() {
        User u = new User();
        u.setId(UUID.randomUUID());
        return u;
    }

    private void apply() {
        manager.applyToJob(job.getId(), new CreateJobApplicationDto());
    }

    @Test
    void aReadyWorkerCanApply() {
        assertDoesNotThrow(this::apply);
        verify(jobApplicationDao).save(any(JobApplication.class));
    }

    @Test
    void anUnverifiedWorkerCannotApply() {
        applicant.setIdentityVerified(false);

        ValidationException thrown = assertThrows(ValidationException.class, this::apply);

        assertEquals("APPLICANT_IDENTITY_NOT_VERIFIED", thrown.getErrorCode());
        verify(jobApplicationDao, never()).save(any());
    }

    @Test
    void aWorkerWithoutAnAddressCannotApply() {
        applicant.setAddress(null);

        ValidationException thrown = assertThrows(ValidationException.class, this::apply);

        assertEquals("APPLICANT_ADDRESS_REQUIRED", thrown.getErrorCode());
    }

    @Test
    void aWorkerWithoutABankAccountCannotApply() {
        when(bankAccountService.findPrimaryIban(applicant.getId())).thenReturn(Optional.empty());

        ValidationException thrown = assertThrows(ValidationException.class, this::apply);

        assertEquals("APPLICANT_BANK_ACCOUNT_REQUIRED", thrown.getErrorCode());
    }

    @Test
    void aBlankIbanCountsAsMissing() {
        when(bankAccountService.findPrimaryIban(applicant.getId())).thenReturn(Optional.of("   "));

        assertThrows(ValidationException.class, this::apply);
    }

    @Test
    void identityIsCheckedBeforeTheBankAccountSoTheWorkerFixesThingsInOrder() {
        applicant.setIdentityVerified(false);
        when(bankAccountService.findPrimaryIban(applicant.getId())).thenReturn(Optional.empty());

        ValidationException thrown = assertThrows(ValidationException.class, this::apply);

        assertEquals("APPLICANT_IDENTITY_NOT_VERIFIED", thrown.getErrorCode());
    }
}
