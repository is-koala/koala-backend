package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.business.abstracts.JobAssignmentService;
import com.exskylab.koala.business.abstracts.JobService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.helpers.JobAvailability;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.core.mappers.JobApplicationMapper;
import com.exskylab.koala.core.properties.JobSearchProperties;
import com.exskylab.koala.dataAccess.JobApplicationDao;
import com.exskylab.koala.entities.ApplicationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReceivedApplicationsAuthorizationTest {

    private JobApplicationDao jobApplicationDao;
    private SecurityService securityService;
    private CompanyContactService companyContactService;
    private JobApplicationManager manager;

    private final UUID viewerId = UUID.randomUUID();
    private final UUID ownedCompanyId = UUID.randomUUID();
    private final UUID officerCompanyId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jobApplicationDao = mock(JobApplicationDao.class);
        securityService = mock(SecurityService.class);
        companyContactService = mock(CompanyContactService.class);

        manager = new JobApplicationManager(
                jobApplicationDao,
                mock(JobService.class),
                securityService,
                companyContactService,
                mock(JobAssignmentService.class),
                mock(JobApplicationMapper.class),
                new PageableSanitizer(),
                mock(com.exskylab.koala.business.abstracts.GalleryService.class),
                mock(com.exskylab.koala.business.abstracts.ActivityNotificationService.class),
                mock(com.exskylab.koala.business.abstracts.BankAccountService.class),
                mock(com.exskylab.koala.business.abstracts.PricingService.class),
                new JobAvailability(Clock.systemUTC(), new JobSearchProperties()));

        when(securityService.getAuthenticatedUserId()).thenReturn(viewerId);
        when(jobApplicationDao.findReceivedPendingFirst(any(), anyCollection(), any(), any(), any(), any()))
                .thenReturn(Page.empty());
        when(jobApplicationDao.findReceived(any(), anyCollection(), any(), any(), any(), any()))
                .thenReturn(Page.empty());
    }

    @Test
    void theScopeIsTheViewerAsCreatorPlusExactlyTheCompaniesTheContactServiceNames() {
        when(companyContactService.getCompanyIdsOfContact(viewerId))
                .thenReturn(List.of(ownedCompanyId, officerCompanyId));

        manager.getReceivedApplications(null, null, null, PageRequest.of(0, 20));

        ArgumentCaptor<Collection<UUID>> companies = ArgumentCaptor.captor();
        verify(jobApplicationDao).findReceivedPendingFirst(eq(viewerId), companies.capture(),
                eq(null), eq(null), eq(null), any());
        assertEquals(List.of(ownedCompanyId, officerCompanyId), List.copyOf(companies.getValue()));
    }

    @Test
    void aStrangerWithNoMembershipIsScopedToNoCompanyAtAll() {
        when(companyContactService.getCompanyIdsOfContact(viewerId)).thenReturn(List.of());

        manager.getReceivedApplications(null, null, null, PageRequest.of(0, 20));

        ArgumentCaptor<Collection<UUID>> companies = ArgumentCaptor.captor();
        verify(jobApplicationDao).findReceivedPendingFirst(eq(viewerId), companies.capture(),
                any(), any(), any(), any());
        assertTrue(companies.getValue().isEmpty());
        verify(companyContactService, never()).isUserAContactOfCompany(any(), any());
    }

    @Test
    void theCompanyFilterNeverWidensTheScope() {
        UUID foreignCompanyId = UUID.randomUUID();
        when(companyContactService.getCompanyIdsOfContact(viewerId)).thenReturn(List.of(ownedCompanyId));

        manager.getReceivedApplications(null, null, foreignCompanyId, PageRequest.of(0, 20));

        ArgumentCaptor<Collection<UUID>> companies = ArgumentCaptor.captor();
        verify(jobApplicationDao).findReceivedPendingFirst(eq(viewerId), companies.capture(),
                eq(null), eq(null), eq(foreignCompanyId), any());
        assertEquals(List.of(ownedCompanyId), List.copyOf(companies.getValue()));
    }

    @Test
    void theStatusAndJobFiltersReachTheQueryUnchanged() {
        UUID jobId = UUID.randomUUID();
        when(companyContactService.getCompanyIdsOfContact(viewerId)).thenReturn(List.of());

        manager.getReceivedApplications(ApplicationStatus.APPLIED, jobId, null, PageRequest.of(0, 20));

        verify(jobApplicationDao).findReceivedPendingFirst(eq(viewerId), anyCollection(),
                eq(ApplicationStatus.APPLIED), eq(jobId), eq(null), any());
    }

    @Test
    void withoutAClientSortThePendingFirstQueryRunsUnsorted() {
        when(companyContactService.getCompanyIdsOfContact(viewerId)).thenReturn(List.of());

        manager.getReceivedApplications(null, null, null, PageRequest.of(1, 5));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.captor();
        verify(jobApplicationDao).findReceivedPendingFirst(any(), anyCollection(), any(), any(), any(),
                pageable.capture());
        assertTrue(pageable.getValue().getSort().isUnsorted());
        assertEquals(1, pageable.getValue().getPageNumber());
        assertEquals(5, pageable.getValue().getPageSize());
        verify(jobApplicationDao, never()).findReceived(any(), anyCollection(), any(), any(), any(), any());
    }

    @Test
    void aClientSortReplacesTheDefaultAndGetsAStableTieBreaker() {
        when(companyContactService.getCompanyIdsOfContact(viewerId)).thenReturn(List.of());

        manager.getReceivedApplications(null, null, null,
                PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "applicationDateTime")));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.captor();
        verify(jobApplicationDao).findReceived(eq(viewerId), anyCollection(), any(), any(), any(),
                pageable.capture());
        assertEquals(Sort.by(Sort.Direction.ASC, "applicationDateTime")
                .and(Sort.by(Sort.Direction.DESC, "id")), pageable.getValue().getSort());
        verify(jobApplicationDao, never())
                .findReceivedPendingFirst(any(), anyCollection(), any(), any(), any(), any());
    }

    @Test
    void anUnknownSortFieldIsRejectedBeforeAnyQueryRuns() {
        assertThrows(ValidationException.class, () -> manager.getReceivedApplications(null, null, null,
                PageRequest.of(0, 20, Sort.by("notes"))));

        verify(companyContactService, never()).getCompanyIdsOfContact(any());
        verify(jobApplicationDao, never()).findReceived(any(), anyCollection(), any(), any(), any(), any());
        verify(jobApplicationDao, never())
                .findReceivedPendingFirst(any(), anyCollection(), any(), any(), any(), any());
    }
}
