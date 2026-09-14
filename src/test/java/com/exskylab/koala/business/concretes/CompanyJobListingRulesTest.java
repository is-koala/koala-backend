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
import com.exskylab.koala.core.dtos.common.PageDto;
import com.exskylab.koala.core.dtos.job.response.JobSummaryDto;
import com.exskylab.koala.core.exceptions.ForbiddenException;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.helpers.MessageResolver;
import com.exskylab.koala.core.helpers.JobAvailability;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.core.mappers.JobMapper;
import com.exskylab.koala.dataAccess.JobApplicationDao;
import com.exskylab.koala.dataAccess.JobAssignmentDao;
import com.exskylab.koala.dataAccess.JobDao;
import com.exskylab.koala.entities.Address;
import com.exskylab.koala.entities.AddressPrecision;
import com.exskylab.koala.entities.Company;
import com.exskylab.koala.entities.Job;
import com.exskylab.koala.entities.JobEmployerType;
import com.exskylab.koala.entities.User;
import com.exskylab.koala.support.FallbackSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanyJobListingRulesTest {

    private JobDao jobDao;
    private SecurityService securityService;
    private CompanyService companyService;
    private CompanyContactService companyContactService;
    private JobAssignmentDao jobAssignmentDao;
    private JobMapper jobMapper;
    private JobManager jobManager;

    private final UUID companyId = UUID.randomUUID();
    private final UUID firstContactId = UUID.randomUUID();
    private final UUID secondContactId = UUID.randomUUID();
    private final UUID strangerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jobDao = mock(JobDao.class);
        securityService = mock(SecurityService.class);
        companyService = mock(CompanyService.class);
        companyContactService = mock(CompanyContactService.class);
        jobAssignmentDao = mock(JobAssignmentDao.class);
        jobMapper = mock(JobMapper.class);

        jobManager = new JobManager(jobDao, securityService, mock(JobCategoryService.class),
                mock(AddressService.class), companyService, jobMapper, new PageableSanitizer(),
                companyContactService, mock(JobAlertService.class), mock(GalleryService.class),
                mock(CompanyFollowService.class), mock(JobApplicationDao.class), jobAssignmentDao,
                mock(NotificationService.class), mock(MessageResolver.class),
                new PricingManager(FallbackSettings.commissionCalculator()), mock(JobAvailability.class),
                mock(com.exskylab.koala.core.mappers.AdminDetailMapper.class));

        when(jobMapper.toJobSummaryDto(any(Job.class))).thenAnswer(invocation -> unprotected(invocation.getArgument(0)));
    }

    @Test
    void theSecondContactSeesTheJobTheFirstContactOpened() {
        Job job = companyJob(false);
        assertNotEquals(secondContactId, job.getCreator().getId(),
                "Kurgu ancak ilani baskasi actiysa sorulan soruyu olcer");
        viewerIsAContact(secondContactId);
        when(jobDao.findAllByCompanyId(eq(companyId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(job)));

        PageDto<JobSummaryDto> page = jobManager.getJobsOfCompany(companyId, PageRequest.of(0, 20));

        assertEquals(List.of(job.getId()), page.content().stream().map(JobSummaryDto::getId).toList());
        assertEquals(1, page.totalElements());
        verify(jobDao, never()).findAllByCreatorId(any(), any());
    }

    @Test
    void aCompanyThatDoesNotExistIsNotFound() {
        when(securityService.getAuthenticatedUserId()).thenReturn(strangerId);
        when(companyService.getCompanyById(companyId))
                .thenThrow(new ResourceNotFoundException("{company.not.found}"));

        ResourceNotFoundException thrown = assertThrows(ResourceNotFoundException.class,
                () -> jobManager.getJobsOfCompany(companyId, PageRequest.of(0, 20)));

        assertEquals(HttpStatus.NOT_FOUND, thrown.getHttpStatus());
        assertEquals("COMPANY_NOT_FOUND", thrown.getErrorCode());
        verify(companyContactService, never()).isUserAContactOfCompany(any(), any());
        verify(jobDao, never()).findAllByCompanyId(any(), any());
    }

    @Test
    void aUserWhoIsNotAContactOfAnExistingCompanyIsForbidden() {
        when(securityService.getAuthenticatedUserId()).thenReturn(strangerId);
        when(companyContactService.isUserAContactOfCompany(companyId, strangerId)).thenReturn(false);

        ForbiddenException thrown = assertThrows(ForbiddenException.class,
                () -> jobManager.getJobsOfCompany(companyId, PageRequest.of(0, 20)));

        assertEquals(HttpStatus.FORBIDDEN, thrown.getHttpStatus());
        assertEquals("JOB_USER_IS_NOT_A_CONTACT_OF_COMPANY", thrown.getErrorCode());
        verify(jobDao, never()).findAllByCompanyId(any(), any());
    }

    @Test
    void aSortFieldOutsideTheWhitelistIsRejected() {
        viewerIsAContact(secondContactId);

        ValidationException thrown = assertThrows(ValidationException.class,
                () -> jobManager.getJobsOfCompany(companyId, PageRequest.of(0, 20, Sort.by("creator"))));

        assertEquals(HttpStatus.BAD_REQUEST, thrown.getHttpStatus());
        assertEquals("PAGE_SORT_FIELD_NOT_ALLOWED", thrown.getErrorCode());
        verify(jobDao, never()).findAllByCompanyId(any(), any());
    }

    @Test
    void aWhitelistedSortFieldReachesTheQuery() {
        viewerIsAContact(secondContactId);
        when(jobDao.findAllByCompanyId(eq(companyId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        jobManager.getJobsOfCompany(companyId, PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "salary")));

        ArgumentCaptor<Pageable> asked = ArgumentCaptor.forClass(Pageable.class);
        verify(jobDao).findAllByCompanyId(eq(companyId), asked.capture());
        assertEquals(Sort.by(Sort.Direction.ASC, "salary"), asked.getValue().getSort());
    }

    @Test
    void aPageSizeAboveTheCeilingIsCappedBeforeTheQuery() {
        viewerIsAContact(secondContactId);
        when(jobDao.findAllByCompanyId(eq(companyId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        jobManager.getJobsOfCompany(companyId, PageRequest.of(0, 5000));

        ArgumentCaptor<Pageable> asked = ArgumentCaptor.forClass(Pageable.class);
        verify(jobDao).findAllByCompanyId(eq(companyId), asked.capture());
        assertEquals(100, asked.getValue().getPageSize());
    }

    @Test
    void theSameJobIsExactForTheContactWhoOpenedItAndApproximateForTheOther() {
        Job job = companyJob(false);
        when(jobDao.findAllByCompanyId(eq(companyId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(job)));

        viewerIsAContact(firstContactId);
        JobSummaryDto forTheOneWhoOpenedIt = firstListedJob();

        assertEquals(AddressPrecision.EXACT, forTheOneWhoOpenedIt.getAddressPrecision());
        assertEquals(40.987654, forTheOneWhoOpenedIt.getLatitude());
        assertEquals(29.034567, forTheOneWhoOpenedIt.getLongitude());

        viewerIsAContact(secondContactId);
        JobSummaryDto forTheOtherContact = firstListedJob();

        assertEquals(AddressPrecision.APPROXIMATE, forTheOtherContact.getAddressPrecision());
        assertNull(forTheOtherContact.getLatitude(),
                "Yaklasik dalda koordinati mapper yazar, protect ona dokunmamali");
        assertNull(forTheOtherContact.getLongitude(),
                "Yaklasik dalda koordinati mapper yazar, protect ona dokunmamali");
    }

    @Test
    void aCompanyJobWithAPublishedAddressReturnsTheExactCoordinates() {
        Job job = companyJob(true);
        viewerIsAContact(secondContactId);
        when(jobDao.findAllByCompanyId(eq(companyId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(job)));

        JobSummaryDto summary = firstListedJob();

        assertEquals(AddressPrecision.EXACT, summary.getAddressPrecision());
        assertEquals(40.987654, summary.getLatitude());
        assertEquals(29.034567, summary.getLongitude());
    }

    @Test
    void aContactAssignedToTheJobSeesTheExactAddressHereToo() {
        Job job = companyJob(false);
        viewerIsAContact(secondContactId);
        when(jobDao.findAllByCompanyId(eq(companyId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(job)));
        when(jobAssignmentDao.findVisibleAddressJobIds(anyCollection(), eq(secondContactId), anyCollection()))
                .thenReturn(List.of(job.getId()));

        JobSummaryDto summary = firstListedJob();

        assertEquals(AddressPrecision.EXACT, summary.getAddressPrecision());
        assertEquals(40.987654, summary.getLatitude());
        assertEquals(29.034567, summary.getLongitude());
    }

    private JobSummaryDto firstListedJob() {
        return jobManager.getJobsOfCompany(companyId, PageRequest.of(0, 20)).content().getFirst();
    }

    private void viewerIsAContact(UUID viewerId) {
        when(securityService.getAuthenticatedUserId()).thenReturn(viewerId);
        when(companyContactService.isUserAContactOfCompany(companyId, viewerId)).thenReturn(true);
    }

    private Job companyJob(boolean addressPublic) {
        Company company = new Company();
        company.setId(companyId);

        User creator = new User();
        creator.setId(firstContactId);

        Address address = new Address();
        address.setId(UUID.randomUUID());
        address.setLatitude(new BigDecimal("40.987654"));
        address.setLongitude(new BigDecimal("29.034567"));

        Job job = new Job();
        job.setId(UUID.randomUUID());
        job.setEmployerType(JobEmployerType.COMPANY);
        job.setCompany(company);
        job.setCreator(creator);
        job.setAddress(address);
        job.setAddressPublic(addressPublic);
        return job;
    }

    private static JobSummaryDto unprotected(Job job) {
        JobSummaryDto summary = new JobSummaryDto();
        summary.setId(job.getId());
        return summary;
    }
}
