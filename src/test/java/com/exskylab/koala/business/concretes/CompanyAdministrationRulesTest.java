package com.exskylab.koala.business.concretes;

import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.business.abstracts.*;
import com.exskylab.koala.core.dtos.company.request.UpdateCompanyRequestDto;
import com.exskylab.koala.core.dtos.company.response.CompanyDto;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.exceptions.ValidationException;
import com.exskylab.koala.core.mappers.CompanyMapper;
import com.exskylab.koala.dataAccess.CompanyDao;
import com.exskylab.koala.entities.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CompanyAdministrationRulesTest {

    private CompanyDao companyDao;
    private CompanyContactService companyContactService;
    private SecurityService securityService;
    private ActivityNotificationService activityNotificationService;
    private CompanyMapper companyMapper;
    private CompanyManager manager;

    private Company company;
    private final UUID adminId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();
    private final UUID strangerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        companyDao = mock(CompanyDao.class);
        companyContactService = mock(CompanyContactService.class);
        securityService = mock(SecurityService.class);
        activityNotificationService = mock(ActivityNotificationService.class);
        companyMapper = mock(CompanyMapper.class);

        manager = new CompanyManager(companyDao, mock(UserService.class), mock(NotificationService.class),
                mock(ImageService.class), mock(CompanyContactInvitationService.class), securityService,
                mock(AddressService.class), mock(EntityManager.class), companyMapper,
                companyContactService, mock(GalleryService.class),
                mock(com.exskylab.koala.business.abstracts.CompanyFollowService.class),
                new com.exskylab.koala.core.helpers.PageableSanitizer(), activityNotificationService);

        company = new Company();
        company.setId(UUID.randomUUID());
        company.setName("Koala AŞ");
        company.setApprovalStatus(CompanyApprovalStatus.PENDING);

        when(companyDao.findById(company.getId())).thenReturn(Optional.of(company));
        when(companyDao.save(any())).thenAnswer(i -> i.getArgument(0));

        when(companyContactService.isUserAdminOfCompany(company.getId(), adminId)).thenReturn(true);
        when(companyContactService.isUserAContactOfCompany(company.getId(), adminId)).thenReturn(true);
        when(companyContactService.isUserAContactOfCompany(company.getId(), memberId)).thenReturn(true);
    }

    private UpdateCompanyRequestDto update(String name) {
        UpdateCompanyRequestDto dto = new UpdateCompanyRequestDto();
        dto.setName(name);
        return dto;
    }

    @Test
    void onlyACompanyAdminCanEditTheCompany() {
        when(securityService.getAuthenticatedUserId()).thenReturn(memberId);

        assertThrows(ResourceNotFoundException.class,
                () -> manager.updateCompany(company.getId(), update("Yeni Ad")));
        assertEquals("Koala AŞ", company.getName());
    }

    @Test
    void anAdminCanEditAndUntouchedFieldsStay() {
        when(securityService.getAuthenticatedUserId()).thenReturn(adminId);

        manager.updateCompany(company.getId(), update("Koala Teknoloji AŞ"));

        assertEquals("Koala Teknoloji AŞ", company.getName());
        assertNull(company.getWebsite());
    }

    @Test
    void anOutsiderCannotReadTheContactList() {
        when(securityService.getAuthenticatedUserId()).thenReturn(strangerId);

        assertThrows(ResourceNotFoundException.class, () -> manager.getContacts(company.getId()));
    }

    @Test
    void theLastAdminCannotBeRemoved() {
        when(securityService.getAuthenticatedUserId()).thenReturn(adminId);

        CompanyContact lastAdmin = new CompanyContact();
        lastAdmin.setId(UUID.randomUUID());
        lastAdmin.setRole(CompanyContactRole.ADMIN);
        when(companyContactService.findContact(company.getId(), adminId))
                .thenReturn(Optional.of(lastAdmin));
        when(companyContactService.countAdminsOfCompany(company.getId())).thenReturn(1L);

        assertThrows(ConflictException.class, () -> manager.removeContact(company.getId(), adminId));
        verify(companyContactService, never()).removeContact(any());
    }

    @Test
    void anOrdinaryContactCanBeRemoved() {
        when(securityService.getAuthenticatedUserId()).thenReturn(adminId);

        CompanyContact contact = new CompanyContact();
        contact.setId(UUID.randomUUID());
        contact.setRole(CompanyContactRole.STAFF);
        when(companyContactService.findContact(company.getId(), memberId))
                .thenReturn(Optional.of(contact));

        manager.removeContact(company.getId(), memberId);

        verify(companyContactService).removeContact(contact);
    }

    @Test
    void aCompanyIsCreatedUnapprovedAndAnAdminApprovesIt() {
        when(securityService.getAuthenticatedUserId()).thenReturn(adminId);

        assertFalse(company.getApprovalStatus().isApproved());
        assertTrue(manager.approveCompany(company.getId(), null).getApprovalStatus().isApproved());
    }

    @Test
    void anUnknownSortFieldOnTheCompanyQueueIsRefused() {
        assertThrows(ValidationException.class, () -> manager.searchCompanies(
                CompanyApprovalStatus.PENDING, null,
                PageRequest.of(0, 20, Sort.by("taxNumber"))));

        verify(companyDao, never()).search(any(), any(), any());
    }

    @Test
    void onlyTheCompanysOwnPeopleReadWhyItWasRejected() {
        when(companyMapper.toCompanyDto(company)).thenAnswer(call -> new CompanyDto());
        company.setApprovalStatus(CompanyApprovalStatus.REJECTED);
        company.setApprovalNote("Vergi levhası okunmuyor");

        when(securityService.getAuthenticatedUserId()).thenReturn(memberId);
        assertEquals("Vergi levhası okunmuyor",
                manager.getCompanyDetail(company.getId()).getApprovalNote());

        when(securityService.getAuthenticatedUserId()).thenReturn(strangerId);
        assertNull(manager.getCompanyDetail(company.getId()).getApprovalNote());
    }

    @Test
    void anApprovalTellsTheCompany() {
        when(securityService.getAuthenticatedUserId()).thenReturn(adminId);

        manager.approveCompany(company.getId(), null);

        verify(activityNotificationService).companyApprovalDecided(company);
    }

    @Test
    void aRejectionTellsTheCompany() {
        when(securityService.getAuthenticatedUserId()).thenReturn(adminId);

        manager.rejectCompany(company.getId(), "Vergi levhası okunmuyor");

        verify(activityNotificationService).companyApprovalDecided(company);
    }

    @Test
    void aRefusedDecisionTellsNobody() {
        when(securityService.getAuthenticatedUserId()).thenReturn(adminId);
        company.setApprovalStatus(CompanyApprovalStatus.APPROVED);

        assertThrows(ConflictException.class, () -> manager.approveCompany(company.getId(), null));
        assertThrows(ConflictException.class, () -> manager.rejectCompany(company.getId(), "Şüpheli"));

        verifyNoInteractions(activityNotificationService);
    }

    @Test
    void anApprovalRecordsTheNoteItWasGiven() {
        when(securityService.getAuthenticatedUserId()).thenReturn(adminId);

        Company approved = manager.approveCompany(company.getId(), "  Vergi levhası doğrulandı  ");

        assertEquals(CompanyApprovalStatus.APPROVED, approved.getApprovalStatus());
        assertEquals("Vergi levhası doğrulandı", approved.getApprovalNote());
        assertNotNull(approved.getApprovalDecidedAt());
    }

    @Test
    void anApprovedCompanyCannotBeRejected() {
        when(securityService.getAuthenticatedUserId()).thenReturn(adminId);
        company.setApprovalStatus(CompanyApprovalStatus.APPROVED);

        assertThrows(ConflictException.class, () -> manager.rejectCompany(company.getId(), "Şüpheli"));
        assertEquals(CompanyApprovalStatus.APPROVED, company.getApprovalStatus());
    }

    @Test
    void aRejectedCompanyCannotBeRejectedTwice() {
        when(securityService.getAuthenticatedUserId()).thenReturn(adminId);
        manager.rejectCompany(company.getId(), "Vergi levhası okunmuyor");

        assertThrows(ConflictException.class, () -> manager.rejectCompany(company.getId(), "Yine olmadı"));
        assertEquals("Vergi levhası okunmuyor", company.getApprovalNote());
    }

    @Test
    void aRejectedCompanyCanStillBeApprovedAndTheNoteIsReplaced() {
        when(securityService.getAuthenticatedUserId()).thenReturn(adminId);
        manager.rejectCompany(company.getId(), "Vergi levhası okunmuyor");

        Company approved = manager.approveCompany(company.getId(), "Belgeler yenilendi");

        assertEquals(CompanyApprovalStatus.APPROVED, approved.getApprovalStatus());
        assertEquals("Belgeler yenilendi", approved.getApprovalNote());
    }

    @Test
    void aPendingCompanyIsRejectedWithAReasonAndLeavesThePendingQueue() {
        when(securityService.getAuthenticatedUserId()).thenReturn(adminId);

        Company rejected = manager.rejectCompany(company.getId(), "Vergi levhası okunmuyor");

        assertEquals(CompanyApprovalStatus.REJECTED, rejected.getApprovalStatus());
        assertEquals("Vergi levhası okunmuyor", rejected.getApprovalNote());
        assertNotNull(rejected.getApprovalDecidedAt());
    }

    @Test
    void anAlreadyApprovedCompanyCannotBeApprovedAgain() {
        when(securityService.getAuthenticatedUserId()).thenReturn(adminId);
        company.setApprovalStatus(CompanyApprovalStatus.APPROVED);

        assertThrows(ConflictException.class, () -> manager.approveCompany(company.getId(), null));
    }
}
