package com.exskylab.koala.core.schedulers;

import com.exskylab.koala.business.abstracts.PayoutService;
import com.exskylab.koala.core.monitoring.PaymentMetrics;
import com.exskylab.koala.core.properties.PayoutSettlementProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PayoutSettlementOverdueScheduler {

    private final PayoutService payoutService;
    private final PaymentMetrics paymentMetrics;
    private final PayoutSettlementProperties properties;

    public PayoutSettlementOverdueScheduler(PayoutService payoutService, PaymentMetrics paymentMetrics,
                                            PayoutSettlementProperties properties) {
        this.payoutService = payoutService;
        this.paymentMetrics = paymentMetrics;
        this.properties = properties;
    }

    @Scheduled(cron = "${payout.settlement.overdue-cron:0 20 * * * *}")
    public void publishOverdueSettlements() {
        if (!properties.isOverdueMonitoringEnabled()) {
            return;
        }

        int overdue = payoutService.findPayoutsAwaitingSettlementLongerThan(
                properties.getOverdueAfter()).size();
        int stalled = payoutService.findPayoutsAwaitingSettlementLongerThan(
                properties.getStalledAfter()).size();
        paymentMetrics.payoutsAwaitingSettlement(overdue);
        paymentMetrics.payoutsStalledInSettlement(stalled);

        if (overdue > 0) {
            log.warn("{} payouts have been waiting for bank settlement evidence longer than {}, "
                            + "{} of them longer than {}; "
                            + "run the reconciliation queue in docs/runbooks/payments.md",
                    overdue, properties.getOverdueAfter(), stalled, properties.getStalledAfter());
        }
    }
}
