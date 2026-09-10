package com.exskylab.koala.core.schedulers;

import com.exskylab.koala.business.abstracts.PushDeliveryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PushDeliveryScheduler {

    private final PushDeliveryService pushDeliveryService;

    public PushDeliveryScheduler(PushDeliveryService pushDeliveryService) {
        this.pushDeliveryService = pushDeliveryService;
    }

    @Scheduled(fixedDelayString = "${push.delivery.fixed-delay:30s}",
            initialDelayString = "${push.delivery.fixed-delay:30s}")
    public void processDuePushDeliveries() {
        int recovered = pushDeliveryService.recoverStaleClaims();
        int attempted = pushDeliveryService.deliverDueBatch();
        if (recovered > 0 || attempted > 0) {
            log.info("Push delivery sweep recovered {} stale claims and attempted {} due deliveries",
                    recovered, attempted);
        }
    }
}
