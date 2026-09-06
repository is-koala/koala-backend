package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.exceptions.ResourceNotFoundException;
import com.exskylab.koala.core.helpers.MessageResolver;
import com.exskylab.koala.core.mappers.CompanyContactInvitationMapper;
import com.exskylab.koala.dataAccess.CompanyContactInvitationDao;
import com.exskylab.koala.entities.Company;
import com.exskylab.koala.entities.CompanyContactInvitation;
import com.exskylab.koala.entities.CompanyContactInvitationStatus;
import com.exskylab.koala.entities.CompanyContactRole;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompanyContactInvitationRulesTest {

    private CompanyContactInvitationDao invitationDao;
    private SecurityService securityService;
    private CompanyContactService companyContactService;
    private CompanyContactInvitationManager manager;

    private Company company;
    private User invitedUser;
    private User invitedBy;
    private User companyAdmin;
    private User stranger;
    private CompanyContactInvitation invitation;

    @BeforeEach
    void setUp() {
        invitationDao = mock(CompanyContactInvitationDao.class);
        securityService = mock(SecurityService.class);
        companyContactService = mock(CompanyContactService.class);

        manager = new CompanyContactInvitationManager(invitationDao, mock(NotificationService.class),
                securityService, companyContactService, mock(CompanyContactInvitationMapper.class),
                mock(MessageResolver.class));

        company = new Company();
        company.setId(UUID.randomUUID());
        company.setName("Koala AŞ");

        invitedUser = user();
        invitedBy = user();
        companyAdmin = user();
        stranger = user();

        invitation = new CompanyContactInvitation();
        invitation.setId(UUID.randomUUID());
        invitation.setCompany(company);
        invitation.setInvitedBy(invitedBy);
        invitation.setInvitedUser(invitedUser);
        invitation.setRole(CompanyContactRole.STAFF);
        invitation.setStatus(CompanyContactInvitationStatus.PENDING);

        when(invitationDao.findById(invitation.getId())).thenReturn(Optional.of(invitation));
        when(invitationDao.findByCompanyId(company.getId())).thenReturn(List.of(invitation));
        when(companyContactService.isUserAdminOfCompany(company.getId(), companyAdmin.getId())).thenReturn(true);
    }

    private static User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFirstName("Ayşe");
        user.setLastName("Yılmaz");
        user.setEmail("ayse.yilmaz@ornek.test");
        return user;
    }

    private void authenticatedAs(User user) {
        when(securityService.getAuthenticatedUser()).thenReturn(user);
    }

    @Test
    void theCompanyAdminCanListTheInvitations() {
        authenticatedAs(companyAdmin);

        assertDoesNotThrow(() -> manager.getCompanyContactInvitationsByCompanyIdDto(company.getId()));
    }

    @Test
    void anOutsiderCannotListTheInvitations() {
        authenticatedAs(stranger);

        assertThrows(ResourceNotFoundException.class,
                () -> manager.getCompanyContactInvitationsByCompanyIdDto(company.getId()));
    }

    @Test
    void theInvitedUserCanReadTheInvitation() {
        authenticatedAs(invitedUser);

        assertDoesNotThrow(() -> manager.getInvitationById(invitation.getId()));
    }

    @Test
    void theInviterCanReadTheInvitation() {
        authenticatedAs(invitedBy);

        assertDoesNotThrow(() -> manager.getInvitationById(invitation.getId()));
    }

    @Test
    void aCompanyAdminCanReadTheInvitation() {
        authenticatedAs(companyAdmin);

        assertDoesNotThrow(() -> manager.getInvitationById(invitation.getId()));
    }

    @Test
    void aStrangerHoldingTheIdCannotReadTheInvitation() {
        authenticatedAs(stranger);

        assertThrows(ResourceNotFoundException.class, () -> manager.getInvitationById(invitation.getId()));
    }

    @Test
    void aStrangerIsRefusedBeforeTheInvitationStatusIsConsulted() {
        invitation.setStatus(CompanyContactInvitationStatus.ACCEPTED);
        authenticatedAs(stranger);

        assertThrows(ResourceNotFoundException.class, () -> manager.getInvitationById(invitation.getId()));
    }
}
