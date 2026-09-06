package com.exskylab.koala.core.schedulers;

import com.exskylab.koala.core.privacy.PersonalMediaDeletionOutbox;
import com.exskylab.koala.core.properties.AccountDeletionProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PersonalMediaDeletionScheduler {

    private final PersonalMediaDeletionOutbox outbox;
    private final AccountDeletionProperties properties;

    public PersonalMediaDeletionScheduler(PersonalMediaDeletionOutbox outbox,
                                          AccountDeletionProperties properties) {
        this.outbox = outbox;
        this.properties = properties;
    }

    @Scheduled(cron = "${account-deletion.media-deletion-cron:0 */15 * * * *}")
    public void retryPendingDeletes() {
        if (!properties.isEnabled()) {
            return;
        }
        int deleted = outbox.processPending(properties.getBatchSize());
        log.info("Personal media deletion retry finished: {} objects deleted", deleted);
    }
}
