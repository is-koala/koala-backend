package com.exskylab.koala.business.concretes;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.business.abstracts.*;
import com.exskylab.koala.core.constants.ActivityMessages;
import com.exskylab.koala.core.constants.CompanyContactInvitationMessages;
import com.exskylab.koala.core.constants.CompanyMessages;
import com.exskylab.koala.core.dtos.companyContactInvitation.response.CompanyContactInvitationDto;
import com.exskylab.koala.core.dtos.companyContactInvitation.response.GetCompanyContactInvitationDto;
import com.exskylab.koala.core.dtos.notification.request.SendEmailDto;
import com.exskylab.koala.core.dtos.notification.request.SendInAppDto;
import com.exskylab.koala.core.helpers.MessageResolver;
import com.exskylab.koala.core.exceptions.*;
import com.exskylab.koala.core.mappers.CompanyContactInvitationMapper;
import com.exskylab.koala.dataAccess.CompanyContactInvitationDao;
import com.exskylab.koala.entities.*;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class CompanyContactInvitationManager implements CompanyContactInvitationService {


    private final CompanyContactInvitationDao companyContactInvitationDao;
    private final NotificationService notificationService;
    private final SecurityService securityService;
    private final CompanyContactService companyContactService;
    private final CompanyContactInvitationMapper companyContactInvitationMapper;
    private final MessageResolver messageResolver;

    public CompanyContactInvitationManager(CompanyContactInvitationDao companyContactInvitationDao,
                                           NotificationService notificationService, SecurityService securityService,
                                           CompanyContactService companyContactService,
                                           CompanyContactInvitationMapper companyContactInvitationMapper,
                                           MessageResolver messageResolver) {
        this.companyContactInvitationDao = companyContactInvitationDao;
        this.notificationService = notificationService;
        this.securityService = securityService;
        this.companyContactService = companyContactService;
        this.companyContactInvitationMapper = companyContactInvitationMapper;
        this.messageResolver = messageResolver;
    }


    @Override
    public CompanyContactInvitation inviteUserToCompany(User invitedBy, User userToInvite, Company company, CompanyContactRole role) {
        log.info("Inviting user with id: {} to company with id: {}", userToInvite.getId(), company.getId());
        CompanyContactInvitation invitation = new CompanyContactInvitation();
        invitation.setCompany(company);
        invitation.setInvitedBy(invitedBy);
        invitation.setInvitedUser(userToInvite);
        invitation.setRole(role);
        invitation.setStatus(CompanyContactInvitationStatus.PENDING);
        invitation.setInvitedAt(Instant.now());
        invitation.setAnsweredAt(null);
        CompanyContactInvitation savedInvitation = companyContactInvitationDao.save(invitation);

        log.info("Saved invitation with id: {}", savedInvitation.getId());

        SendEmailDto sendEmailDto = new SendEmailDto();
        sendEmailDto.setRecipient(userToInvite);
        sendEmailDto.setCategory(NotificationCategory.COMPANY_UPDATES);
        sendEmailDto.setDestinationEmail(userToInvite.getEmail());

        sendEmailDto.setSubject(messageResolver.resolve(CompanyContactInvitationMessages.INVITATION_EMAIL_SUBJECT));
        sendEmailDto.setTemplateName("company-invitation-template");
        sendEmailDto.setTemplateParameters(Map.of(
                "firstName", userToInvite.getFirstName(),
                "lastName", userToInvite.getLastName(),
                "companyName", company.getName(),
                "invitedByFirstName", invitedBy.getFirstName(),
                "invitedByLastName", invitedBy.getLastName(),
                "role", role.name()
        ));
        log.info("Sending notification to user with id: {} about the invitation.", userToInvite.getId());

        notificationService.sendEmail(sendEmailDto, DispatchPriority.HIGH, true);

        recordInvitationInFeed(savedInvitation);

        return savedInvitation;
    }

    private void recordInvitationInFeed(CompanyContactInvitation invitation) {
        try {
            SendInAppDto sendInAppDto = new SendInAppDto();
            sendInAppDto.setRecipient(invitation.getInvitedUser());
            sendInAppDto.setCategory(NotificationCategory.COMPANY_UPDATES);
            sendInAppDto.setSubject(invitation.getCompany().getName());
            sendInAppDto.setBody(messageResolver.resolve(ActivityMessages.COMPANY_INVITATION_RECEIVED));
            sendInAppDto.setTargetType(NotificationTargetType.COMPANY_CONTACT_INVITATION);
            sendInAppDto.setTargetId(invitation.getId());

            notificationService.sendInApp(sendInAppDto);
        } catch (Exception exception) {
            log.warn("Invitation with id: {} could not be recorded in the feed of user with id: {}: {}",
                    invitation.getId(), invitation.getInvitedUser().getId(), exception.getMessage());
        }
    }

    @Override
    public List<CompanyContactInvitation> getInvitationsByCompanyId(UUID companyId) {
        log.debug("Getting invitations for company with id: {}", companyId);
        return companyContactInvitationDao.findByCompanyId(companyId);
    }

    @Override
    public boolean hasPendingInvitation(UUID companyId, UUID invitedUserId) {
        log.debug("Checking pending invitation for user with id: {} in company with id: {}", invitedUserId, companyId);
        return companyContactInvitationDao.existsByCompanyIdAndInvitedUserIdAndStatus(companyId, invitedUserId,
                CompanyContactInvitationStatus.PENDING);
    }

    @Override
    public GetCompanyContactInvitationDto answerToInvitation(UUID invitationId, boolean accepted) {
        log.info("Answering invitation with id: {} to accepted: {}", invitationId, accepted);
        CompanyContactInvitation invitation = companyContactInvitationDao.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException(CompanyContactInvitationMessages.COMPANY_CONTACT_INVITATION_NOT_FOUND));

        var currentUser = securityService.getAuthenticatedUser();

        if (!invitation.wasSentTo(currentUser)){
            log.warn("User with id: {} was not invited by invitation with id: {}, terminating request!",
                    currentUser.getId(), invitationId);
            throw new ResourceNotFoundException(CompanyContactInvitationMessages.COMPANY_CONTACT_INVITATION_NOT_FOUND);
        }

        if (invitation.getStatus() != CompanyContactInvitationStatus.PENDING) {
            log.warn("Invitation with id: {} has already been answered.", invitationId);
            throw new ConflictException(CompanyContactInvitationMessages.INVITATION_ALREADY_ANSWERED);
        }

        invitation.setStatus(accepted ? CompanyContactInvitationStatus.ACCEPTED : CompanyContactInvitationStatus.REJECTED);

        invitation.setAnsweredAt(Instant.now());

        invitation = companyContactInvitationDao.save(invitation);

        if (accepted){
            log.info("Invitation with id: {} accepted. Adding user to company contacts.", invitationId);
            companyContactService.addCompanyContact(invitation.getCompany(), invitation.getInvitedUser(), invitation.getRole());
        }

        log.info("Invitation with id: {} has been answered and saved.", invitationId);

        SendEmailDto invitedUsersEmailNotification = new SendEmailDto();
        invitedUsersEmailNotification.setRecipient(invitation.getInvitedUser());
        invitedUsersEmailNotification.setCategory(NotificationCategory.COMPANY_UPDATES);
        invitedUsersEmailNotification.setDestinationEmail(invitation.getInvitedUser().getEmail());
        invitedUsersEmailNotification.setSubject(messageResolver.resolve(accepted
                ? CompanyContactInvitationMessages.INVITATION_ACCEPTED_EMAIL_SUBJECT
                : CompanyContactInvitationMessages.INVITATION_REJECTED_EMAIL_SUBJECT));
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
        invitedByEmailNotification.setCategory(NotificationCategory.COMPANY_UPDATES);
        invitedByEmailNotification.setDestinationEmail(invitation.getInvitedBy().getEmail());
        invitedByEmailNotification.setSubject(messageResolver.resolve(accepted
                ? CompanyContactInvitationMessages.INVITATION_ACCEPTED_INVITER_EMAIL_SUBJECT
                : CompanyContactInvitationMessages.INVITATION_REJECTED_INVITER_EMAIL_SUBJECT));
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
        log.info("Notifications about the answered invitation have been sent.");
        return companyContactInvitationMapper.toGetCompanyContactInvitationDto(invitation);
    }

    @Override
    public GetCompanyContactInvitationDto getInvitationById(UUID invitationId) {
        log.debug("Getting invitation with id: {}", invitationId);
        var entity = companyContactInvitationDao.findById(invitationId)
                .orElseThrow(() -> new ResourceNotFoundException(CompanyContactInvitationMessages.COMPANY_CONTACT_INVITATION_NOT_FOUND));

        var currentUser = securityService.getAuthenticatedUser();

        if (!isAssociatedWithInvitation(entity, currentUser)) {
            log.warn("User with id: {} is not associated with invitation with id: {}, terminating request!",
                    currentUser.getId(), invitationId);
            throw new ResourceNotFoundException(CompanyContactInvitationMessages.COMPANY_CONTACT_INVITATION_NOT_FOUND);
        }

        return companyContactInvitationMapper.toGetCompanyContactInvitationDto(entity);
    }

    private boolean isAssociatedWithInvitation(CompanyContactInvitation invitation, User currentUser) {
        return invitation.isPartyTo(currentUser)
                || companyContactService.isUserAdminOfCompany(invitation.companyId(), currentUser.getId());
    }

    @Override
    public List<CompanyContactInvitationDto> getCompanyContactInvitationsByCompanyIdDto(UUID companyId) {
        log.debug("Getting company contact invitations for company with id: {}", companyId);
        var currentUser = securityService.getAuthenticatedUser();

        var isAdmin = companyContactService.isUserAdminOfCompany(companyId, currentUser.getId());

        if (!isAdmin){
            log.warn("User with id: {} is not an admin of company with id: {}, terminating request!", currentUser.getId(), companyId);
            throw new ResourceNotFoundException(CompanyMessages.USER_NOT_ASSOCIATED_WITH_COMPANY);
        }

        var invitations = getInvitationsByCompanyId(companyId);

        log.info("Company contact invitations for company with id: {} retrieved successfully.", companyId);

        return companyContactInvitationMapper.toCompanyContactInvitationDtoList(invitations);
    }


}
