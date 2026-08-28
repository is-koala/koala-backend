package com.exskylab.koala.business.concretes;

import com.exskylab.koala.business.abstracts.ActivityNotificationService;
import com.exskylab.koala.business.abstracts.NotificationService;
import com.exskylab.koala.core.constants.ActivityMessages;
import com.exskylab.koala.core.dtos.notification.request.SendPushToUserDto;
import com.exskylab.koala.core.helpers.MessageResolver;
import com.exskylab.koala.entities.ApplicationStatus;
import com.exskylab.koala.entities.AssignmentStatus;
import com.exskylab.koala.entities.Cancellation;
import com.exskylab.koala.entities.DispatchPriority;
import com.exskylab.koala.entities.Dispute;
import com.exskylab.koala.entities.JobApplication;
import com.exskylab.koala.entities.JobAssignment;
import com.exskylab.koala.entities.NotificationCategory;
import com.exskylab.koala.entities.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ActivityNotificationManager implements ActivityNotificationService {

    private final NotificationService notificationService;
    private final MessageResolver messageResolver;

    public ActivityNotificationManager(NotificationService notificationService, MessageResolver messageResolver) {
        this.notificationService = notificationService;
        this.messageResolver = messageResolver;
    }

    @Override
    public void applicationAnswered(JobApplication application) {
        String key = application.getStatus() == ApplicationStatus.ACCEPTED
                ? ActivityMessages.APPLICATION_ACCEPTED
                : ActivityMessages.APPLICATION_REJECTED;

        notify(application.getUser(), application.getJob().getTitle(), key,
                NotificationCategory.APPLICATION_UPDATES);
    }

    @Override
    public void assignmentOffered(JobAssignment assignment) {
        notify(assignment.getWorker(), assignment.getJob().getTitle(),
                ActivityMessages.ASSIGNMENT_OFFERED, NotificationCategory.APPLICATION_UPDATES);
    }

    @Override
    public void assignmentAnswered(JobAssignment assignment) {
        String key = assignment.getStatus() == AssignmentStatus.CONFIRMED
                ? ActivityMessages.ASSIGNMENT_ACCEPTED
                : ActivityMessages.ASSIGNMENT_REJECTED;

        notify(employerOf(assignment), assignment.getJob().getTitle(), key,
                NotificationCategory.APPLICATION_UPDATES);
    }

    @Override
    public void assignmentCompleted(JobAssignment assignment) {
        notify(assignment.getWorker(), assignment.getJob().getTitle(),
                ActivityMessages.WORK_COMPLETED, NotificationCategory.APPLICATION_UPDATES);
    }

    @Override
    public void payoutReleased(JobAssignment assignment) {
        notify(assignment.getWorker(), assignment.getJob().getTitle(),
                ActivityMessages.PAYOUT_RELEASED, NotificationCategory.APPLICATION_UPDATES);
    }

    @Override
    public void disputeOpened(Dispute dispute) {
        JobAssignment assignment = dispute.getJobAssignment();
        User counterpart = dispute.getOpenedBy() != null
                && dispute.getOpenedBy().getId().equals(assignment.getWorker().getId())
                ? employerOf(assignment)
                : assignment.getWorker();

        notify(counterpart, assignment.getJob().getTitle(),
                ActivityMessages.DISPUTE_OPENED, NotificationCategory.APPLICATION_UPDATES);
    }

    @Override
    public void disputeResolved(Dispute dispute) {
        JobAssignment assignment = dispute.getJobAssignment();

        notify(assignment.getWorker(), assignment.getJob().getTitle(),
                ActivityMessages.DISPUTE_RESOLVED, NotificationCategory.APPLICATION_UPDATES);
        notify(employerOf(assignment), assignment.getJob().getTitle(),
                ActivityMessages.DISPUTE_RESOLVED, NotificationCategory.APPLICATION_UPDATES);
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
                ActivityMessages.ASSIGNMENT_CANCELLED, NotificationCategory.APPLICATION_UPDATES);
    }

    @Override
    public void identityVerified(User user) {
        notify(user, messageResolver.resolve(ActivityMessages.IDENTITY_VERIFIED_TITLE),
                ActivityMessages.IDENTITY_VERIFIED, NotificationCategory.IDENTITY_VERIFICATION);
    }

    private User employerOf(JobAssignment assignment) {
        return assignment.getJob().getCreator();
    }

    private void notify(User recipient, String title, String bodyKey, NotificationCategory category) {
        if (recipient == null) {
            return;
        }

        try {
            SendPushToUserDto push = new SendPushToUserDto();
            push.setRecipient(recipient);
            push.setTitle(title);
            push.setBody(messageResolver.resolve(bodyKey));
            push.setCategory(category);

            notificationService.sendPushToUser(push, DispatchPriority.NORMAL, true);
        } catch (Exception exception) {
            log.warn("Activity notification {} could not be delivered to {}: {}",
                    bodyKey, recipient.getId(), exception.getMessage());
        }
    }
}
