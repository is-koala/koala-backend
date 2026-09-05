package com.exskylab.koala.core.events;

import com.exskylab.koala.business.abstracts.PasswordResetNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class PasswordResetCompletedListener {

    private final PasswordResetNotificationService notificationService;

    public PasswordResetCompletedListener(PasswordResetNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPasswordResetCompleted(PasswordResetCompletedEvent event) {
        try {
            notificationService.sendCompletionEmail(event.userId());
        } catch (RuntimeException exception) {
            log.error("Password reset completion email could not be queued for user id: {}",
                    event.userId(), exception);
        }
    }
}
