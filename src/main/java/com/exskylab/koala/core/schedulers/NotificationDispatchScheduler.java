package com.exskylab.koala.core.schedulers;

import com.exskylab.koala.business.abstracts.NotificationDispatchService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;

@Component
public class NotificationDispatchScheduler {

    private final NotificationDispatchService dispatchService;
    private final Executor dispatchExecutor;

    public NotificationDispatchScheduler(NotificationDispatchService dispatchService,
                                         @Qualifier("notificationDispatchExecutor") Executor dispatchExecutor) {
        this.dispatchService = dispatchService;
        this.dispatchExecutor = dispatchExecutor;
    }

    @Scheduled(fixedDelayString = "${notification.dispatch.fixed-delay:PT5S}",
            initialDelayString = "${notification.dispatch.fixed-delay:PT5S}",
            scheduler = "notificationDispatchTriggerScheduler")
    public void processDueTasks() {
        for (var notificationId : dispatchService.findDueTaskIds()) {
            dispatchExecutor.execute(() -> dispatchService.runTask(notificationId));
        }
    }
}
