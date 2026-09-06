package com.exskylab.koala.business.concretes;

import com.exskylab.koala.support.FallbackSettings;
import com.exskylab.koala.business.abstracts.*;
import com.exskylab.koala.core.dtos.address.request.AddressWriteDto;
import com.exskylab.koala.core.dtos.job.request.CreateIndividualJobDto;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.helpers.MessageResolver;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.core.helpers.CommissionCalculator;
import com.exskylab.koala.core.mappers.JobMapper;
import com.exskylab.koala.core.properties.CommissionProperties;
import com.exskylab.koala.core.properties.JobSearchProperties;
import com.exskylab.koala.dataAccess.JobApplicationDao;
import com.exskylab.koala.dataAccess.JobAssignmentDao;
import com.exskylab.koala.dataAccess.JobDao;
import com.exskylab.koala.entities.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmployerIdentityGateTest {

    private JobDao jobDao;
    private JobManager jobManager;
    private User employer;

    @BeforeEach
    void setUp() {
        jobDao = mock(JobDao.class);

        employer = new User();
        employer.setId(UUID.randomUUID());
        employer.setEmployer(true);
        employer.setIdentityVerified(true);
        employer.setTcIdentityNumber("10000000146");

        SecurityService security = mock(SecurityService.class);
        when(security.getAuthenticatedUser()).thenReturn(employer);

        JobCategoryService categories = mock(JobCategoryService.class);
        JobCategory category = new JobCategory();
        category.setId(UUID.randomUUID());
        category.setRequiresInsurance(false);
        when(categories.getJobCategoryById(any())).thenReturn(category);

        AddressService addresses = mock(AddressService.class);
        Address address = new Address();
        address.setId(UUID.randomUUID());
        when(addresses.createAddress(any())).thenReturn(address);

        jobManager = new JobManager(jobDao, security, categories, addresses, mock(CompanyService.class),
                mock(JobMapper.class), new PageableSanitizer(), mock(CompanyContactService.class),
                mock(JobAlertService.class), mock(GalleryService.class), mock(CompanyFollowService.class),
                mock(JobApplicationDao.class), mock(JobAssignmentDao.class), mock(NotificationService.class),
                mock(MessageResolver.class), new JobSearchProperties(),
                new PricingManager(FallbackSettings.commissionCalculator()));

        when(jobDao.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private static CreateIndividualJobDto jobRequest() {
        CreateIndividualJobDto dto = new CreateIndividualJobDto();
        dto.setCategoryId(UUID.randomUUID().toString());
        dto.setTitle("Garson");
        dto.setDescription("Bir gunluk garsonluk");
        dto.setPosition("Garson");
        dto.setType("ONE_TIME");
        dto.setSalaryType("DAILY");
        dto.setSalary(new BigDecimal("1000.00"));
        dto.setStartDateTime(LocalDateTime.now().plusDays(2));
        dto.setEndDateTime(LocalDateTime.now().plusDays(2).plusHours(8));
        dto.setMinAge(18);
        dto.setMaxAge(60);
        dto.setExperienceRequired(false);
        dto.setAddress(new AddressWriteDto());
        return dto;
    }

    @Test
    void aVerifiedIndividualEmployerCanOpenAJob() {
        assertDoesNotThrow(() -> jobManager.createIndividualJob(jobRequest()));
        var jobCaptor = org.mockito.ArgumentCaptor.forClass(Job.class);
        verify(jobDao).save(jobCaptor.capture());
        Job saved = jobCaptor.getValue();
        assertEquals("2026-09-v1", saved.getPricingVersion());
        assertEquals(new BigDecimal("75.00"), saved.getCommissionAmount());
        assertEquals(new BigDecimal("1075.00"), saved.getTotalCharged());
    }

    @Test
    void anUnverifiedIndividualEmployerCannotOpenAJob() {
        employer.setIdentityVerified(false);

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> jobManager.createIndividualJob(jobRequest()));

        assertEquals("JOB_INDIVIDUAL_EMPLOYER_IDENTITY_NOT_VERIFIED", thrown.getErrorCode());
        verify(jobDao, never()).save(any());
    }

    @Test
    void theIdentityGateIsCheckedBeforeAnyAddressIsCreated() {
        employer.setIdentityVerified(false);
        AddressService addresses = mock(AddressService.class);

        assertThrows(ValidationException.class, () -> jobManager.createIndividualJob(jobRequest()));

        verifyNoInteractions(addresses);
    }
}
