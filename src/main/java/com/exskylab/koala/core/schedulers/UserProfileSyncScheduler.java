package com.exskylab.koala.core.schedulers;

import com.exskylab.koala.business.abstracts.UserProfileSyncService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UserProfileSyncScheduler {

    private final UserProfileSyncService syncService;

    public UserProfileSyncScheduler(UserProfileSyncService syncService) {
        this.syncService = syncService;
    }

    @Scheduled(fixedDelayString = "${user-profile-sync.dispatch.fixed-delay:PT5S}",
            initialDelayString = "${user-profile-sync.dispatch.fixed-delay:PT5S}")
    public void processDueTasks() {
        for (UUID intentId : syncService.findDueTaskIds()) {
            syncService.runTask(intentId);
        }
    }
}
