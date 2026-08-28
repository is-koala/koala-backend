package com.exskylab.koala.core.schedulers;

import lombok.extern.slf4j.Slf4j;
import com.exskylab.koala.business.abstracts.SettlementService;
import com.exskylab.koala.core.properties.SettlementRetryProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class SettlementRetryScheduler {

    private final SettlementService settlementService;
    private final SettlementRetryProperties properties;

    public SettlementRetryScheduler(SettlementService settlementService,
                                    SettlementRetryProperties properties) {
        this.settlementService = settlementService;
        this.properties = properties;
    }

    @Scheduled(cron = "${iyzico.settlement-retry.cron:0 */5 * * * *}")
    public void runDueSettlements() {
        if (!properties.isEnabled()) {
            return;
        }

        List<UUID> due = settlementService.findDueTaskIds();
        if (due.isEmpty()) {
            return;
        }

        int succeeded = 0;
        for (UUID taskId : due) {
            try {
                if (settlementService.runTask(taskId)) {
                    succeeded++;
                }
            } catch (Exception e) {
                log.error("Settlement task {} could not be run: {}", taskId, e.getMessage());
            }
        }

        log.info("Settlement retry run finished: {} of {} tasks succeeded.", succeeded, due.size());
    }
}
