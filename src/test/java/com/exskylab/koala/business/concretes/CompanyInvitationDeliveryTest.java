package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.CompanyContactService;
import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.business.abstracts.SecurityService;
import com.exskylab.koala.core.dtos.notification.request.SendEmailDto;
import com.exskylab.koala.core.dtos.notification.request.SendInAppDto;
import com.exskylab.koala.core.helpers.MessageResolver;
import com.exskylab.koala.core.mappers.CompanyContactInvitationMapper;
import com.exskylab.koala.dataAccess.CompanyContactInvitationDao;
import com.exskylab.koala.entities.Company;
import com.exskylab.koala.entities.CompanyContactInvitation;
import com.exskylab.koala.entities.CompanyContactInvitationStatus;
import com.exskylab.koala.entities.CompanyContactRole;
import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.NotificationTargetType;
import com.exskylab.koala.entities.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanyInvitationDeliveryTest {

    private CompanyContactInvitationDao invitationDao;
    private NotificationService notificationService;
    private CompanyContactInvitationManager manager;

    private Company company;
    private User invitedBy;
    private User invitedUser;
    private CompanyContactInvitation invitation;

    @BeforeEach
    void setUp() {
        invitationDao = mock(CompanyContactInvitationDao.class);
        notificationService = mock(NotificationService.class);

        MessageResolver messageResolver = mock(MessageResolver.class);
        when(messageResolver.resolve(any())).thenAnswer(call -> "metin:" + call.getArgument(0));

        manager = new CompanyContactInvitationManager(invitationDao, notificationService,
                mock(SecurityService.class), mock(CompanyContactService.class),
                mock(CompanyContactInvitationMapper.class), messageResolver);

        company = new Company();
        company.setId(UUID.randomUUID());
        company.setName("Koala AŞ");

        invitedBy = user();
        invitedUser = user();

        invitation = new CompanyContactInvitation();
        invitation.setId(UUID.randomUUID());
        invitation.setCompany(company);
        invitation.setInvitedBy(invitedBy);
        invitation.setInvitedUser(invitedUser);
        invitation.setRole(CompanyContactRole.STAFF);
        invitation.setStatus(CompanyContactInvitationStatus.PENDING);

        when(invitationDao.save(any())).thenAnswer(call -> {
            CompanyContactInvitation saved = call.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(invitation.getId());
            }
            return saved;
        });
        when(invitationDao.findById(invitation.getId())).thenReturn(Optional.of(invitation));
    }

    private static User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFirstName("Ayşe");
        user.setLastName("Yılmaz");
        user.setEmail("ayse.yilmaz" + UUID.randomUUID() + "@ornek.test");
        return user;
    }

    private void invite() {
        manager.inviteUserToCompany(invitedBy, invitedUser, company, CompanyContactRole.STAFF);
    }

    private SendInAppDto recordedFeedEntry() {
        ArgumentCaptor<SendInAppDto> captor = ArgumentCaptor.forClass(SendInAppDto.class);
        verify(notificationService).sendInApp(captor.capture());
        return captor.getValue();
    }

    private List<SendEmailDto> queuedEmails() {
        ArgumentCaptor<SendEmailDto> captor = ArgumentCaptor.forClass(SendEmailDto.class);
        verify(notificationService, atLeastOnce()).sendEmail(captor.capture(), any(), anyBoolean());
        return captor.getAllValues();
    }

    @Test
    void theInvitationReachesTheInvitedUsersNotificationFeed() {
        invite();

        SendInAppDto entry = recordedFeedEntry();
        assertEquals(invitedUser.getId(), entry.getRecipient().getId());
        assertEquals(NotificationCategory.COMPANY_UPDATES, entry.getCategory());
        assertEquals(company.getName(), entry.getSubject());
        assertEquals(NotificationTargetType.COMPANY_CONTACT_INVITATION, entry.getTargetType());
        assertEquals(invitation.getId(), entry.getTargetId());
        assertNotNull(entry.getBody());
        assertFalse(entry.getBody().isBlank());
    }

    @Test
    void aFeedEntryThatCannotBeWrittenLosesNeitherTheInvitationNorTheEmail() {
        doThrow(new RuntimeException("bildirim yazilamadi")).when(notificationService).sendInApp(any());

        CompanyContactInvitation saved =
                manager.inviteUserToCompany(invitedBy, invitedUser, company, CompanyContactRole.STAFF);

        assertNotNull(saved.getId());
        verify(invitationDao).save(any());
        assertEquals(1, queuedEmails().size());
    }

    @Test
    void theInvitationEmailCarriesASubject() {
        invite();

        SendEmailDto email = queuedEmails().getFirst();
        assertNotNull(email.getSubject());
        assertFalse(email.getSubject().isBlank());
    }

    @Test
    void bothReceiptsOfAnAcceptedInvitationCarryTheirOwnSubjects() {
        answerAs(true);

        List<SendEmailDto> emails = queuedEmails();
        assertEquals(2, emails.size());
        for (SendEmailDto email : emails) {
            assertNotNull(email.getSubject());
            assertFalse(email.getSubject().isBlank());
        }
        assertNotEquals(emails.get(0).getSubject(), emails.get(1).getSubject());
    }

    @Test
    void bothReceiptsOfARejectedInvitationCarryTheirOwnSubjects() {
        answerAs(false);

        List<SendEmailDto> emails = queuedEmails();
        assertEquals(2, emails.size());
        for (SendEmailDto email : emails) {
            assertNotNull(email.getSubject());
            assertFalse(email.getSubject().isBlank());
        }
    }

    private void answerAs(boolean accepted) {
        SecurityService securityService = mock(SecurityService.class);
        when(securityService.getAuthenticatedUser()).thenReturn(invitedUser);

        MessageResolver messageResolver = mock(MessageResolver.class);
        when(messageResolver.resolve(any())).thenAnswer(call -> "metin:" + call.getArgument(0));

        manager = new CompanyContactInvitationManager(invitationDao, notificationService, securityService,
                mock(CompanyContactService.class), mock(CompanyContactInvitationMapper.class), messageResolver);

        manager.answerToInvitation(invitation.getId(), accepted);
    }
}
