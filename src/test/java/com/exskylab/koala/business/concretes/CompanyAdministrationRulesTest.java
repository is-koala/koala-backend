package com.exskylab.koala.business.concretes;

import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.business.abstracts.*;
import com.exskylab.koala.core.dtos.company.request.UpdateCompanyRequestDto;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.mappers.CompanyMapper;
import com.exskylab.koala.dataAccess.CompanyDao;
import com.exskylab.koala.entities.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CompanyAdministrationRulesTest {

    private CompanyDao companyDao;
    private CompanyContactService companyContactService;
    private SecurityService securityService;
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

        manager = new CompanyManager(companyDao, mock(UserService.class), mock(NotificationService.class),
                mock(ImageService.class), mock(CompanyContactInvitationService.class), securityService,
                mock(AddressService.class), mock(EntityManager.class), mock(CompanyMapper.class),
                companyContactService, mock(GalleryService.class),
                mock(com.exskylab.koala.business.abstracts.CompanyFollowService.class),
                new com.exskylab.koala.core.helpers.PageableSanitizer());

        company = new Company();
        company.setId(UUID.randomUUID());
        company.setName("Koala AŞ");
        company.setApproved(false);

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

        assertFalse(company.isApproved());
        assertTrue(manager.approveCompany(company.getId()).isApproved());
    }

    @Test
    void anAlreadyApprovedCompanyCannotBeApprovedAgain() {
        when(securityService.getAuthenticatedUserId()).thenReturn(adminId);
        company.setApproved(true);

        assertThrows(ConflictException.class, () -> manager.approveCompany(company.getId()));
    }
}
