package com.exskylab.koala.core.events;

import com.exskylab.koala.business.abstracts.MessagingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class CrewConversationListener {

    private final MessagingService messagingService;

    public CrewConversationListener(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    @EventListener
    public void onAssignmentConfirmed(AssignmentConfirmedEvent event) {
        try {
            messagingService.joinJobCrew(event.jobId(), event.workerId());
        } catch (Exception exception) {
            log.warn("Worker {} could not be added to the crew of job {}: {}",
                    event.workerId(), event.jobId(), exception.getMessage());
        }
    }
}
