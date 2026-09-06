package com.exskylab.koala.core.schedulers;

import com.exskylab.koala.business.abstracts.IysReconciliationService;
import com.exskylab.koala.business.abstracts.IysService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class IysReconciliationScheduler {

    private final IysService iysService;
    private final IysReconciliationService reconciliationService;

    public IysReconciliationScheduler(IysService iysService, IysReconciliationService reconciliationService) {
        this.iysService = iysService;
        this.reconciliationService = reconciliationService;
    }

    @Scheduled(fixedDelayString = "${iys.reconciliation.fixed-delay:PT15M}",
            initialDelayString = "${iys.reconciliation.fixed-delay:PT15M}")
    public void reconcileStalePermissions() {
        for (var target : iysService.findStalePermissions()) {
            try {
                reconciliationService.reconcile(target.userId(), target.channel());
            } catch (Exception exception) {
                log.warn("IYS reconciliation failed for user {} on {}; retry was scheduled",
                        target.userId(), target.channel());
            }
        }
    }
}
