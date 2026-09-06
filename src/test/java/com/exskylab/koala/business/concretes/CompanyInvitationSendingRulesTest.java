package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.ActivityNotificationService;
import com.exskylab.koala.business.abstracts.AddressService;
import com.exskylab.koala.business.abstracts.CompanyContactInvitationService;
import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.business.abstracts.CompanyFollowService;
import com.exskylab.koala.business.abstracts.GalleryService;
import com.exskylab.koala.business.abstracts.ImageService;
import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.business.abstracts.UserService;
import com.exskylab.koala.core.dtos.companyContact.request.InviteContactToCompanyDto;
import com.exskylab.koala.core.exceptions.ConflictException;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.PageableSanitizer;
import com.exskylab.koala.core.mappers.CompanyMapper;
import com.exskylab.koala.dataAccess.CompanyDao;
import com.exskylab.koala.entities.Company;
import com.exskylab.koala.entities.CompanyContact;
import com.exskylab.koala.entities.CompanyContactRole;
import com.exskylab.koala.entities.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanyInvitationSendingRulesTest {

    private static final String INVITEE_TC = "10000000146";

    private CompanyDao companyDao;
    private UserService userService;
    private CompanyContactInvitationService invitationService;
    private SecurityService securityService;
    private CompanyContactService companyContactService;
    private CompanyManager manager;

    private Company company;
    private User companyAdmin;
    private User invitee;

    @BeforeEach
    void setUp() {
        companyDao = mock(CompanyDao.class);
        userService = mock(UserService.class);
        invitationService = mock(CompanyContactInvitationService.class);
        securityService = mock(SecurityService.class);
        companyContactService = mock(CompanyContactService.class);

        manager = new CompanyManager(companyDao, userService, mock(NotificationService.class),
                mock(ImageService.class), invitationService, securityService,
                mock(AddressService.class), mock(EntityManager.class), mock(CompanyMapper.class),
                companyContactService, mock(GalleryService.class),
                mock(CompanyFollowService.class), new PageableSanitizer(),
                mock(ActivityNotificationService.class));

        companyAdmin = user();
        invitee = user();
        invitee.setTcIdentityNumber(INVITEE_TC);

        company = new Company();
        company.setId(UUID.randomUUID());
        company.setName("Koala AŞ");
        company.setContacts(new ArrayList<>());
        company.getContacts().add(contact(companyAdmin, CompanyContactRole.ADMIN));

        when(companyDao.findById(company.getId())).thenReturn(Optional.of(company));
        when(securityService.getAuthenticatedUser()).thenReturn(companyAdmin);
        when(securityService.getAuthenticatedUserId()).thenReturn(companyAdmin.getId());
        when(companyContactService.isUserAdminOfCompany(company.getId(), companyAdmin.getId())).thenReturn(true);
        when(userService.getByEmail(invitee.getEmail())).thenReturn(invitee);
    }

    private static User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFirstName("Ayşe");
        user.setLastName("Yılmaz");
        user.setEmail("ayse.yilmaz" + UUID.randomUUID() + "@ornek.test");
        return user;
    }

    private CompanyContact contact(User user, CompanyContactRole role) {
        CompanyContact contact = new CompanyContact();
        contact.setId(UUID.randomUUID());
        contact.setUser(user);
        contact.setRole(role);
        return contact;
    }

    private InviteContactToCompanyDto invite() {
        InviteContactToCompanyDto dto = new InviteContactToCompanyDto();
        dto.setEmail(invitee.getEmail());
        dto.setTcIdentityNumber(INVITEE_TC);
        dto.setRole(CompanyContactRole.STAFF.name());
        return dto;
    }

    @Test
    void aFirstInvitationIsSent() {
        manager.inviteContactToCompany(company.getId(), invite());

        verify(invitationService).inviteUserToCompany(companyAdmin, invitee, company, CompanyContactRole.STAFF);
    }

    @Test
    void invitingSomeoneWhoIsAlreadyAContactIsAConflict() {
        company.getContacts().add(contact(invitee, CompanyContactRole.STAFF));

        assertThrows(ConflictException.class,
                () -> manager.inviteContactToCompany(company.getId(), invite()));
        verify(invitationService, never()).inviteUserToCompany(any(), any(), any(), any());
    }

    @Test
    void aSecondInvitationWhileOneIsStillPendingIsAConflict() {
        when(invitationService.hasPendingInvitation(company.getId(), invitee.getId())).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> manager.inviteContactToCompany(company.getId(), invite()));
        verify(invitationService, never()).inviteUserToCompany(any(), any(), any(), any());
    }

    @Test
    void anAnsweredInvitationDoesNotBlockANewOne() {
        when(invitationService.hasPendingInvitation(company.getId(), invitee.getId())).thenReturn(false);

        manager.inviteContactToCompany(company.getId(), invite());

        verify(invitationService).inviteUserToCompany(companyAdmin, invitee, company, CompanyContactRole.STAFF);
    }

    @Test
    void aNonAdminCannotTellTheCompanyFromAMissingOne() {
        User outsider = user();
        when(securityService.getAuthenticatedUser()).thenReturn(outsider);
        when(securityService.getAuthenticatedUserId()).thenReturn(outsider.getId());

        assertThrows(ResourceNotFoundException.class,
                () -> manager.inviteContactToCompany(company.getId(), invite()));
        verify(companyDao, never()).findById(any());
        verify(userService, never()).getByEmail(any());
        verify(invitationService, never()).hasPendingInvitation(any(), any());
    }
}
