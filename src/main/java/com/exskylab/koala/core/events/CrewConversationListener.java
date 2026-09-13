package com.exskylab.koala.core.events;

import com.exskylab.koala.business.abstracts.MessagingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class CrewConversationListener {

    private final MessagingService messagingService;

    public CrewConversationListener(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAssignmentPaid(AssignmentPaidEvent event) {
        try {
            messagingService.joinJobCrew(event.jobId(), event.workerId());
        } catch (RuntimeException exception) {
            log.warn("Worker {} could not be added to the crew of job {} after assignment {} was paid",
                    event.workerId(), event.jobId(), event.assignmentId(), exception);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onAssignmentClosed(AssignmentClosedEvent event) {
        try {
            messagingService.leaveJobCrew(event.jobId(), event.workerId());
        } catch (RuntimeException exception) {
            log.warn("Worker {} could not be removed from the crew of job {} after assignment {} closed",
                    event.workerId(), event.jobId(), event.assignmentId(), exception);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCompanyContactAdded(CompanyContactAddedEvent event) {
        try {
            messagingService.joinCompanyCrews(event.companyId(), event.userId());
        } catch (RuntimeException exception) {
            log.warn("Contact {} could not be added to the crews of company {}",
                    event.userId(), event.companyId(), exception);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onCompanyContactRemoved(CompanyContactRemovedEvent event) {
        try {
            messagingService.leaveCompanyCrews(event.companyId(), event.userId());
        } catch (RuntimeException exception) {
            log.warn("Contact {} could not be removed from the crews of company {}",
                    event.userId(), event.companyId(), exception);
        }
    }
}
