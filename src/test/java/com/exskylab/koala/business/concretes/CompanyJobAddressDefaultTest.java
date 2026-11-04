package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.AddressService;
import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.business.abstracts.CompanyFollowService;
import com.exskylab.koala.business.abstracts.CompanyService;
import com.exskylab.koala.business.abstracts.GalleryService;
import com.exskylab.koala.business.abstracts.JobAlertService;
import com.exskylab.koala.business.abstracts.JobCategoryService;
import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.dtos.job.request.CreateCompanyJobDto;
import com.exskylab.koala.core.helpers.JobAvailability;
import com.exskylab.koala.core.helpers.MessageResolver;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.core.mappers.JobMapper;
import com.exskylab.koala.dataAccess.JobApplicationDao;
import com.exskylab.koala.dataAccess.JobAssignmentDao;
import com.exskylab.koala.dataAccess.JobDao;
import com.exskylab.koala.entities.Address;
import com.exskylab.koala.entities.Company;
import com.exskylab.koala.entities.CompanyContact;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.JobCategory;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.support.FallbackSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanyJobAddressDefaultTest {

    private final UUID companyId = UUID.randomUUID();
    private JobDao jobDao;
    private JobManager jobManager;

    @BeforeEach
    void setUp() {
        jobDao = mock(JobDao.class);
        SecurityService securityService = mock(SecurityService.class);
        JobCategoryService jobCategoryService = mock(JobCategoryService.class);
        AddressService addressService = mock(AddressService.class);
        CompanyService companyService = mock(CompanyService.class);
        CompanyContactService companyContactService = mock(CompanyContactService.class);

        jobManager = new JobManager(jobDao, securityService, jobCategoryService,
                addressService, companyService, mock(JobMapper.class), new PageableSanitizer(),
                companyContactService, mock(JobAlertService.class), mock(GalleryService.class),
                mock(CompanyFollowService.class), mock(JobApplicationDao.class), mock(JobAssignmentDao.class),
                mock(NotificationService.class), mock(MessageResolver.class),
                new PricingManager(FallbackSettings.commissionCalculator()), mock(JobAvailability.class),
                mock(com.exskylab.koala.core.mappers.AdminDetailMapper.class),
                mock(com.exskylab.koala.business.abstracts.TransportService.class),
                new com.exskylab.koala.core.helpers.JobEditors(companyContactService));

        User author = new User();
        author.setId(UUID.randomUUID());
        CompanyContact contact = new CompanyContact();
        contact.setUser(author);
        Company company = new Company();
        company.setId(companyId);
        company.getContacts().add(contact);

        when(securityService.getAuthenticatedUser()).thenReturn(author);
        when(jobCategoryService.getJobCategoryById(any(UUID.class))).thenReturn(new JobCategory());
        when(addressService.createAddress(any())).thenReturn(new Address());
        when(companyService.getCompanyById(companyId)).thenReturn(company);
        when(jobDao.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void aCompanyJobPublishesItsAddressWhenTheClientSaysNothing() {
        assertTrue(savedAddressPublic(null));
    }

    @Test
    void aCompanyJobPublishesItsAddressWhenAskedTo() {
        assertTrue(savedAddressPublic(Boolean.TRUE));
    }

    @Test
    void aCompanyJobKeepsItsAddressHiddenWhenTheClientTurnsItOff() {
        assertFalse(savedAddressPublic(Boolean.FALSE));
    }

    private boolean savedAddressPublic(Boolean requested) {
        CreateCompanyJobDto dto = new CreateCompanyJobDto();
        dto.setCategoryId(UUID.randomUUID().toString());
        dto.setTitle("Garson");
        dto.setDescription("Akşam servisi");
        dto.setPosition("Garson");
        dto.setType("ONE_TIME");
        dto.setSalaryType("HOURLY");
        dto.setSalary(new BigDecimal("250.00"));
        dto.setStartDateTime(LocalDateTime.of(2027, 1, 10, 18, 0));
        dto.setEndDateTime(LocalDateTime.of(2027, 1, 10, 23, 0));
        dto.setMinAge(18);
        dto.setMaxAge(65);
        dto.setExperienceRequired(false);
        dto.setInsuranceRequired(false);
        dto.setAddressPublic(requested);

        jobManager.createCorporateJob(companyId, dto);

        ArgumentCaptor<Job> saved = ArgumentCaptor.forClass(Job.class);
        verify(jobDao).save(saved.capture());
        return saved.getValue().isAddressPublic();
    }
}
