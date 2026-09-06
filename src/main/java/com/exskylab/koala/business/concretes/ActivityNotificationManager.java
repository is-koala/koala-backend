package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.ActivityNotificationService;
import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.core.constants.ActivityMessages;
import com.exskylab.koala.core.dtos.notification.request.SendEmailDto;
import com.exskylab.koala.core.dtos.notification.request.SendInAppDto;
import com.exskylab.koala.core.dtos.notification.request.SendPushToUserDto;
import com.exskylab.koala.core.properties.AppProperties;
import com.exskylab.koala.core.helpers.MessageResolver;
import com.exskylab.koala.entities.ApplicationStatus;
import com.exskylab.koala.entities.AssignmentStatus;
import com.exskylab.koala.entities.Cancellation;
import com.exskylab.koala.entities.Company;
import com.exskylab.koala.entities.CompanyApprovalStatus;
import com.exskylab.koala.entities.CompanyContact;
import com.exskylab.koala.entities.CompanyContactRole;
import com.exskylab.koala.entities.DispatchPriority;
import com.exskylab.koala.entities.Dispute;
import com.exskylab.koala.entities.JobApplication;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.NotificationTargetType;
import com.exskylab.koala.entities.Review;
import com.exskylab.koala.entities.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.UUID;
import java.util.Map;

@Service
@Slf4j
public class ActivityNotificationManager implements ActivityNotificationService {

    private final NotificationService notificationService;
    private final MessageResolver messageResolver;
    private final AppProperties appProperties;

    public ActivityNotificationManager(NotificationService notificationService, MessageResolver messageResolver,
                                       AppProperties appProperties) {
        this.notificationService = notificationService;
        this.messageResolver = messageResolver;
        this.appProperties = appProperties;
    }

    @Override
    public void applicationAnswered(JobApplication application) {
        String key = application.getStatus() == ApplicationStatus.ACCEPTED
                ? ActivityMessages.APPLICATION_ACCEPTED
                : ActivityMessages.APPLICATION_REJECTED;

        notify(application.getUser(), application.getJob().getTitle(), key,
                NotificationCategory.APPLICATION_UPDATES,
                NotificationTargetType.JOB_APPLICATION, application.getId());
    }

    @Override
    public void applicationWithdrawn(JobApplication application) {
        notify(application.getJob().getCreator(), application.getJob().getTitle(),
                ActivityMessages.APPLICATION_WITHDRAWN, NotificationCategory.APPLICATION_UPDATES,
                NotificationTargetType.JOB_APPLICATION, application.getId());
    }

    @Override
    public void assignmentOffered(JobAssignment assignment) {
        notify(assignment.getWorker(), assignment.getJob().getTitle(),
                ActivityMessages.ASSIGNMENT_OFFERED, NotificationCategory.APPLICATION_UPDATES,
                NotificationTargetType.JOB_ASSIGNMENT, assignment.getId());
    }

    @Override
    public void assignmentAnswered(JobAssignment assignment) {
        String key = assignment.getStatus() == AssignmentStatus.CONFIRMED
                ? ActivityMessages.ASSIGNMENT_ACCEPTED
                : ActivityMessages.ASSIGNMENT_REJECTED;

        notify(employerOf(assignment), assignment.getJob().getTitle(), key,
                NotificationCategory.APPLICATION_UPDATES,
                NotificationTargetType.JOB_ASSIGNMENT, assignment.getId());
    }

    @Override
    public void assignmentCompleted(JobAssignment assignment) {
        notify(assignment.getWorker(), assignment.getJob().getTitle(),
                ActivityMessages.WORK_COMPLETED, NotificationCategory.APPLICATION_UPDATES,
                NotificationTargetType.JOB_ASSIGNMENT, assignment.getId());
    }

    @Override
    public void payoutReleased(JobAssignment assignment) {
        notify(assignment.getWorker(), assignment.getJob().getTitle(),
                ActivityMessages.PAYOUT_RELEASED, NotificationCategory.APPLICATION_UPDATES,
                NotificationTargetType.JOB_ASSIGNMENT, assignment.getId());
    }

    @Override
    public void disputeOpened(Dispute dispute) {
        JobAssignment assignment = dispute.getJobAssignment();
        User counterpart = dispute.getOpenedBy() != null
                && dispute.getOpenedBy().getId().equals(assignment.getWorker().getId())
                ? employerOf(assignment)
                : assignment.getWorker();

        notify(counterpart, assignment.getJob().getTitle(),
                ActivityMessages.DISPUTE_OPENED, NotificationCategory.APPLICATION_UPDATES,
                NotificationTargetType.JOB_ASSIGNMENT, assignment.getId());
    }

    @Override
    public void disputeResolved(Dispute dispute) {
        JobAssignment assignment = dispute.getJobAssignment();

        notify(assignment.getWorker(), assignment.getJob().getTitle(),
                ActivityMessages.DISPUTE_RESOLVED, NotificationCategory.APPLICATION_UPDATES,
                NotificationTargetType.JOB_ASSIGNMENT, assignment.getId());
        notify(employerOf(assignment), assignment.getJob().getTitle(),
                ActivityMessages.DISPUTE_RESOLVED, NotificationCategory.APPLICATION_UPDATES,
                NotificationTargetType.JOB_ASSIGNMENT, assignment.getId());
    }

    @Override
    public void assignmentCancelled(Cancellation cancellation) {
        JobAssignment assignment = cancellation.getJobAssignment();
        User cancelledBy = cancellation.getCancelledBy();

        User counterpart = cancelledBy != null
                && cancelledBy.getId().equals(assignment.getWorker().getId())
                ? employerOf(assignment)
                : assignment.getWorker();

        notify(counterpart, assignment.getJob().getTitle(),
                ActivityMessages.ASSIGNMENT_CANCELLED, NotificationCategory.APPLICATION_UPDATES,
                NotificationTargetType.JOB_ASSIGNMENT, assignment.getId());
    }

    @Override
    public void assignmentExpired(Cancellation cancellation) {
        JobAssignment assignment = cancellation.getJobAssignment();
        String jobTitle = assignment.getJob().getTitle();

        notify(assignment.getWorker(), jobTitle,
                ActivityMessages.ASSIGNMENT_EXPIRED_WORKER, NotificationCategory.APPLICATION_UPDATES,
                NotificationTargetType.JOB_ASSIGNMENT, assignment.getId());
        notify(employerOf(assignment), jobTitle,
                ActivityMessages.ASSIGNMENT_EXPIRED_EMPLOYER, NotificationCategory.APPLICATION_UPDATES,
                NotificationTargetType.JOB_ASSIGNMENT, assignment.getId());
    }

    @Override
    public void reviewReceived(Review review) {
        notify(review.getSubject(), review.getJobAssignment().getJob().getTitle(),
                ActivityMessages.REVIEW_RECEIVED, NotificationCategory.APPLICATION_UPDATES,
                NotificationTargetType.JOB_ASSIGNMENT, review.getJobAssignment().getId());
    }

    @Override
    public void identityVerified(User user) {
        notify(user, null, ActivityMessages.IDENTITY_VERIFIED, NotificationCategory.IDENTITY_VERIFICATION,
                NotificationTargetType.USER, user.getId());
    }

    @Override
    public void companyApprovalDecided(Company company) {
        String key = company.getApprovalStatus() == CompanyApprovalStatus.APPROVED
                ? ActivityMessages.COMPANY_APPROVED
                : ActivityMessages.COMPANY_REJECTED;

        company.getContacts().stream()
                .filter(contact -> contact.getRole() == CompanyContactRole.ADMIN)
                .map(CompanyContact::getUser)
                .forEach(admin -> notify(admin, company.getName(), key,
                        NotificationCategory.COMPANY_UPDATES,
                        NotificationTargetType.COMPANY, company.getId()));
    }

    private User employerOf(JobAssignment assignment) {
        return assignment.getJob().getCreator();
    }

    private void notify(User recipient, String jobTitle, String bodyKey, NotificationCategory category) {
        notify(recipient, jobTitle, bodyKey, category, null, null);
    }

    private void notify(User recipient, String jobTitle, String bodyKey, NotificationCategory category,
                        NotificationTargetType targetType, UUID targetId) {
        if (recipient == null) {
            return;
        }

        String message = messageResolver.resolve(bodyKey);

        inApp(recipient, jobTitle, message, category, bodyKey, targetType, targetId);
        push(recipient, jobTitle, message, category, bodyKey);
        email(recipient, jobTitle, message, category, bodyKey);
    }

    private void inApp(User recipient, String jobTitle, String message, NotificationCategory category,
                       String bodyKey, NotificationTargetType targetType, UUID targetId) {
        try {
            SendInAppDto dto = new SendInAppDto();
            dto.setRecipient(recipient);
            dto.setCategory(category);
            dto.setSubject(jobTitle);
            dto.setBody(message);
            dto.setTargetType(targetType);
            dto.setTargetId(targetId);

            notificationService.sendInApp(dto);
        } catch (Exception exception) {
            log.warn("Activity feed entry {} could not be recorded for {}: {}",
                    bodyKey, recipient.getId(), exception.getMessage());
        }
    }

    private void push(User recipient, String jobTitle, String message,
                      NotificationCategory category, String bodyKey) {
        try {
            SendPushToUserDto dto = new SendPushToUserDto();
            dto.setRecipient(recipient);
            dto.setTitle(jobTitle);
            dto.setBody(message);
            dto.setCategory(category);

            notificationService.sendPushToUser(dto, DispatchPriority.NORMAL, true);
        } catch (Exception exception) {
            log.warn("Activity push {} could not be delivered to {}: {}",
                    bodyKey, recipient.getId(), exception.getMessage());
        }
    }

    private void email(User recipient, String jobTitle, String message,
                       NotificationCategory category, String bodyKey) {
        if (recipient.getEmail() == null || recipient.getEmail().isBlank()) {
            return;
        }

        try {
            SendEmailDto dto = new SendEmailDto();
            dto.setRecipient(recipient);
            dto.setDestinationEmail(recipient.getEmail());
            dto.setCategory(category);
            dto.setSubject(message);
            dto.setTemplateName("activity-notice-template");

            Map<String, Object> parameters = new HashMap<>();
            parameters.put("firstName", recipient.getFirstName());
            parameters.put("message", message);
            parameters.put("jobTitle", jobTitle);
            parameters.put("actionUrl", appProperties.webBaseUrl());
            dto.setTemplateParameters(parameters);

            notificationService.sendEmail(dto, DispatchPriority.NORMAL, true);
        } catch (Exception exception) {
            log.warn("Activity email {} could not be delivered to {}: {}",
                    bodyKey, recipient.getId(), exception.getMessage());
        }
    }
}
