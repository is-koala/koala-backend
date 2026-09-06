package com.exskylab.koala.core.schedulers;

import com.exskylab.koala.business.abstracts.IysService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IysConsentRetryScheduler {

    private final IysService iysService;

    public IysConsentRetryScheduler(IysService iysService) {
        this.iysService = iysService;
    }

    @Scheduled(fixedDelayString = "${iys.retry.fixed-delay:PT30S}",
            initialDelayString = "${iys.retry.fixed-delay:PT30S}")
    public void processDueTasks() {
        for (var taskId : iysService.findDueTaskIds()) {
            iysService.runTask(taskId);
        }
    }
}
