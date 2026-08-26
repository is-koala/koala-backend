package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.*;
import com.exskylab.koala.core.constants.CompanyContactInvitationMessages;
import com.exskylab.koala.core.constants.CompanyMessages;
import com.exskylab.koala.core.dtos.companyContactInvitation.response.CompanyContactInvitationDto;
import com.exskylab.koala.core.dtos.companyContactInvitation.response.GetCompanyContactInvitationDto;
import com.exskylab.koala.core.dtos.notification.request.SendEmailDto;
import com.exskylab.koala.core.exceptions.*;
import com.exskylab.koala.core.mappers.CompanyContactInvitationMapper;
import com.exskylab.koala.dataAccess.CompanyContactInvitationDao;
import com.exskylab.koala.entities.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CompanyContactInvitationManager implements CompanyContactInvitationService {


    private final CompanyContactInvitationDao companyContactInvitationDao;
    private final NotificationService notificationService;
    private final SecurityService securityService;
    private final CompanyContactService companyContactService;

    private final Logger logger = LoggerFactory.getLogger(CompanyContactInvitationManager.class);
    private final CompanyContactInvitationMapper companyContactInvitationMapper;

    public CompanyContactInvitationManager(CompanyContactInvitationDao companyContactInvitationDao,
                                           NotificationService notificationService, SecurityService securityService,
                                           CompanyContactService companyContactService, CompanyContactInvitationMapper companyContactInvitationMapper) {
        this.companyContactInvitationDao = companyContactInvitationDao;
        this.notificationService = notificationService;
        this.securityService = securityService;
        this.companyContactService = companyContactService;
        this.companyContactInvitationMapper = companyContactInvitationMapper;
    }


    @Override
    public CompanyContactInvitation inviteUserToCompany(User invitedBy, User userToInvite, Company company, CompanyContactRole role) {
        logger.info("Inviting user with id: {} to company with id: {}", userToInvite.getId(), company.getId());
        CompanyContactInvitation invitation = new CompanyContactInvitation();
        invitation.setCompany(company);
        invitation.setInvitedBy(invitedBy);
        invitation.setInvitedUser(userToInvite);
        invitation.setRole(role);
        invitation.setStatus(CompanyContactInvitationStatus.PENDING);
        invitation.setInvitedAt(LocalDateTime.now());
        invitation.setAnsweredAt(null);
        CompanyContactInvitation savedInvitation = companyContactInvitationDao.save(invitation);

        logger.info("Saved invitation with id: {}", savedInvitation.getId());

        SendEmailDto sendEmailDto = new SendEmailDto();
        sendEmailDto.setRecipient(userToInvite);
        sendEmailDto.setCategory(NotificationCategory.ACCOUNT_SECURITY);
        sendEmailDto.setDestinationEmail(userToInvite.getEmail());

        sendEmailDto.setTemplateName("company-invitation-template");
        sendEmailDto.setTemplateParameters(Map.of(
                "firstName", userToInvite.getFirstName(),
                "lastName", userToInvite.getLastName(),
                "companyName", company.getName(),
                "invitedByFirstName", invitedBy.getFirstName(),
                "invitedByLastName", invitedBy.getLastName(),
                "role", role.name()
        ));
        logger.info("Sending notification to user with id: {} about the invitation.", userToInvite.getId());

        notificationService.sendEmail(sendEmailDto, DispatchPriority.CRITICAL, true);

        return savedInvitation;
    }

    @Override
    public List<CompanyContactInvitation> getInvitationsByCompanyId(UUID companyId) {
        logger.info("Getting invitations for company with id: {}", companyId);
        return companyContactInvitationDao.findByCompanyId(companyId);
    }

    @Override
    public GetCompanyContactInvitationDto answerToInvitation(UUID invitationId, boolean accepted) {
        logger.info("Answering invitation with id: {} to accepted: {}", invitationId, accepted);
        CompanyContactInvitation invitation = companyContactInvitationDao.findById(invitationId)
                .orElseThrow(() -> new CompanyContactInvitationNotFoundException(CompanyContactInvitationMessages.COMPANY_CONTACT_INVITATION_NOT_FOUND));

        var currentUser = securityService.getAuthenticatedUserFromContext();

        if (!invitation.getInvitedUser().getId().equals(currentUser.getId())){
            throw new UserNotAssociatedWithInvitationException(CompanyContactInvitationMessages.USER_NOT_ASSOCIATED_WITH_INVITATION);
        }

        if (invitation.getStatus() != CompanyContactInvitationStatus.PENDING) {
            logger.warn("Invitation with id: {} has already been answered.", invitationId);
            throw new InvitationAlreadyAnsweredException(CompanyContactInvitationMessages.INVITATION_ALREADY_ANSWERED);
        }

        invitation.setStatus(accepted ? CompanyContactInvitationStatus.ACCEPTED : CompanyContactInvitationStatus.REJECTED);

        invitation.setAnsweredAt(LocalDateTime.now());

        invitation = companyContactInvitationDao.save(invitation);

        if (accepted){
            logger.info("Invitation with id: {} accepted. Adding user to company contacts.", invitationId);
            companyContactService.addCompanyContact(invitation.getCompany(), invitation.getInvitedUser(), invitation.getRole());
        }

        logger.info("Invitation with id: {} has been answered and saved.", invitationId);

        SendEmailDto invitedUsersEmailNotification = new SendEmailDto();
        invitedUsersEmailNotification.setRecipient(invitation.getInvitedUser());
        invitedUsersEmailNotification.setCategory(NotificationCategory.ACCOUNT_SECURITY);
        invitedUsersEmailNotification.setDestinationEmail(invitation.getInvitedUser().getEmail());
        invitedUsersEmailNotification.setTemplateName(accepted ? "company-invitation-accepted-template" : "company-invitation-rejected-template");
        invitedUsersEmailNotification.setTemplateParameters(Map.of(
                "firstName", invitation.getInvitedUser().getFirstName(),
                "lastName", invitation.getInvitedUser().getLastName(),
                "companyName", invitation.getCompany().getName(),
                "role", invitation.getRole().name()
        ));

        notificationService.sendEmail(invitedUsersEmailNotification, DispatchPriority.NORMAL, true);

        SendEmailDto invitedByEmailNotification = new SendEmailDto();
        invitedByEmailNotification.setRecipient(invitation.getInvitedBy());
        invitedByEmailNotification.setCategory(NotificationCategory.ACCOUNT_SECURITY);
        invitedByEmailNotification.setDestinationEmail(invitation.getInvitedBy().getEmail());
        invitedByEmailNotification.setTemplateName(accepted ? "company-invitation-accepted-by-inviter-template" : "company-invitation-rejected-by-inviter-template");
        invitedByEmailNotification.setTemplateParameters(Map.of(
                "firstName", invitation.getInvitedBy().getFirstName(),
                "lastName", invitation.getInvitedBy().getLastName(),
                "companyName", invitation.getCompany().getName(),
                "role", invitation.getRole().name(),
                "invitedUserFirstName", invitation.getInvitedUser().getFirstName(),
                "invitedUserLastName", invitation.getInvitedUser().getLastName()
        ));

        notificationService.sendEmail(invitedByEmailNotification, DispatchPriority.NORMAL, true);
        logger.info("Notifications about the answered invitation have been sent.");
        return companyContactInvitationMapper.toGetCompanyContactInvitationDto(invitation);
    }

    @Override
    public GetCompanyContactInvitationDto getInvitationById(UUID invitationId) {
        logger.info("Getting invitation with id: {}", invitationId);
        var entity = companyContactInvitationDao.findById(invitationId)
                .orElseThrow(() -> new CompanyContactInvitationNotFoundException(CompanyContactInvitationMessages.COMPANY_CONTACT_INVITATION_NOT_FOUND));

        return companyContactInvitationMapper.toGetCompanyContactInvitationDto(entity);
    }

    @Override
    public List<CompanyContactInvitationDto> getCompanyContactInvitationsByCompanyIdDto(UUID companyId) {
        logger.info("Getting company contact invitations for company with id: {}", companyId);
        var currentUser = securityService.getAuthenticatedUserFromContext();

        var isAdmin = companyContactService.isUserAdminOfCompany(companyId, currentUser.getId());

        if (!isAdmin){
            logger.warn("User with id: {} is not a contact of company with id: {}, terminating request!", currentUser.getId(), companyId);
            throw new UserNotAssociatedWithCompanyException(CompanyMessages.USER_NOT_ASSOCIATED_WITH_COMPANY);
        }

        var invitations = getInvitationsByCompanyId(companyId);

        logger.info("Company contact invitations for company with id: {} retrieved successfully.", companyId);

        return companyContactInvitationMapper.toCompanyContactInvitationDtoList(invitations);
    }


}
