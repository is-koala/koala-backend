package com.exskylab.koala.core.schedulers;

import com.exskylab.koala.business.abstracts.IysService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class IysConsentRetryScheduler {

    private final IysService iysService;

    public IysConsentRetryScheduler(IysService iysService) {
        this.iysService = iysService;
    }

    @Scheduled(fixedDelayString = "${iys.retry.fixed-delay:PT30S}",
            initialDelayString = "${iys.retry.fixed-delay:PT30S}")
    public void processDueTasks() {
        for (var taskId : iysService.findDueTaskIds()) {
            try {
                iysService.runTask(taskId);
            } catch (RuntimeException exception) {
                log.warn("IYS consent task {} could not be processed; retry state is preserved", taskId);
            }
        }
    }
}
